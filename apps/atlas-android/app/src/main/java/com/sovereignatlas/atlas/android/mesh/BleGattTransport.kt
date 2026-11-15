// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.android.mesh

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelUuid
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID

object MeshtasticBleUuids {
    val SERVICE_UUID: UUID = UUID.fromString("6ba1b218-15a8-461f-9fa8-5dcae273eafd")
    val TO_RADIO_UUID: UUID = UUID.fromString("f75c76d2-129e-4dad-a1dd-7866124401e7")
    val FROM_RADIO_UUID: UUID = UUID.fromString("2c55e69e-4993-11ed-b878-0242ac120002")
    val FROM_NUM_UUID: UUID = UUID.fromString("ed9da18c-a800-4f66-a670-aa7547e34453")
    val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    const val DEFAULT_MTU: Int = 23
    const val PREFERRED_MTU: Int = 512
}

interface BleGattCallbacks {
    fun onCharacteristicNotified(uuid: UUID, value: ByteArray)
    fun onDisconnected(isError: Boolean, reason: String)
}

interface BleGattTransport {
    suspend fun connect(callbacks: BleGattCallbacks): Boolean
    suspend fun requestMtu(mtu: Int): Int
    suspend fun discoverMeshtasticCharacteristics(): Boolean
    suspend fun enableFromNumNotifications(): Boolean
    suspend fun writeToRadio(bytes: ByteArray): Boolean
    suspend fun readFromRadio(): ByteArray?
    fun close()
}

