// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.android.offline

import com.sovereignatlas.atlas.offline.BASEMAP_SESSION_TILES
import com.sovereignatlas.atlas.offline.DEM_DIR_NAME
import com.sovereignatlas.atlas.offline.DEM_SESSION_TILES
import com.sovereignatlas.atlas.offline.DemTileStore
import com.sovereignatlas.atlas.offline.GLOBAL_TILE_HARD_CAP
import com.sovereignatlas.atlas.offline.LocalTileServer
import com.sovereignatlas.atlas.offline.MbtilesTile
import com.sovereignatlas.atlas.offline.MbtilesTileSource
import com.sovereignatlas.atlas.offline.TileBucket
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

val MBTILES_TILE_EXTENSIONS = listOf(".png", ".pbf")

data class PackTileServerLimits(
    val maxWorkers: Int = 8,
    val maxQueuedRequests: Int = 16,
    val socketReadTimeoutMs: Int = 5_000,
    val requestTotalTimeoutMs: Long = 10_000L,
    val maxRequestLineBytes: Int = 1024,
    val maxHeaderCount: Int = 32,
    val maxTotalHeaderBytes: Int = 8192,
    val maxTileBytes: Int = 4 * 1024 * 1024,
    val acceptBacklog: Int = 32,
) {
    init {
        require(maxWorkers >= 1) { "maxWorkers must be >= 1" }
        require(maxQueuedRequests >= 0) { "maxQueuedRequests must be >= 0" }
        require(socketReadTimeoutMs >= 1) { "socketReadTimeoutMs must be >= 1" }
        require(requestTotalTimeoutMs >= 1L) { "requestTotalTimeoutMs must be >= 1" }
        require(maxRequestLineBytes >= 16) { "maxRequestLineBytes must be >= 16" }
        require(maxHeaderCount >= 0) { "maxHeaderCount must be >= 0" }
        require(maxTotalHeaderBytes >= maxRequestLineBytes) { "maxTotalHeaderBytes must be >= maxRequestLineBytes" }
        require(maxTileBytes >= 1) { "maxTileBytes must be >= 1" }
        require(acceptBacklog >= 1) { "acceptBacklog must be >= 1" }
    }
}

/**
 * Loopback tile server over `java.net.Socket`.
 *
 * Relocated here from the pure `offline/` tier by ADR-006, which completes the
 * extraction: `offline/` now holds no networking at all. This class implements the
 * pure [LocalTileServer] contract, so no caller names it.
 *
 * The quota constants it defaults to stay in `offline/` and are imported above.
 * Those are session policy - how many tiles a session may serve - and they belong
 * with the pack bookkeeping that defines them, not with the transport that
 * enforces them.
 *
 * HTTP status policy:
 * - `200 OK`: valid tile within quota and `maxTileBytes`.
 * - `400 Bad Request`: malformed request syntax, invalid header, unexpected request body, or premature EOF.
 * - `404 Not Found`: unknown route, traversal/unsafe pack ID, missing tile, or exhausted quota (compatible with MapLibre).
 * - `405 Method Not Allowed`: non-`GET` HTTP method.
 * - `408 Request Timeout`: per-read idle timeout or request header deadline exceeded.
 * - `413 Content Too Large`: tile exceeds `maxTileBytes`.
 * - `414 URI Too Long`: request line exceeds `maxRequestLineBytes`.
 * - `431 Request Header Fields Too Large`: header count or total header bytes exceeded.
 * - `503 Service Unavailable`: worker pool and bounded queue saturated.
 */
