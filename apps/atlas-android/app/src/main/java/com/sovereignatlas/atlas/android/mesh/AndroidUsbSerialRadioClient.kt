// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.android.mesh

import android.content.Context
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import android.util.Log
import com.sovereignatlas.atlas.tactical.mesh.FrameDecodeResult
import com.sovereignatlas.atlas.tactical.mesh.MeshNodeInfo
import com.sovereignatlas.atlas.tactical.mesh.MeshPacket
import com.sovereignatlas.atlas.tactical.mesh.MeshRadioClient
import com.sovereignatlas.atlas.tactical.mesh.MeshtasticFrameCodec
import com.sovereignatlas.atlas.tactical.mesh.PortNum
import com.sovereignatlas.atlas.tactical.mesh.RadioConnectionState
import com.sovereignatlas.atlas.tactical.mesh.RadioHardwareInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AndroidUsbSerialRadioClient(
    private val context: Context,
    private val scope: CoroutineScope,
) : MeshRadioClient {

    private val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager

    private val _connectionState = MutableStateFlow(RadioConnectionState.DISCONNECTED)
    override val connectionState: StateFlow<RadioConnectionState> = _connectionState.asStateFlow()

    private val _discoveredNodes = MutableStateFlow<List<MeshNodeInfo>>(emptyList())
    override val discoveredNodes: StateFlow<List<MeshNodeInfo>> = _discoveredNodes.asStateFlow()

    private val _inboundPackets = MutableSharedFlow<MeshPacket>(extraBufferCapacity = 128)
    override val inboundPackets: SharedFlow<MeshPacket> = _inboundPackets.asSharedFlow()

    private val _activeRadioInfo = MutableStateFlow<RadioHardwareInfo?>(null)
    override val activeRadioInfo: StateFlow<RadioHardwareInfo?> = _activeRadioInfo.asStateFlow()

    private var connection: UsbDeviceConnection? = null
    private var usbInterface: UsbInterface? = null
    private var inEndpoint: UsbEndpoint? = null
    private var outEndpoint: UsbEndpoint? = null
    private var readJob: Job? = null

    override suspend fun connect(): Boolean = withContext(Dispatchers.IO) {
        if (_connectionState.value == RadioConnectionState.CONNECTED ||
            _connectionState.value == RadioConnectionState.STREAMING
        ) {
            return@withContext true
        }

        _connectionState.value = RadioConnectionState.CONNECTING
        val deviceList = usbManager.deviceList
        val targetDevice = findMeshtasticDevice(deviceList.values)
        if (targetDevice == null) {
            _connectionState.value = RadioConnectionState.DISCONNECTED
            return@withContext false
        }

        if (!usbManager.hasPermission(targetDevice)) {
            _connectionState.value = RadioConnectionState.ERROR
            return@withContext false
        }

        val conn = usbManager.openDevice(targetDevice)
        if (conn == null) {
            _connectionState.value = RadioConnectionState.ERROR
            return@withContext false
        }

        var foundIn: UsbEndpoint? = null
        var foundOut: UsbEndpoint? = null
        var claimedInterface: UsbInterface? = null

        for (i in 0 until targetDevice.interfaceCount) {
            val iface = targetDevice.getInterface(i)
            var inEp: UsbEndpoint? = null
            var outEp: UsbEndpoint? = null
            for (j in 0 until iface.endpointCount) {
                val ep = iface.getEndpoint(j)
                if (ep.type == UsbConstants.USB_ENDPOINT_XFER_BULK) {
                    if (ep.direction == UsbConstants.USB_DIR_IN) {
                        inEp = ep
                    } else if (ep.direction == UsbConstants.USB_DIR_OUT) {
                        outEp = ep
                    }
                }
            }
            if (inEp != null && outEp != null) {
                if (conn.claimInterface(iface, true)) {
                    claimedInterface = iface
                    foundIn = inEp
                    foundOut = outEp
                    break
                }
            }
        }

        if (claimedInterface == null || foundIn == null || foundOut == null) {
            conn.close()
            _connectionState.value = RadioConnectionState.ERROR
            return@withContext false
        }

        connection = conn
        usbInterface = claimedInterface
        inEndpoint = foundIn
        outEndpoint = foundOut

        val radioInfo = RadioHardwareInfo(
            radioModel = targetDevice.productName ?: "Meshtastic USB Radio",
            frequencyBand = "915 MHz US",
            firmwareVersion = "2.3.x",
            nodeNum = (targetDevice.deviceId.toLong() and 0xFFFFFFFFL),
            isUsb = true,
        )
        _activeRadioInfo.value = radioInfo
        _connectionState.value = RadioConnectionState.CONNECTED

        startIoLoop()
        true
    }

    override suspend fun disconnect(): Unit = withContext(Dispatchers.IO) {
        readJob?.cancel()
        readJob = null
        try {
            val iface = usbInterface
            if (iface != null) {
                connection?.releaseInterface(iface)
            }
            connection?.close()
        } catch (e: Exception) {
            Log.w("AndroidUsbSerialRadioClient", "Error during USB disconnect", e)
        } finally {
            connection = null
            usbInterface = null
            inEndpoint = null
            outEndpoint = null
            _activeRadioInfo.value = null
            _connectionState.value = RadioConnectionState.DISCONNECTED
        }
    }

    override suspend fun transmitPacket(packet: MeshPacket): Boolean = withContext(Dispatchers.IO) {
        val conn = connection ?: return@withContext false
        val outEp = outEndpoint ?: return@withContext false

        val framedBytes = MeshtasticFrameCodec.encodeFrame(packet.payload)
        val written = conn.bulkTransfer(outEp, framedBytes, framedBytes.size, USB_TIMEOUT_MS)
        return@withContext written == framedBytes.size
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

    private fun startIoLoop() {
        readJob?.cancel()
        readJob = scope.launch(Dispatchers.IO) {
            val conn = connection ?: return@launch
            val inEp = inEndpoint ?: return@launch
            val buffer = ByteArray(READ_BUFFER_SIZE)
            var accumulated = ByteArray(0)

            _connectionState.value = RadioConnectionState.STREAMING

            while (isActive) {
                val bytesRead = conn.bulkTransfer(inEp, buffer, buffer.size, READ_POLL_TIMEOUT_MS)
                if (bytesRead > 0) {
                    val chunk = buffer.copyOf(bytesRead)
                    val combined = accumulated + chunk
                    val result: FrameDecodeResult = MeshtasticFrameCodec.decodeStream(combined)
                    accumulated = result.remainingBytes

                    for (rawPayload in result.frames) {
                        processReceivedPayload(rawPayload)
                    }
                }
            }
        }
    }

    private fun processReceivedPayload(payload: ByteArray) {
        if (payload.isEmpty()) return
        val senderNodeNum = _activeRadioInfo.value?.nodeNum ?: 0x1A2B3C4DL
        val packet = MeshPacket(
            from = senderNodeNum,
            to = 0xFFFFFFFFL,
            portNum = PortNum.PORTNUM_ATAK_PLUGIN,
            payload = payload,
            rxTime = System.currentTimeMillis(),
        )
        _inboundPackets.tryEmit(packet)
    }

    private fun findMeshtasticDevice(devices: Collection<UsbDevice>): UsbDevice? {
        return devices.firstOrNull { dev ->
            val vid = dev.vendorId
            vid == VID_CP210X || vid == VID_FTDI || vid == VID_CH340 ||
                vid == VID_RASPBERRY_PI || vid == VID_ESPRESSIF ||
                dev.productName?.contains("Meshtastic", ignoreCase = true) == true
        }
    }

    companion object {
        private const val USB_TIMEOUT_MS = 2500
        private const val READ_POLL_TIMEOUT_MS = 500
        private const val READ_BUFFER_SIZE = 1024

        const val VID_FTDI = 0x0403
        const val VID_CP210X = 0x10C4
        const val VID_CH340 = 0x1A86
        const val VID_RASPBERRY_PI = 0x2E8A
        const val VID_ESPRESSIF = 0x303A
    }
}
