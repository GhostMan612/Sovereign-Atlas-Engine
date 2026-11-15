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
import kotlinx.coroutines.delay

sealed class UsbReadResult {
    data class Data(val bytes: ByteArray) : UsbReadResult()
    data object Timeout : UsbReadResult()
    data class Closed(val reason: String, val isError: Boolean) : UsbReadResult()
}

interface UsbSerialTransport {
    fun open(): Boolean
    suspend fun write(bytes: ByteArray, timeoutMs: Int = 2_000): Boolean
    suspend fun read(maxBytes: Int = 512, timeoutMs: Int = 250): UsbReadResult
    fun close()
}

class AndroidUsbSerialTransport(
    private val context: Context,
) : UsbSerialTransport {

    private var usbManager: UsbManager? = null
    private var device: UsbDevice? = null
    private var connection: UsbDeviceConnection? = null
    private var claimedInterface: UsbInterface? = null
    private var inEndpoint: UsbEndpoint? = null
    private var outEndpoint: UsbEndpoint? = null

    @Volatile
    private var closed: Boolean = false

    override fun open(): Boolean {
        closed = false
        val manager = context.getSystemService(Context.USB_SERVICE) as? UsbManager ?: return false
        val targetDevice = manager.deviceList.values.firstOrNull { isSupportedMeshtasticUsb(it) }
            ?: return false
        if (!manager.hasPermission(targetDevice)) return false

        val selection = selectDataInterfaceAndEndpoints(targetDevice) ?: return false
        val conn = manager.openDevice(targetDevice) ?: return false
        if (!conn.claimInterface(selection.iface, true)) {
            conn.close()
            return false
        }

        configureCdcLineCoding(conn, selection.iface.id)

        usbManager = manager
        device = targetDevice
        connection = conn
        claimedInterface = selection.iface
        inEndpoint = selection.inEndpoint
        outEndpoint = selection.outEndpoint
        return true
    }

    override suspend fun write(bytes: ByteArray, timeoutMs: Int): Boolean {
        val conn = connection ?: return false
        val ep = outEndpoint ?: return false
        if (closed) return false
        var offset = 0
        while (offset < bytes.size) {
            val remaining = bytes.size - offset
            val chunk = if (offset == 0 && remaining <= bytes.size) {
                bytes
            } else {
                bytes.copyOfRange(offset, bytes.size)
            }
            val written = conn.bulkTransfer(ep, chunk, chunk.size, timeoutMs)
            if (written <= 0) return false
            offset += written
        }
        return true
    }

    override suspend fun read(maxBytes: Int, timeoutMs: Int): UsbReadResult {
        val conn = connection ?: return UsbReadResult.Closed("USB connection closed", isError = false)
        val ep = inEndpoint ?: return UsbReadResult.Closed("USB endpoint missing", isError = true)
        if (closed) return UsbReadResult.Closed("USB transport closed", isError = false)

        val buffer = ByteArray(maxBytes)
        val len = conn.bulkTransfer(ep, buffer, buffer.size, timeoutMs)
        if (len > 0) {
            return UsbReadResult.Data(buffer.copyOf(len))
        }
        val activeDevice = device
        val attached = activeDevice != null && (usbManager?.deviceList?.containsKey(activeDevice.deviceName) == true)
        if (closed || !attached) {
            return UsbReadResult.Closed("USB device detached", isError = !closed)
        }
        delay(5L)
        return UsbReadResult.Timeout
    }

    override fun close() {
        closed = true
        try {
            val iface = claimedInterface
            if (iface != null) {
                connection?.releaseInterface(iface)
            }
            connection?.close()
        } catch (_: Throwable) {
        }
        claimedInterface = null
        connection = null
        device = null
        inEndpoint = null
        outEndpoint = null
        usbManager = null
    }

    private fun configureCdcLineCoding(conn: UsbDeviceConnection, interfaceIndex: Int) {
        val lineCoding = byteArrayOf(
            0x00, 0xC2.toByte(), 0x01, 0x00,
            0x00,
            0x00,
            0x08,
        )
        conn.controlTransfer(0x21, 0x20, 0, interfaceIndex, lineCoding, lineCoding.size, 500)
        conn.controlTransfer(0x21, 0x22, 0x03, interfaceIndex, null, 0, 500)
    }

    private data class EndpointSelection(
        val iface: UsbInterface,
        val inEndpoint: UsbEndpoint,
        val outEndpoint: UsbEndpoint,
    )

    private fun selectDataInterfaceAndEndpoints(dev: UsbDevice): EndpointSelection? {
        var fallback: EndpointSelection? = null
        for (i in 0 until dev.interfaceCount) {
            val iface = dev.getInterface(i)
            var bulkIn: UsbEndpoint? = null
            var bulkOut: UsbEndpoint? = null
            for (j in 0 until iface.endpointCount) {
                val ep = iface.getEndpoint(j)
                if (ep.type == UsbConstants.USB_ENDPOINT_XFER_BULK) {
                    if (ep.direction == UsbConstants.USB_DIR_IN) bulkIn = ep
                    if (ep.direction == UsbConstants.USB_DIR_OUT) bulkOut = ep
                }
            }
            if (bulkIn != null && bulkOut != null) {
                val candidate = EndpointSelection(iface, bulkIn, bulkOut)
                if (iface.interfaceClass == UsbConstants.USB_CLASS_CDC_DATA) {
                    return candidate
                }
                if (fallback == null) {
                    fallback = candidate
                }
            }
        }
        return fallback
    }

    private fun isSupportedMeshtasticUsb(dev: UsbDevice): Boolean {
        if (dev.vendorId in SUPPORTED_USB_VIDS) return true
        for (i in 0 until dev.interfaceCount) {
            val cls = dev.getInterface(i).interfaceClass
            if (cls == UsbConstants.USB_CLASS_COMM || cls == UsbConstants.USB_CLASS_CDC_DATA) {
                return true
            }
        }
        return false
    }

    companion object {
        private val SUPPORTED_USB_VIDS = setOf(
            0x10C4,
            0x1A86,
            0x0403,
            0x303A,
            0x239A,
            0x2886,
            0x2E8A,
            0x1915,
        )
    }
}