class PackTileServer(
    private val packsDir: () -> File,
    private val port: Int = 0,
    private val basemapQuota: Int = BASEMAP_SESSION_TILES,
    private val demQuota: Int = DEM_SESSION_TILES,
    private val globalCap: Int = GLOBAL_TILE_HARD_CAP,
    private val limits: PackTileServerLimits = PackTileServerLimits(),
) : LocalTileServer {
    private val lifecycleLock = Any()
    private val quotaLock = Any()
    private val running = AtomicBoolean(false)
    private val activeClients = ConcurrentHashMap.newKeySet<Socket>()
    private val workerSeq = AtomicInteger(0)

    @Volatile
    private var socket: ServerSocket? = null
    private var acceptThread: Thread? = null
    private var workerPool: ThreadPoolExecutor? = null
    private var baseHits = 0L
    private var demHits = 0L

    @Volatile
    override var demStore: DemTileStore? = null

    @Volatile
    override var mbtilesStore: MbtilesTileSource? = null

    override fun port(): Int = socket?.localPort ?: -1

    fun boundAddress(): java.net.InetAddress? = socket?.inetAddress

    override fun isRunning(): Boolean = running.get()

    override fun tileHits(): Long = synchronized(quotaLock) { baseHits + demHits }

    override fun basemapHits(): Long = synchronized(quotaLock) { baseHits }

    override fun demHits(): Long = synchronized(quotaLock) { demHits }

    internal fun activeConnectionCount(): Int = activeClients.size

    internal fun activeWorkerCount(): Int = workerPool?.activeCount ?: 0

    override fun start(): Int {
        synchronized(lifecycleLock) {
            if (running.get()) {
                return socket?.localPort ?: -1
            }
            val server = ServerSocket(port, limits.acceptBacklog, java.net.InetAddress.getLoopbackAddress())
            val queue = if (limits.maxQueuedRequests == 0) {
                SynchronousQueue<Runnable>()
            } else {
                ArrayBlockingQueue<Runnable>(limits.maxQueuedRequests)
            }
            val factory = ThreadFactory { runnable ->
                Thread(runnable, "atlas-tile-worker-${workerSeq.incrementAndGet()}").apply {
                    isDaemon = true
                }
            }
            val pool = ThreadPoolExecutor(
                limits.maxWorkers,
                limits.maxWorkers,
                30L,
                TimeUnit.SECONDS,
                queue,
                factory,
            )
            socket = server
            workerPool = pool
            running.set(true)
            acceptThread = thread(isDaemon = true, name = "atlas-tiles") {
                acceptLoop(server, pool)
            }
            return server.localPort
        }
    }

    override fun stop() {
        val serverToClose: ServerSocket?
        val poolToShutdown: ThreadPoolExecutor?
        val threadToJoin: Thread?
        synchronized(lifecycleLock) {
            running.set(false)
            serverToClose = socket
            socket = null
            poolToShutdown = workerPool
            workerPool = null
            threadToJoin = acceptThread
            acceptThread = null
        }
        closeQuietly(serverToClose)
        activeClients.forEach(java.util.function.Consumer<Socket> { client ->
            closeQuietly(client)
        })
        activeClients.clear()
        if (poolToShutdown != null) {
            poolToShutdown.shutdownNow()
            try {
                poolToShutdown.awaitTermination(2L, TimeUnit.SECONDS)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }
        if (threadToJoin != null && threadToJoin !== Thread.currentThread()) {
            try {
                threadToJoin.join(2_000L)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }
        mbtilesStore?.shutdown()
    }

    override fun tileUrl(packId: String): String? {
        val bound = port()
        if (bound <= 0) return null
        return "http://127.0.0.1:$bound/$packId/{z}/{x}/{y}.png"
    }

    override fun demTileUrl(): String? {
        val bound = port()
        if (bound <= 0) return null
        return "http://127.0.0.1:$bound/dem/{z}/{x}/{y}.png"
    }

    override fun demAvailable(): Boolean {
        return File(packsDir(), DEM_DIR_NAME).isDirectory
    }

    private fun acceptLoop(server: ServerSocket, pool: ThreadPoolExecutor) {
        while (running.get() && !server.isClosed) {
            val client = try {
                server.accept()
            } catch (_: Throwable) {
                if (running.get() && !server.isClosed) continue else break
            }
            if (!running.get()) {
                closeQuietly(client)
                break
            }
            activeClients.add(client)
            try {
                pool.execute {
                    serve(client)
                }
            } catch (_: RejectedExecutionException) {
                rejectOverloaded(client)
            } catch (_: Throwable) {
                activeClients.remove(client)
                closeQuietly(client)
            }
        }
    }

    private fun rejectOverloaded(client: Socket) {
        try {
            client.soTimeout = minOf(limits.socketReadTimeoutMs, 500)
            writeStatus(client.getOutputStream(), "HTTP/1.1 503 Service Unavailable")
        } catch (_: Throwable) {
            Unit
        } finally {
            activeClients.remove(client)
            closeQuietly(client)
        }
    }

    private fun serve(client: Socket) {
        try {
            client.use { socket ->
                if (!running.get()) return
                val output = socket.getOutputStream()
                val parsed = when (val result = readHttpRequest(socket)) {
                    is RequestOutcome.Success -> result
                    is RequestOutcome.Failure -> {
                        writeStatus(output, result.statusLine, result.extraHeaders)
                        return
                    }
                    RequestOutcome.EmptyEof -> return
                }
                val path = parsed.target
                val route = routeFor(path)
                if (route == null || !hasQuota(route.bucket)) {
                    writeStatus(output, "HTTP/1.1 404 Not Found")
                    return
                }

                if (route.bucket == TileBucket.DEM) {
                    val store = demStore
                    if (store != null) {
                        val tile = demStoreTile(path, store)
                        if (tile == null) {
                            writeStatus(output, "HTTP/1.1 404 Not Found")
                            return
                        }
                        sendBoundedTile(output, route.bucket, tile.first, tile.second)
                        return
                    }
                }
                val mbtilesTile = mbtilesTile(path)
                if (mbtilesTile != null) {
                    sendBoundedTile(output, route.bucket, mbtilesTile.data, mbtilesTile.mimeType)
                    return
                }
                if (isMbtilesPack(path)) {
                    writeStatus(output, "HTTP/1.1 404 Not Found")
                    return
                }
                val body = fileFor(path, route.bucket)
                if (body == null || !body.isFile) {
                    writeStatus(output, "HTTP/1.1 404 Not Found")
                    return
                }
                when (val readResult = readBoundedFileBytes(body, limits.maxTileBytes)) {
                    is FileReadResult.Success -> sendBoundedTile(output, route.bucket, readResult.bytes, "image/png")
                    FileReadResult.TooLarge -> writeStatus(output, "HTTP/1.1 413 Content Too Large")
                    FileReadResult.Unavailable -> writeStatus(output, "HTTP/1.1 404 Not Found")
                }
            }
        } catch (_: Throwable) {
            Unit
        } finally {
            activeClients.remove(client)
        }
    }

    private sealed interface RequestOutcome {
        data class Success(val target: String) : RequestOutcome
        data class Failure(val statusLine: String, val extraHeaders: String = "") : RequestOutcome
        data object EmptyEof : RequestOutcome
    }

    private sealed interface LineReadResult {
        data class Line(val text: String, val byteCount: Int) : LineReadResult
        data class Eof(val partialBytes: Int) : LineReadResult
        data object TooLong : LineReadResult
        data object InvalidCharacters : LineReadResult
    }

    private sealed interface FileReadResult {
        data class Success(val bytes: ByteArray) : FileReadResult
        data object TooLarge : FileReadResult
        data object Unavailable : FileReadResult
    }

    private fun readHttpRequest(socket: Socket): RequestOutcome {
        val deadlineNanos = System.nanoTime() + limits.requestTotalTimeoutMs * 1_000_000L
        val boundedInput = DeadlineBufferedInputStream(
            socket = socket,
            raw = socket.getInputStream(),
            socketReadTimeoutMs = limits.socketReadTimeoutMs,
            deadlineNanos = deadlineNanos,
        )
        try {
            val requestLineResult = readAsciiLine(boundedInput, limits.maxRequestLineBytes)
            val requestLine = when (requestLineResult) {
                is LineReadResult.Line -> requestLineResult
                is LineReadResult.Eof -> {
                    return if (requestLineResult.partialBytes == 0) {
                        RequestOutcome.EmptyEof
                    } else {
                        RequestOutcome.Failure("HTTP/1.1 400 Bad Request")
                    }
                }
                LineReadResult.TooLong -> return RequestOutcome.Failure("HTTP/1.1 414 URI Too Long")
                LineReadResult.InvalidCharacters -> return RequestOutcome.Failure("HTTP/1.1 400 Bad Request")
            }

            val parts = requestLine.text.split(' ')
            if (parts.size != 3) {
                return RequestOutcome.Failure("HTTP/1.1 400 Bad Request")
            }
            val method = parts[0]
            val target = parts[1]
            val version = parts[2]
            if (version != "HTTP/1.1" && version != "HTTP/1.0") {
                return RequestOutcome.Failure("HTTP/1.1 400 Bad Request")
            }
            if (!target.startsWith("/")) {
                return RequestOutcome.Failure("HTTP/1.1 400 Bad Request")
            }
            if (method.isEmpty() || !method.all { it in 'A'..'Z' }) {
                return RequestOutcome.Failure("HTTP/1.1 400 Bad Request")
            }
            if (method != "GET") {
                return RequestOutcome.Failure("HTTP/1.1 405 Method Not Allowed", "Allow: GET\r\n")
            }

            var totalHeaderBytes = requestLine.byteCount
            var headerCount = 0
            while (true) {
                val remainingHeaderBudget = limits.maxTotalHeaderBytes - totalHeaderBytes
                if (remainingHeaderBudget <= 0) {
                    return RequestOutcome.Failure("HTTP/1.1 431 Request Header Fields Too Large")
                }
                val headerLineResult = readAsciiLine(boundedInput, remainingHeaderBudget)
                val headerLine = when (headerLineResult) {
                    is LineReadResult.Line -> headerLineResult
                    is LineReadResult.Eof -> return RequestOutcome.Failure("HTTP/1.1 400 Bad Request")
                    LineReadResult.TooLong -> return RequestOutcome.Failure("HTTP/1.1 431 Request Header Fields Too Large")
                    LineReadResult.InvalidCharacters -> return RequestOutcome.Failure("HTTP/1.1 400 Bad Request")
                }
                totalHeaderBytes += headerLine.byteCount
                if (totalHeaderBytes > limits.maxTotalHeaderBytes) {
                    return RequestOutcome.Failure("HTTP/1.1 431 Request Header Fields Too Large")
                }
                if (headerLine.text.isEmpty()) {
                    return RequestOutcome.Success(target)
                }
                headerCount += 1
                if (headerCount > limits.maxHeaderCount) {
                    return RequestOutcome.Failure("HTTP/1.1 431 Request Header Fields Too Large")
                }
                val colonIndex = headerLine.text.indexOf(':')
                if (colonIndex <= 0) {
                    return RequestOutcome.Failure("HTTP/1.1 400 Bad Request")
                }
                val headerName = headerLine.text.substring(0, colonIndex)
                if (!headerName.all { it > ' ' && it.code < 127 }) {
                    return RequestOutcome.Failure("HTTP/1.1 400 Bad Request")
                }
                val headerValue = headerLine.text.substring(colonIndex + 1).trim()
                if (headerName.equals("Transfer-Encoding", ignoreCase = true) && headerValue.isNotEmpty()) {
                    return RequestOutcome.Failure("HTTP/1.1 400 Bad Request")
                }
                if (headerName.equals("Content-Length", ignoreCase = true)) {
                    val length = headerValue.toLongOrNull() ?: return RequestOutcome.Failure("HTTP/1.1 400 Bad Request")
                    if (length != 0L) {
                        return RequestOutcome.Failure("HTTP/1.1 400 Bad Request")
                    }
                }
            }
        } catch (_: SocketTimeoutException) {
            return RequestOutcome.Failure("HTTP/1.1 408 Request Timeout")
        }
    }

    private fun readAsciiLine(input: InputStream, maxBytes: Int): LineReadResult {
        val out = ByteArrayOutputStream(minOf(maxBytes, 256))
        var count = 0
        var sawCr = false
        while (true) {
            val b = input.read()
            if (b == -1) {
                return LineReadResult.Eof(count)
            }
            count += 1
            if (count > maxBytes) {
                return LineReadResult.TooLong
            }
            if (b == '\n'.code) {
                val text = out.toString(Charsets.US_ASCII.name())
                return LineReadResult.Line(text, count)
            }
            if (sawCr) {
                return LineReadResult.InvalidCharacters
            }
            if (b == '\r'.code) {
                sawCr = true
                continue
            }
            if ((b < 0x20 && b != 0x09) || b == 0x7F) {
                return LineReadResult.InvalidCharacters
            }
            out.write(b)
        }
    }

    private class DeadlineBufferedInputStream(
        socket: Socket,
        raw: InputStream,
        socketReadTimeoutMs: Int,
        deadlineNanos: Long,
    ) : BufferedInputStream(
        DeadlineSocketInputStream(socket, raw, socketReadTimeoutMs, deadlineNanos),
        2048,
    )

    private class DeadlineSocketInputStream(
        private val socket: Socket,
        private val delegate: InputStream,
        private val socketReadTimeoutMs: Int,
        private val deadlineNanos: Long,
    ) : InputStream() {
        override fun read(): Int {
            val single = ByteArray(1)
            val n = read(single, 0, 1)
            return if (n == -1) -1 else single[0].toInt() and 0xFF
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            val remainingMs = TimeUnit.NANOSECONDS.toMillis(deadlineNanos - System.nanoTime())
            if (remainingMs <= 0L) {
                throw SocketTimeoutException("Request deadline exceeded")
            }
            val boundedTimeout = minOf(remainingMs, socketReadTimeoutMs.toLong()).toInt().coerceAtLeast(1)
            socket.soTimeout = boundedTimeout
            return delegate.read(b, off, len)
        }
    }

    private data class Route(val bucket: TileBucket)

    private fun routeFor(path: String): Route? {
        val parts = path.trimStart('/').split("/")
        if (parts.size != 4) return null
        if (parts[0] == DEM_DIR_NAME) return Route(TileBucket.DEM)
        return Route(TileBucket.BASEMAP)
    }

    private fun hasQuota(bucket: TileBucket): Boolean = synchronized(quotaLock) {
        if (baseHits + demHits >= globalCap.toLong()) return false
        return when (bucket) {
            TileBucket.BASEMAP -> baseHits < basemapQuota.toLong()
            TileBucket.DEM -> demHits < demQuota.toLong()
        }
    }

    private fun tryReserveQuota(bucket: TileBucket): Boolean = synchronized(quotaLock) {
        if (baseHits + demHits >= globalCap.toLong()) return false
        when (bucket) {
            TileBucket.BASEMAP -> {
                if (baseHits >= basemapQuota.toLong()) return false
                baseHits += 1L
            }
            TileBucket.DEM -> {
                if (demHits >= demQuota.toLong()) return false
                demHits += 1L
            }
        }
        return true
    }

    private fun releaseQuota(bucket: TileBucket) = synchronized(quotaLock) {
        when (bucket) {
            TileBucket.BASEMAP -> if (baseHits > 0L) baseHits -= 1L
            TileBucket.DEM -> if (demHits > 0L) demHits -= 1L
        }
    }

    private fun sendBoundedTile(
        output: OutputStream,
        bucket: TileBucket,
        bytes: ByteArray,
        mime: String,
    ) {
        if (bytes.size > limits.maxTileBytes) {
            writeStatus(output, "HTTP/1.1 413 Content Too Large")
            return
        }
        if (!tryReserveQuota(bucket)) {
            writeStatus(output, "HTTP/1.1 404 Not Found")
            return
        }
        var committed = false
        try {
            writeTile(output, bytes, mime)
            committed = true
        } finally {
            if (!committed) {
                releaseQuota(bucket)
            }
        }
    }

    private fun writeStatus(
        output: OutputStream,
        status: String,
        extraHeaders: String = "",
    ) {
        val head = "$status\r\n${extraHeaders}Content-Length: 0\r\nConnection: close\r\n\r\n"
        output.write(head.toByteArray(Charsets.US_ASCII))
        output.flush()
    }

    private fun writeTile(output: OutputStream, bytes: ByteArray, mime: String) {
        val head = "HTTP/1.1 200 OK\r\nContent-Type: $mime\r\n" +
            "Content-Length: ${bytes.size}\r\nConnection: close\r\n\r\n"
        output.write(head.toByteArray(Charsets.US_ASCII))
        output.write(bytes)
        output.flush()
    }

    private fun readBoundedFileBytes(file: File, maxBytes: Int): FileReadResult {
        return try {
            val declaredLength = file.length()
            if (declaredLength > maxBytes.toLong()) {
                return FileReadResult.TooLarge
            }
            val initialCapacity = declaredLength.toInt().coerceIn(0, 65_536)
            val buffer = ByteArray(8192)
            val out = ByteArrayOutputStream(initialCapacity)
            var total = 0
            file.inputStream().use { stream ->
                while (true) {
                    val maxRead = minOf(buffer.size, maxBytes - total + 1)
                    val read = stream.read(buffer, 0, maxRead)
                    if (read == -1) break
                    total += read
                    if (total > maxBytes) {
                        return FileReadResult.TooLarge
                    }
                    out.write(buffer, 0, read)
                }
            }
            FileReadResult.Success(out.toByteArray())
        } catch (_: EOFException) {
            FileReadResult.Unavailable
        } catch (_: Throwable) {
            FileReadResult.Unavailable
        }
    }

    private fun demStoreTile(path: String, store: DemTileStore): Pair<ByteArray, String>? {
        val parts = path.trimStart('/').split("/")
        if (parts.size != 4) return null
        val z = parts[1].toIntOrNull() ?: return null
        val x = parts[2].toIntOrNull() ?: return null
        val y = parts[3].removeSuffix(".png").toIntOrNull() ?: return null
        if (!isTileRef(parts[1]) || !isTileRef(parts[2])) return null
        val bytes = store.tileBytes(z, x, y) ?: return null
        val mime = if (store.tileFormat() == "webp") "image/webp" else "image/png"
        return bytes to mime
    }

    private fun isMbtilesPack(path: String): Boolean {
        val parts = path.trimStart('/').split("/")
        return parts.size == 4 && parts[0].endsWith(".mbtiles")
    }

    private fun mbtilesTile(path: String): MbtilesTile? {
        val store = mbtilesStore ?: return null
        val parts = path.trimStart('/').split("/")
        if (parts.size != 4 || !parts[0].endsWith(".mbtiles")) return null
        val rawY = parts[3]
        val tileExtension = MBTILES_TILE_EXTENSIONS.firstOrNull { rawY.endsWith(it) } ?: return null
        val z = parts[1].toIntOrNull() ?: return null
        val x = parts[2].toIntOrNull() ?: return null
        val y = rawY.removeSuffix(tileExtension).toIntOrNull() ?: return null
        if (z !in 0..28 || !isTileRef(parts[1]) || !isTileRef(parts[2])) return null
        return store.getTile(parts[0], z, x, y)
    }

    private fun fileFor(path: String, bucket: TileBucket): File? {
        val parts = path.trimStart('/').split("/")
        if (parts.size != 4) return null
        val root = packsDir().canonicalFile
        if (bucket == TileBucket.DEM) {
            val (_, z, x, y) = parts
            if (!y.endsWith(".png")) return null
            val tile = y.removeSuffix(".png")
            if (!isTileRef(z) || !isTileRef(x) || !isTileRef(tile)) return null
            val target = File(File(File(root, DEM_DIR_NAME), z), "$x/$tile.png").canonicalFile
            if (!target.path.startsWith(root.path + File.separator)) return null
            return target
        }
        val (packId, z, x, y) = parts
        if (!y.endsWith(".png")) return null
        if (!isSafePackId(packId)) return null
        val tile = y.removeSuffix(".png")
        if (!isTileRef(z) || !isTileRef(x) || !isTileRef(tile)) return null
        val target = File(File(root, packId), "$z/$x/$tile.png").canonicalFile
        if (!target.path.startsWith(root.path + File.separator)) return null
        return target
    }

    private fun isSafePackId(raw: String): Boolean {
        if (raw.isEmpty() || raw.startsWith(".") || raw.contains("..")) return false
        return raw.all { it.isLetterOrDigit() || it == '-' || it == '_' || it == '.' }
    }

    private fun isTileRef(raw: String): Boolean {
        if (raw.isEmpty() || raw.length > 7) return false
        return raw.all { it.isDigit() }
    }

    private fun closeQuietly(closeable: java.io.Closeable?) {
        try {
            closeable?.close()
        } catch (_: Throwable) {
            Unit
        }
    }
}
