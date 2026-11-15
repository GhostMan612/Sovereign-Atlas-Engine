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
import com.sovereignatlas.atlas.tactical.mesh.MeshtasticWireAdapter
import com.sovereignatlas.atlas.tactical.mesh.PacketDeliveryUpdate
import com.sovereignatlas.atlas.tactical.mesh.RadioConnectionState
import com.sovereignatlas.atlas.tactical.mesh.RadioHardwareInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

class AndroidBleRadioClient internal constructor(
    private val context: Context?,
    private val scope: CoroutineScope,
    private val transportFactory: () -> BleGattTransport,
    private val clockMs: () -> Long = { System.currentTimeMillis() },
    private val handshakeTimeoutMs: Long = 10_000L,
) : MeshRadioClient {

    constructor(
        context: Context,
        scope: CoroutineScope,
    ) : this(
        context = context,
        scope = scope,
        transportFactory = { AndroidBleGattTransport(context) },
    )

    private val session = MeshtasticProtocolSession(clockMs = clockMs)
    private val connectMutex = Mutex()
    private val gattMutex = Mutex()
    private val drainMutex = Mutex()
    private val drainRequested = AtomicBoolean(false)
    private val connectionGeneration = AtomicLong(0L)

    @Volatile
    private var activeTransport: BleGattTransport? = null

    @Volatile
    private var negotiatedAttPayloadMax: Int = 20

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
        var setupTransport: BleGattTransport? = null
        val initiated = connectMutex.withLock {
            if (session.isReady.value && activeTransport != null) {
                return@withContext true
            }
            cleanupTransportLocked(sendDisconnectEnvelope = false, isError = false, reason = null)

            setupGeneration = connectionGeneration.incrementAndGet()
            session.beginConnecting()
            val transport = transportFactory()
            activeTransport = transport
            setupTransport = transport
            negotiatedAttPayloadMax = 20

            val callbacks = object : BleGattCallbacks {
                override fun onCharacteristicNotified(uuid: UUID, value: ByteArray) {
                    if (uuid == MeshtasticBleUuids.FROM_NUM_UUID) {
                        scheduleDrainFromRadio()
                    }
                }

                override fun onDisconnected(isError: Boolean, reason: String) {
                    scope.launch(Dispatchers.IO) {
                        disconnectInternal(isError = isError, reason = reason)
                    }
                }
            }

            val connected = gattMutex.withLock { transport.connect(callbacks) }
            if (!connected) {
                cleanupTransportLocked(sendDisconnectEnvelope = false, isError = true, reason = "BLE connection failed")
                return@withLock false
            }

            val mtu = gattMutex.withLock { transport.requestMtu(MeshtasticBleUuids.PREFERRED_MTU) }
            negotiatedAttPayloadMax = (mtu - 3).coerceIn(20, MeshtasticWireAdapter.MAX_TO_FROM_RADIO_SIZE)

            val discovered = gattMutex.withLock { transport.discoverMeshtasticCharacteristics() }
            if (!discovered) {
                cleanupTransportLocked(sendDisconnectEnvelope = false, isError = true, reason = "Missing Meshtastic BLE characteristics")
                return@withLock false
            }

            val subscribed = gattMutex.withLock { transport.enableFromNumNotifications() }
            if (!subscribed) {
                cleanupTransportLocked(sendDisconnectEnvelope = false, isError = true, reason = "Failed to enable FromNum notifications")
                return@withLock false
            }

            rebootMonitorJob = scope.launch(Dispatchers.IO) {
                session.rebootEvents.collect {
                    if (activeTransport === transport) {
                        initiateWantConfigAndDrain(transport)
                    }
                }
            }

            val handshakeSent = initiateWantConfigAndDrain(transport)
            if (!handshakeSent) {
                cleanupTransportLocked(sendDisconnectEnvelope = false, isError = true, reason = "Failed to send want_config_id")
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
                    reason = "Meshtastic BLE want_config handshake timeout",
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
        val prepared = session.prepareOutgoingPacket(packet, maxEnvelopeBytes = negotiatedAttPayloadMax)
            ?: return@withContext false
        val wrote = gattMutex.withLock {
            if (activeTransport !== transport) {
                false
            } else {
                transport.writeToRadio(prepared.toRadioBytes)
            }
        }
        if (!wrote) {
            session.markTransportWriteFailed(prepared.packet.id, "BLE ToRadio GATT write failed")
            return@withContext false
        }
        true
    }

    override suspend fun pingNode(targetNodeNum: Long): Boolean {
        return transmitPacket(session.buildPingPacket(targetNodeNum))
    }

    private suspend fun initiateWantConfigAndDrain(transport: BleGattTransport): Boolean {
        val wantConfigBytes = session.beginHandshake(isUsb = false)
        val wrote = gattMutex.withLock {
            if (activeTransport !== transport) false else transport.writeToRadio(wantConfigBytes)
        }
        if (!wrote) return false
        drainFromRadioLoop(transport)
        return true
    }

    private fun scheduleDrainFromRadio() {
        drainRequested.set(true)
        scope.launch(Dispatchers.IO) {
            val transport = activeTransport ?: return@launch
            drainFromRadioLoop(transport)
        }
    }

    private suspend fun drainFromRadioLoop(transport: BleGattTransport) {
        drainMutex.withLock {
            do {
                drainRequested.set(false)
                while (activeTransport === transport) {
                    val bytes = gattMutex.withLock {
                        if (activeTransport !== transport) null else transport.readFromRadio()
                    } ?: break
                    if (bytes.isEmpty()) break
                    session.onFromRadioBytes(bytes)
                }
            } while (drainRequested.get() && activeTransport === transport)
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
        val transport = activeTransport
        activeTransport = null
        negotiatedAttPayloadMax = 20
        if (transport != null) {
            if (sendDisconnectEnvelope) {
                try {
                    val disconnectFrame = session.encodeDisconnectCommand()
                    gattMutex.withLock { transport.writeToRadio(disconnectFrame) }
                } catch (_: Throwable) {
                }
            }
            transport.close()
        }
        session.onTransportDisconnected(isError = isError, reason = reason)
    }
}
