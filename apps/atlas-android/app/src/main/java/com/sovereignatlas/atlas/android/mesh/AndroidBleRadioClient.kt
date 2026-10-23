// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.android.mesh

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.util.Log
import com.sovereignatlas.atlas.tactical.mesh.MeshNodeInfo
import com.sovereignatlas.atlas.tactical.mesh.MeshPacket
import com.sovereignatlas.atlas.tactical.mesh.MeshRadioClient
import com.sovereignatlas.atlas.tactical.mesh.PortNum
import com.sovereignatlas.atlas.tactical.mesh.RadioConnectionState
import com.sovereignatlas.atlas.tactical.mesh.RadioHardwareInfo
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class AndroidBleRadioClient(
    private val context: Context,
    private val scope: CoroutineScope,
    private val targetDeviceAddress: String? = null,
) : MeshRadioClient {

    private val bluetoothManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter

    private val _connectionState = MutableStateFlow(RadioConnectionState.DISCONNECTED)
    override val connectionState: StateFlow<RadioConnectionState> = _connectionState.asStateFlow()

    private val _discoveredNodes = MutableStateFlow<List<MeshNodeInfo>>(emptyList())
    override val discoveredNodes: StateFlow<List<MeshNodeInfo>> = _discoveredNodes.asStateFlow()

    private val _inboundPackets = MutableSharedFlow<MeshPacket>(extraBufferCapacity = 128)
    override val inboundPackets: SharedFlow<MeshPacket> = _inboundPackets.asSharedFlow()

    private val _activeRadioInfo = MutableStateFlow<RadioHardwareInfo?>(null)
    override val activeRadioInfo: StateFlow<RadioHardwareInfo?> = _activeRadioInfo.asStateFlow()

    private var bluetoothGatt: BluetoothGatt? = null
    private var toRadioChar: BluetoothGattCharacteristic? = null
    private var fromRadioChar: BluetoothGattCharacteristic? = null

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                _connectionState.value = RadioConnectionState.ERROR
                gatt.close()
                bluetoothGatt = null
                return
            }

            if (newState == BluetoothProfile.STATE_CONNECTED) {
                _connectionState.value = RadioConnectionState.CONNECTING
                gatt.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                _connectionState.value = RadioConnectionState.DISCONNECTED
                gatt.close()
                bluetoothGatt = null
                toRadioChar = null
                fromRadioChar = null
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                _connectionState.value = RadioConnectionState.ERROR
                return
            }

            val meshService = gatt.getService(MESHTASTIC_SERVICE_UUID)
            if (meshService == null) {
                _connectionState.value = RadioConnectionState.ERROR
                return
            }

            val toChar = meshService.getCharacteristic(TO_RADIO_UUID)
            val fromChar = meshService.getCharacteristic(FROM_RADIO_UUID)

            if (toChar == null || fromChar == null) {
                _connectionState.value = RadioConnectionState.ERROR
                return
            }

            toRadioChar = toChar
            fromRadioChar = fromChar

            gatt.setCharacteristicNotification(fromChar, true)
            val descriptor = fromChar.getDescriptor(CCCD_UUID)
            if (descriptor != null) {
                descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                gatt.writeDescriptor(descriptor)
            }

            val radioInfo = RadioHardwareInfo(
                radioModel = gatt.device.name ?: "Meshtastic BLE Radio",
                frequencyBand = "915 MHz US",
                firmwareVersion = "2.3.x",
                nodeNum = (gatt.device.address.hashCode().toLong() and 0xFFFFFFFFL),
                isUsb = false,
            )
            _activeRadioInfo.value = radioInfo
            _connectionState.value = RadioConnectionState.CONNECTED
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
        ) {
            if (characteristic.uuid == FROM_RADIO_UUID) {
                val value = characteristic.value ?: return
                onFromRadioPacket(value)
            }
        }
    }

    @SuppressLint("MissingPermission")
    override suspend fun connect(): Boolean = withContext(Dispatchers.IO) {
        val adapter = bluetoothAdapter
        if (adapter == null || !adapter.isEnabled) {
            _connectionState.value = RadioConnectionState.DISCONNECTED
            return@withContext false
        }

        val address = targetDeviceAddress
        val device: BluetoothDevice? = if (address != null) {
            adapter.getRemoteDevice(address)
        } else {
            adapter.bondedDevices?.firstOrNull { d ->
                d.name?.contains("Meshtastic", ignoreCase = true) == true
            }
        }

        if (device == null) {
            _connectionState.value = RadioConnectionState.DISCONNECTED
            return@withContext false
        }

        _connectionState.value = RadioConnectionState.CONNECTING
        bluetoothGatt = device.connectGatt(context, false, gattCallback)
        true
    }

    @SuppressLint("MissingPermission")
    override suspend fun disconnect(): Unit = withContext(Dispatchers.IO) {
        try {
            bluetoothGatt?.disconnect()
            bluetoothGatt?.close()
        } catch (e: Exception) {
            Log.w("AndroidBleRadioClient", "Error during BLE disconnect", e)
        } finally {
            bluetoothGatt = null
            toRadioChar = null
            fromRadioChar = null
            _activeRadioInfo.value = null
            _connectionState.value = RadioConnectionState.DISCONNECTED
        }
    }

    @SuppressLint("MissingPermission")
    override suspend fun transmitPacket(packet: MeshPacket): Boolean = withContext(Dispatchers.IO) {
        val gatt = bluetoothGatt ?: return@withContext false
        val char = toRadioChar ?: return@withContext false

        char.value = packet.payload
        char.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        return@withContext gatt.writeCharacteristic(char)
    }

    override suspend fun pingNode(targetNodeNum: Long): Boolean {
        val myNode = _activeRadioInfo.value?.nodeNum ?: 0L
        val pingPacket = MeshPacket(
            from = myNode,
            to = targetNodeNum,
            portNum = PortNum.PORTNUM_TELEMETRY_APP,
            payload = "PING".toByteArray(Charsets.UTF_8),
            wantAck = true,
        )
        return transmitPacket(pingPacket)
    }

    private fun onFromRadioPacket(rawBytes: ByteArray) {
        if (rawBytes.isEmpty()) return
        val senderNodeNum = _activeRadioInfo.value?.nodeNum ?: 0x1A2B3C4DL
        val packet = MeshPacket(
            from = senderNodeNum,
            to = 0xFFFFFFFFL,
            portNum = PortNum.PORTNUM_ATAK_PLUGIN,
            payload = rawBytes,
            rxTime = System.currentTimeMillis(),
        )
        _inboundPackets.tryEmit(packet)
    }

    companion object {
        val MESHTASTIC_SERVICE_UUID: UUID =
            UUID.fromString("6ba1b218-15a8-461f-9fa8-5dcae273eafd")
        val TO_RADIO_UUID: UUID =
            UUID.fromString("f75de93c-4530-4ec4-b399-f442377b8359")
        val FROM_RADIO_UUID: UUID =
            UUID.fromString("2c55e69e-4993-11ea-b77f-2e728ce88125")
        val CCCD_UUID: UUID =
            UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }
}
