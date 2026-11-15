// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.android.mesh

import android.content.Context
import com.sovereignatlas.atlas.tactical.mesh.MeshNodeInfo
import com.sovereignatlas.atlas.tactical.mesh.MeshPacket
import com.sovereignatlas.atlas.tactical.mesh.MeshRadioClient
import com.sovereignatlas.atlas.tactical.mesh.MeshtasticProtocolSession
import com.sovereignatlas.atlas.tactical.mesh.MeshtasticSerialFramer
import com.sovereignatlas.atlas.tactical.mesh.PacketDeliveryUpdate
import com.sovereignatlas.atlas.tactical.mesh.RadioConnectionState
import com.sovereignatlas.atlas.tactical.mesh.RadioHardwareInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicLong

class AndroidUsbSerialRadioClient internal constructor(
    private val context: Context?,
    private val scope: CoroutineScope,
    private val transportFactory: () -> UsbSerialTransport,
    private val clockMs: () -> Long = { System.currentTimeMillis() },
    private val handshakeTimeoutMs: Long = 10_000L,
) : MeshRadioClient {

    constructor(
        context: Context,
        scope: CoroutineScope,
    ) : this(
        context = context,
        scope = scope,
        transportFactory = { AndroidUsbSerialTransport(context) },
    )

    private val session = MeshtasticProtocolSession(clockMs = clockMs)
    private val connectMutex = Mutex()
    private val writeMutex = Mutex()
    private val connectionGeneration = AtomicLong(0L)

    @Volatile
    private var activeTransport: UsbSerialTransport? = null

    private var readLoopJob: Job? = null
    private var rebootMonitorJob: Job? = null

    override val connectionState: StateFlow<RadioConnectionState> = session.connectionState
    override val isReady: StateFlow<Boolean> = session.isReady
    override val discoveredNodes: StateFlow<List<MeshNodeInfo>> = session.discoveredNodes
    override val inboundPackets: SharedFlow<MeshPacket> = session.inboundPackets
    override val activeRadioInfo: StateFlow<RadioHardwareInfo?> = session.activeRadioInfo
    override val deliveryUpdates: SharedFlow<PacketDeliveryUpdate> = session.deliveryUpdates
    override val deliveryStates: StateFlow<Map<Long, PacketDeliveryUpdate>> = session.deliveryStates

    val malformedFrameCount: StateFlow<Long> = session.malformedFrameCount

    override suspend fun connect(): Boolean = withContext(Dispatchers.IO) {
        var setupGeneration = 0L
        var setupTransport: UsbSerialTransport? = null
        val initiated = connectMutex.withLock {
            if (session.isReady.value && activeTransport != null) {
                return@withContext true
            }
            cleanupTransportLocked(sendDisconnectEnvelope = false, isError = false, reason = null)

            setupGeneration = connectionGeneration.incrementAndGet()
            session.beginConnecting()
            val transport = transportFactory()
            if (!transport.open()) {
                session.onTransportDisconnected(isError = true, reason = "Failed to open USB serial radio")
                return@withLock false
            }
            activeTransport = transport
            setupTransport = transport

            val framer = MeshtasticSerialFramer()
            readLoopJob = scope.launch(Dispatchers.IO) {
                try {
                    while (isActive && activeTransport === transport) {
                        when (val result = transport.read()) {
                            is UsbReadResult.Data -> {
                                val frames = framer.feed(result.bytes)
                                for (frame in frames) {
                                    session.onFromRadioBytes(frame)
                                }
                            }
                            is UsbReadResult.Timeout -> Unit
                            is UsbReadResult.Closed -> {
                                if (activeTransport === transport) {
                                    scope.launch(Dispatchers.IO) {
                                        disconnectInternal(isError = result.isError, reason = result.reason)
                                    }
                                }
                                break
                            }
                        }
                    }
                } catch (t: Throwable) {
                    if (isActive && activeTransport === transport) {
                        scope.launch(Dispatchers.IO) {
                            disconnectInternal(isError = true, reason = t.message ?: "USB read error")
                        }
                    }
                }
            }

            rebootMonitorJob = scope.launch(Dispatchers.IO) {
                session.rebootEvents.collect {
                    if (activeTransport === transport) {
                        framer.reset()
                        sendWantConfigFrame(transport)
                    }
                }
            }

            val sentWantConfig = sendWantConfigFrame(transport)
            if (!sentWantConfig) {
                cleanupTransportLocked(sendDisconnectEnvelope = false, isError = true, reason = "Failed to send USB want_config_id")
                return@withLock false
            }
            true
        }
        if (!initiated) return@withContext false

        withTimeoutOrNull(handshakeTimeoutMs) {
            session.connectionState.first { state ->
                state == RadioConnectionState.CONNECTED ||
                    state == RadioConnectionState.STREAMING ||
                    state == RadioConnectionState.ERROR ||
                    state == RadioConnectionState.DISCONNECTED
            }
        }

        connectMutex.withLock {
            val readyNow = connectionGeneration.get() == setupGeneration &&
                activeTransport === setupTransport &&
                session.isReady.value
            if (!readyNow && activeTransport === setupTransport && connectionGeneration.get() == setupGeneration) {
                cleanupTransportLocked(
                    sendDisconnectEnvelope = false,
                    isError = true,
                    reason = "Meshtastic USB want_config handshake timeout",
                )
            }
            readyNow
        }
    }

    override suspend fun disconnect(): Unit = withContext(Dispatchers.IO) {
        disconnectInternal(isError = false, reason = null)
    }

    override suspend fun transmitPacket(packet: MeshPacket): Boolean = withContext(Dispatchers.IO) {
        val transport = activeTransport ?: return@withContext false
        val prepared = session.prepareOutgoingPacket(packet) ?: return@withContext false
        val framed = MeshtasticSerialFramer.frame(prepared.toRadioBytes)
        val wrote = writeMutex.withLock {
            if (activeTransport !== transport) false else transport.write(framed)
        }
        if (!wrote) {
            session.markTransportWriteFailed(prepared.packet.id, "USB serial write failed")
            return@withContext false
        }
        true
    }

    override suspend fun pingNode(targetNodeNum: Long): Boolean {
        return transmitPacket(session.buildPingPacket(targetNodeNum))
    }

    private suspend fun sendWantConfigFrame(transport: UsbSerialTransport): Boolean {
        val wantConfigBytes = session.beginHandshake(isUsb = true)
        val framed = MeshtasticSerialFramer.frame(wantConfigBytes)
        val combined = ByteArray(MeshtasticSerialFramer.WAKE_PREAMBLE.size + framed.size)
        System.arraycopy(MeshtasticSerialFramer.WAKE_PREAMBLE, 0, combined, 0, MeshtasticSerialFramer.WAKE_PREAMBLE.size)
        System.arraycopy(framed, 0, combined, MeshtasticSerialFramer.WAKE_PREAMBLE.size, framed.size)
        return writeMutex.withLock {
            if (activeTransport !== transport) false else transport.write(combined)
        }
    }

    private suspend fun disconnectInternal(isError: Boolean, reason: String?) {
        connectMutex.withLock {
            cleanupTransportLocked(sendDisconnectEnvelope = !isError, isError = isError, reason = reason)
        }
    }

    private suspend fun cleanupTransportLocked(
        sendDisconnectEnvelope: Boolean,
        isError: Boolean,
        reason: String?,
    ) {
        rebootMonitorJob?.cancel()
        rebootMonitorJob = null
        readLoopJob?.cancel()
        readLoopJob = null

        val transport = activeTransport
        activeTransport = null
        if (transport != null) {
            if (sendDisconnectEnvelope) {
                try {
                    val disconnectFrame = MeshtasticSerialFramer.frame(session.encodeDisconnectCommand())
                    writeMutex.withLock { transport.write(disconnectFrame) }
                } catch (_: Throwable) {
                }
            }
            transport.close()
        }
        session.onTransportDisconnected(isError = isError, reason = reason)
    }
}
