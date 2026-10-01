// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.android.comms

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.PowerManager
import android.util.Log
import com.sovereignatlas.atlas.geo.cot.CotIngestDecision
import com.sovereignatlas.atlas.geo.cot.CotParser
import com.sovereignatlas.atlas.geo.cot.MessageStore
import com.sovereignatlas.atlas.geo.cot.ParsedCot
import com.sovereignatlas.atlas.geo.cot.MarkerStore
import com.sovereignatlas.atlas.geo.cot.PliStore
import com.sovereignatlas.atlas.geo.cot.StringCotParser
import com.sovereignatlas.atlas.geo.cot.decideIngest
import java.io.IOException
import java.net.DatagramPacket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.MulticastSocket
import java.net.NetworkInterface
import java.net.SocketTimeoutException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class AtakMulticastListener(
    context: Context,
    private val pliStore: PliStore,
    private val messageStore: MessageStore,
    private val markerStore: MarkerStore,
    private val parser: CotParser,
) {
    private val appContext = context.applicationContext
    private val wifiManager = appContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val powerManager = appContext.getSystemService(Context.POWER_SERVICE) as PowerManager
    private val connectivityManager =
        appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private var multicastLock: WifiManager.MulticastLock? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var listenJob: Job? = null

    @Volatile
    private var socket: MulticastSocket? = null

    private val restartSignal = MutableSharedFlow<Unit>(replay = 1)

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            restartSignal.tryEmit(Unit)
        }
    }

    @Suppress("DEPRECATION")
    fun startListening(scope: CoroutineScope) {
        if (listenJob != null) return

        connectivityManager.registerDefaultNetworkCallback(networkCallback)
        restartSignal.tryEmit(Unit)

        listenJob = scope.launch(Dispatchers.IO) {
            restartSignal.collectLatest {
                releaseLocks()

                multicastLock = wifiManager.createMulticastLock("atlas-cot-listener").apply {
                    setReferenceCounted(false)
                    acquire()
                }
                wakeLock = powerManager.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "atlas:cot-listener",
                ).apply {
                    setReferenceCounted(false)
                    acquire()
                }

                val groupAddr = InetAddress.getByName("239.2.3.1")
                val wifiNetwork = connectivityManager.allNetworks.firstOrNull { network ->
                    connectivityManager.getNetworkCapabilities(network)
                        ?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
                }
                if (wifiNetwork == null) {
                    Log.w("AtakMulticastListener", "No WiFi network available — PLI broadcast skipped")
                    return@collectLatest
                }

                var joinedInterface: NetworkInterface? = null
                try {
                    val bound = MulticastSocket(6969).apply {
                        reuseAddress = true
                        setLoopbackMode(true)
                        timeToLive = 1
                        wifiNetwork.bindSocket(this)
                    }
                    socket = bound
                    val wifiInterface = NetworkInterface.getNetworkInterfaces().toList()
                        .firstOrNull { it.name.startsWith("wlan") && it.isUp }
                    if (wifiInterface != null) {
                        bound.joinGroup(InetSocketAddress(groupAddr, 6969), wifiInterface)
                        joinedInterface = wifiInterface
                    } else {
                        bound.joinGroup(groupAddr)
                    }
                    bound.soTimeout = 5000
                    val buffer = ByteArray(65507)
                    while (isActive) {
                        val packet = DatagramPacket(buffer, buffer.size)
                        try {
                            bound.receive(packet)
                            val data = packet.data.copyOf(packet.length)
                            ingest(data)
                        } catch (error: SocketTimeoutException) {
                            Unit
                        }
                    }
                } catch (error: IOException) {
                    delay(2000L)
                    restartSignal.tryEmit(Unit)
                } finally {
                    val closing = socket
                    socket = null
                    runCatching {
                        if (joinedInterface != null) {
                            closing?.leaveGroup(InetSocketAddress(groupAddr, 6969), joinedInterface)
                        } else {
                            closing?.leaveGroup(groupAddr)
                        }
                    }
                    runCatching { closing?.close() }
                }
            }
        }

        scope.launch(Dispatchers.Default) {
            while (isActive) {
                pliStore.prune(System.currentTimeMillis())
                delay(30_000L)
            }
        }
    }

    fun stopListening() {
        listenJob?.cancel()
        listenJob = null
        runCatching { connectivityManager.unregisterNetworkCallback(networkCallback) }
        releaseLocks()
    }

    fun sendMulticast(data: ByteArray): Boolean {
        val current = socket ?: return false
        return runCatching {
            val packet = DatagramPacket(
                data,
                data.size,
                InetAddress.getByName("239.2.3.1"),
                6969,
            )
            current.send(packet)
            true
        }.onFailure { error ->
            // Previously swallowed by runCatching, which made a dead radio look
            // identical to a successful send. Logged, never thrown: a mesh
            // failure must not crash the foreground service.
            Log.w("AtakMulticastListener", "CoT send failed", error)
        }.getOrDefault(false)
    }

    private fun releaseLocks() {
        runCatching { multicastLock?.takeIf { it.isHeld }?.release() }
        multicastLock = null
        runCatching { wakeLock?.takeIf { it.isHeld }?.release() }
        wakeLock = null
    }

    /**
     * Routes one received datagram.
     *
     * XML goes to [StringCotParser], which is pure and therefore covered by the
     * host gate. Protobuf stays on the legacy byte reader: a pure Kotlin string
     * scanner cannot decode a wire-format protobuf, and routing it there anyway
     * would silently drop every protobuf peer. Real ATAK nets send both.
     *
     * Framing is `0xBF <version> 0xBF`, where version 0x01 means protobuf. Only
     * that case is diverted. An XML-framed or bare payload goes to the pure reader,
     * which locates `<event` and so ignores any leading header bytes whatever they
     * decode to.
     */
    private fun ingest(data: ByteArray) {
        if (isProtobufFramed(data)) {
            routeLegacy(parser.parse(data))
            return
        }

        val event = StringCotParser.parse(String(data, Charsets.UTF_8)) ?: return
        when (val decision = decideIngest(event, System.currentTimeMillis())) {
            is CotIngestDecision.UpsertPli -> pliStore.upsert(decision.pli)
            is CotIngestDecision.UpsertMarker -> markerStore.upsert(decision.marker)
            // One legacy call, and only for chat, whose payload lives in detail
            // elements the pure reader does not model.
            is CotIngestDecision.DelegateToLegacyChat ->
                (parser.parse(data) as? ParsedCot.Chat)?.let { messageStore.addMessage(it.message) }
            is CotIngestDecision.Ignore -> Unit
        }
    }

    private fun routeLegacy(parsed: ParsedCot?) {
        when (parsed) {
            is ParsedCot.Pli -> pliStore.upsert(parsed.pli)
            is ParsedCot.Chat -> messageStore.addMessage(parsed.message)
            is ParsedCot.Marker -> markerStore.upsert(parsed.marker)
            null -> Unit
        }
    }

    /** True for the `0xBF 0x01 0xBF` framing that precedes a protobuf payload. */
    private fun isProtobufFramed(data: ByteArray): Boolean =
        data.size >= 4 &&
            data[0] == PROTOBUF_MAGIC &&
            data[1] == PROTOBUF_VERSION &&
            data[2] == PROTOBUF_MAGIC

    private companion object {
        const val PROTOBUF_MAGIC = 0xBF.toByte()
        const val PROTOBUF_VERSION = 0x01.toByte()
    }
}
