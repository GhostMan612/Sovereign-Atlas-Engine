// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.offline

import java.io.File
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

class PackTileServer(
    private val packsDir: () -> File,
    private val port: Int = 0,
) {
    private var socket: ServerSocket? = null
    private val running = AtomicBoolean(false)

    fun port(): Int = socket?.localPort ?: -1

    fun isRunning(): Boolean = running.get()

    fun start(): Int {
        if (running.get()) return port()
        val server = ServerSocket(port, 4)
        socket = server
        running.set(true)
        thread(isDaemon = true, name = "atlas-tiles") {
            while (running.get()) {
                try {
                    val client = server.accept()
                    thread(isDaemon = true) { serve(client) }
                } catch (error: Throwable) {
                    if (running.get()) continue else break
                }
            }
        }
        return server.localPort
    }

    fun stop() {
        running.set(false)
        try {
            socket?.close()
        } catch (error: Throwable) {
            Unit
        }
        socket = null
    }

    fun tileUrl(packId: String): String? {
        val bound = port()
        if (bound <= 0) return null
        return "http://127.0.0.1:$bound/$packId/{z}/{x}/{y}.png"
    }

    private fun serve(client: Socket) {
        try {
            client.use { socket ->
                val input = socket.getInputStream().bufferedReader()
                val output = socket.getOutputStream()
                val request = input.readLine() ?: return
                val path = request.split(" ").getOrNull(1) ?: ""
                drainHeaders(input)
                val body = fileFor(path)
                if (body == null || !body.isFile) {
                    val head = "HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"
                    output.write(head.toByteArray(Charsets.US_ASCII))
                    return
                }
                val bytes = body.readBytes()
                val head = "HTTP/1.1 200 OK\r\nContent-Type: image/png\r\n" +
                    "Content-Length: ${bytes.size}\r\nConnection: close\r\n\r\n"
                output.write(head.toByteArray(Charsets.US_ASCII))
                output.write(bytes)
            }
        } catch (error: Throwable) {
            Unit
        }
    }

    private fun drainHeaders(input: java.io.BufferedReader) {
        while (true) {
            val line = input.readLine() ?: return
            if (line.isEmpty()) return
        }
    }

    private fun fileFor(path: String): File? {
        val parts = path.trimStart('/').split("/")
        if (parts.size != 4) return null
        val (packId, z, x, y) = parts
        if (!y.endsWith(".png")) return null
        if (packId.isEmpty() || packId.contains("..")) return null
        val tile = y.removeSuffix(".png")
        if (!isTileRef(z) || !isTileRef(x) || !isTileRef(tile)) return null
        return File(File(packsDir(), packId), "$z/$x/$tile.png")
    }

    private fun isTileRef(raw: String): Boolean {
        if (raw.isEmpty() || raw.length > 7) return false
        return raw.all { it.isDigit() }
    }
}