class AndroidBleGattTransport(
    private val context: Context,
) : BleGattTransport {

    private var gatt: BluetoothGatt? = null
    private var toRadioChar: BluetoothGattCharacteristic? = null
    private var fromRadioChar: BluetoothGattCharacteristic? = null
    private var fromNumChar: BluetoothGattCharacteristic? = null
    private var callbacks: BleGattCallbacks? = null

    @Volatile
    private var connectDeferred: CompletableDeferred<Boolean>? = null
    @Volatile
    private var mtuDeferred: CompletableDeferred<Int>? = null
    @Volatile
    private var servicesDeferred: CompletableDeferred<Boolean>? = null
    @Volatile
    private var descriptorDeferred: CompletableDeferred<Boolean>? = null
    @Volatile
    private var writeDeferred: CompletableDeferred<Boolean>? = null
    @Volatile
    private var readDeferred: CompletableDeferred<ByteArray?>? = null

    @SuppressLint("MissingPermission")
    override suspend fun connect(callbacks: BleGattCallbacks): Boolean {
        if (!hasBlePermissions()) return false
        val btManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            ?: return false
        val adapter = btManager.adapter
        if (adapter == null || !adapter.isEnabled) return false
        val scanner = adapter.bluetoothLeScanner ?: return false

        this.callbacks = callbacks
        val deviceDeferred = CompletableDeferred<BluetoothDevice?>()
        val scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                deviceDeferred.complete(result.device)
            }

            override fun onScanFailed(errorCode: Int) {
                deviceDeferred.complete(null)
            }
        }

        val device = try {
            val filters = listOf(
                ScanFilter.Builder()
                    .setServiceUuid(ParcelUuid(MeshtasticBleUuids.SERVICE_UUID))
                    .build()
            )
            val settings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build()
            scanner.startScan(filters, settings, scanCallback)
            withTimeoutOrNull(10_000L) { deviceDeferred.await() }
        } catch (_: SecurityException) {
            null
        } finally {
            try {
                scanner.stopScan(scanCallback)
            } catch (_: Throwable) {
            }
        } ?: return false

        val pendingConnect = CompletableDeferred<Boolean>()
        connectDeferred = pendingConnect

        val gattCallback = object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                if (newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS) {
                    pendingConnect.complete(true)
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    val wasConnecting = !pendingConnect.isCompleted
                    pendingConnect.complete(false)
                    completeAllPendingWithFailure()
                    if (!wasConnecting) {
                        callbacks.onDisconnected(
                            isError = status != BluetoothGatt.GATT_SUCCESS,
                            reason = "BLE GATT disconnected (status=$status)",
                        )
                    }
                }
            }

            override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
                val resolved = if (status == BluetoothGatt.GATT_SUCCESS) mtu else MeshtasticBleUuids.DEFAULT_MTU
                mtuDeferred?.complete(resolved)
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                servicesDeferred?.complete(status == BluetoothGatt.GATT_SUCCESS)
            }

            override fun onDescriptorWrite(
                gatt: BluetoothGatt,
                descriptor: BluetoothGattDescriptor,
                status: Int,
            ) {
                descriptorDeferred?.complete(status == BluetoothGatt.GATT_SUCCESS)
            }

            override fun onCharacteristicWrite(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                status: Int,
            ) {
                writeDeferred?.complete(status == BluetoothGatt.GATT_SUCCESS)
            }

            override fun onCharacteristicRead(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                value: ByteArray,
                status: Int,
            ) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    readDeferred?.complete(value.copyOf())
                } else {
                    readDeferred?.complete(null)
                }
            }

            @Deprecated("Deprecated in API 33")
            @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
            override fun onCharacteristicRead(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                status: Int,
            ) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    val bytes = characteristic.value?.copyOf() ?: ByteArray(0)
                    readDeferred?.complete(bytes)
                } else {
                    readDeferred?.complete(null)
                }
            }

            override fun onCharacteristicChanged(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                value: ByteArray,
            ) {
                callbacks.onCharacteristicNotified(characteristic.uuid, value.copyOf())
            }

            @Deprecated("Deprecated in API 33")
            @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
            override fun onCharacteristicChanged(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
            ) {
                val bytes = characteristic.value?.copyOf() ?: ByteArray(0)
                callbacks.onCharacteristicNotified(characteristic.uuid, bytes)
            }
        }

        return try {
            val activeGatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
            if (activeGatt == null) {
                pendingConnect.complete(false)
                return false
            }
            gatt = activeGatt
            val connected = withTimeoutOrNull(10_000L) { pendingConnect.await() } == true
            if (!connected) {
                close()
            }
            connected
        } catch (_: SecurityException) {
            close()
            false
        }
    }

    @SuppressLint("MissingPermission")
    override suspend fun requestMtu(mtu: Int): Int {
        val activeGatt = gatt ?: return MeshtasticBleUuids.DEFAULT_MTU
        val deferred = CompletableDeferred<Int>()
        mtuDeferred = deferred
        return try {
            if (!activeGatt.requestMtu(mtu)) {
                mtuDeferred = null
                return MeshtasticBleUuids.DEFAULT_MTU
            }
            withTimeoutOrNull(5_000L) { deferred.await() } ?: MeshtasticBleUuids.DEFAULT_MTU
        } catch (_: SecurityException) {
            MeshtasticBleUuids.DEFAULT_MTU
        } finally {
            mtuDeferred = null
        }
    }

    @SuppressLint("MissingPermission")
    override suspend fun discoverMeshtasticCharacteristics(): Boolean {
        val activeGatt = gatt ?: return false
        val deferred = CompletableDeferred<Boolean>()
        servicesDeferred = deferred
        val discovered = try {
            if (!activeGatt.discoverServices()) {
                servicesDeferred = null
                return false
            }
            withTimeoutOrNull(10_000L) { deferred.await() } == true
        } catch (_: SecurityException) {
            false
        } finally {
            servicesDeferred = null
        }
        if (!discovered) return false

        val service = activeGatt.getService(MeshtasticBleUuids.SERVICE_UUID) ?: return false
        val toRadio = service.getCharacteristic(MeshtasticBleUuids.TO_RADIO_UUID)
        val fromRadio = service.getCharacteristic(MeshtasticBleUuids.FROM_RADIO_UUID)
        val fromNum = service.getCharacteristic(MeshtasticBleUuids.FROM_NUM_UUID)
        if (toRadio == null || fromRadio == null || fromNum == null) return false

        toRadioChar = toRadio
        fromRadioChar = fromRadio
        fromNumChar = fromNum
        return true
    }

    @SuppressLint("MissingPermission")
    override suspend fun enableFromNumNotifications(): Boolean {
        val activeGatt = gatt ?: return false
        val char = fromNumChar ?: return false
        return try {
            if (!activeGatt.setCharacteristicNotification(char, true)) return false
            val cccd = char.getDescriptor(MeshtasticBleUuids.CCCD_UUID) ?: return false
            val deferred = CompletableDeferred<Boolean>()
            descriptorDeferred = deferred
            val initiated = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                activeGatt.writeDescriptor(
                    cccd,
                    BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE,
                ) == BluetoothStatusCodes.SUCCESS
            } else {
                @Suppress("DEPRECATION")
                cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                @Suppress("DEPRECATION")
                activeGatt.writeDescriptor(cccd)
            }
            if (!initiated) return false
            withTimeoutOrNull(5_000L) { deferred.await() } == true
        } catch (_: SecurityException) {
            false
        } finally {
            descriptorDeferred = null
        }
    }

    @SuppressLint("MissingPermission")
    override suspend fun writeToRadio(bytes: ByteArray): Boolean {
        val activeGatt = gatt ?: return false
        val char = toRadioChar ?: return false
        val deferred = CompletableDeferred<Boolean>()
        writeDeferred = deferred
        return try {
            val initiated = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                activeGatt.writeCharacteristic(
                    char,
                    bytes,
                    BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT,
                ) == BluetoothStatusCodes.SUCCESS
            } else {
                char.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                @Suppress("DEPRECATION")
                char.value = bytes
                @Suppress("DEPRECATION")
                activeGatt.writeCharacteristic(char)
            }
            if (!initiated) return false
            withTimeoutOrNull(5_000L) { deferred.await() } == true
        } catch (_: SecurityException) {
            false
        } finally {
            writeDeferred = null
        }
    }

    @SuppressLint("MissingPermission")
    override suspend fun readFromRadio(): ByteArray? {
        val activeGatt = gatt ?: return null
        val char = fromRadioChar ?: return null
        val deferred = CompletableDeferred<ByteArray?>()
        readDeferred = deferred
        return try {
            if (!activeGatt.readCharacteristic(char)) return null
            withTimeoutOrNull(5_000L) { deferred.await() }
        } catch (_: SecurityException) {
            null
        } finally {
            readDeferred = null
        }
    }

    @SuppressLint("MissingPermission")
    override fun close() {
        completeAllPendingWithFailure()
        callbacks = null
        toRadioChar = null
        fromRadioChar = null
        fromNumChar = null
        try {
            gatt?.disconnect()
            gatt?.close()
        } catch (_: Throwable) {
        }
        gatt = null
    }

    private fun completeAllPendingWithFailure() {
        connectDeferred?.complete(false)
        mtuDeferred?.complete(MeshtasticBleUuids.DEFAULT_MTU)
        servicesDeferred?.complete(false)
        descriptorDeferred?.complete(false)
        writeDeferred?.complete(false)
        readDeferred?.complete(null)
    }

    private fun hasBlePermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) ==
                PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH) ==
                PackageManager.PERMISSION_GRANTED
        }
    }
}
