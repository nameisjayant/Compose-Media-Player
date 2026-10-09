package com.nameisjayant.composevideos.media.videos.data

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.AssetFileDescriptor
import android.net.ConnectivityManager
import android.net.Uri
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.BufferedOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.Inet4Address
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A tiny HTTP server that hands the bundled videos (and their thumbnails) to a Cast device.
 *
 * The videos live in `res/raw`, which only this app can read, but a TV fetches what it plays
 * itself, by URL. So while casting, the phone serves them over the Wi-Fi network the TV is on:
 * `http://<phone>:<port>/video/<id>.mp4`, with byte ranges so the TV can seek. It serves the
 * bundled files and nothing else, and only runs from the first cast until the session ends.
 */
@Singleton
class CastMediaServer @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private var serverSocket: ServerSocket? = null
    private var executor: ExecutorService? = null

    /**
     * Where the TV can fetch [video], starting the server if it isn't running; null when the phone
     * has no local network address (no Wi-Fi), so there's nowhere for the TV to reach it.
     */
    @Synchronized
    fun videoUri(video: Video): Uri? = uri("video/${video.id}.mp4")

    /** Where the TV can fetch [video]'s thumbnail, to show while it loads and on the Cast notification. */
    @Synchronized
    fun thumbnailUri(video: Video): Uri? = uri("thumbnail/${video.id}.jpg")

    @Synchronized
    fun stop() {
        // Closing the socket ends the accept loop; open transfers end when the TV hangs up.
        serverSocket?.close()
        serverSocket = null
        executor?.shutdown()
        executor = null
    }

    private fun uri(path: String): Uri? {
        val host = localAddress() ?: return null
        val port = start().localPort
        return Uri.parse("http://${host.hostAddress}:$port/$path")
    }

    private fun start(): ServerSocket {
        serverSocket?.takeUnless { it.isClosed }?.let { return it }
        val socket = ServerSocket(0)
        val pool = Executors.newCachedThreadPool { runnable ->
            Thread(runnable, "CastMediaServer").apply { isDaemon = true }
        }
        pool.execute {
            while (!socket.isClosed) {
                val client = try {
                    socket.accept()
                } catch (e: SocketException) {
                    break // Stopped.
                }
                // The TV opens several connections at once: one to play and others to probe or seek.
                try {
                    pool.execute { client.use(::serve) }
                } catch (e: Exception) {
                    client.close()
                }
            }
        }
        serverSocket = socket
        executor = pool
        return socket
    }

    /** The phone's IPv4 address on the network it's connected to, which the TV is on too. */
    private fun localAddress(): InetAddress? {
        val connectivity = ContextCompat.getSystemService(context, ConnectivityManager::class.java) ?: return null
        val links = connectivity.getLinkProperties(connectivity.activeNetwork) ?: return null
        return links.linkAddresses
            .map { it.address }
            .firstOrNull { it is Inet4Address && !it.isLoopbackAddress }
    }

    private fun serve(socket: Socket) {
        try {
            val request = HttpRequest.read(socket.getInputStream()) ?: return
            val out = BufferedOutputStream(socket.getOutputStream())
            when (request.method) {
                // A web player checks it may ask for byte ranges before it does.
                "OPTIONS" -> out.writeHead(204, "No Content", emptyMap())
                "GET", "HEAD" -> {
                    val file = open(request.path)
                    if (file == null) out.writeHead(404, "Not Found", mapOf("Content-Length" to "0"))
                    else file.use { sendFile(out, it, request) }
                }
                else -> out.writeHead(405, "Method Not Allowed", mapOf("Content-Length" to "0"))
            }
            out.flush()
        } catch (e: IOException) {
            // The TV hung up mid-transfer, e.g. after seeking; it asks again for what it needs.
        }
    }

    // Thumbnails are drawable JPEGs, stored uncompressed, so they open as raw bytes just fine.
    @SuppressLint("ResourceType")
    private fun open(path: String): CastFile? {
        val match = PATH.matchEntire(path) ?: return null
        val (kind, id) = match.destructured
        val video = BundledVideos.all.firstOrNull { it.id == id } ?: return null
        return when (kind) {
            "video" -> CastFile(context.resources.openRawResourceFd(video.videoRes), "video/mp4")
            else -> CastFile(context.resources.openRawResourceFd(video.thumbnailRes), "image/jpeg")
        }
    }

    private fun sendFile(out: OutputStream, file: CastFile, request: HttpRequest) {
        val length = file.descriptor.length
        val range = request.range?.let { parseRange(it, length) }
        if (request.range != null && range == null) {
            out.writeHead(416, "Range Not Satisfiable", mapOf("Content-Range" to "bytes */$length", "Content-Length" to "0"))
            return
        }
        val start = range?.first ?: 0L
        val end = range?.last ?: (length - 1)
        val headers = buildMap {
            put("Content-Type", file.mimeType)
            put("Content-Length", (end - start + 1).toString())
            put("Accept-Ranges", "bytes")
            if (range != null) put("Content-Range", "bytes $start-$end/$length")
        }
        if (range != null) out.writeHead(206, "Partial Content", headers) else out.writeHead(200, "OK", headers)
        if (request.method == "HEAD") return
        file.descriptor.createInputStream().use { input ->
            input.skipFully(start)
            input.copyTo(out, end - start + 1)
        }
    }

    private class CastFile(val descriptor: AssetFileDescriptor, val mimeType: String) : AutoCloseable {
        override fun close() = descriptor.close()
    }

    private companion object {
        val PATH = Regex("/(video|thumbnail)/([a-z0-9_-]+)\\.(?:mp4|jpg)")
    }
}

/** The request line and the one header that matters here. */
internal class HttpRequest(val method: String, val path: String, val range: String?) {
    companion object {
        fun read(input: InputStream): HttpRequest? {
            val requestLine = input.readLine() ?: return null
            val parts = requestLine.split(' ')
            if (parts.size < 2) return null
            var range: String? = null
            while (true) {
                val line = input.readLine() ?: return null
                if (line.isEmpty()) break
                val colon = line.indexOf(':')
                if (colon > 0 && line.substring(0, colon).trim().equals("Range", ignoreCase = true)) {
                    range = line.substring(colon + 1).trim()
                }
            }
            return HttpRequest(parts[0].uppercase(), parts[1].substringBefore('?'), range)
        }

        /** One CRLF- (or LF-) terminated header line, without reading past it into the body. */
        private fun InputStream.readLine(): String? {
            val line = StringBuilder()
            while (true) {
                val byte = read()
                if (byte == -1) return if (line.isEmpty()) null else line.toString()
                if (byte == '\n'.code) return line.toString().removeSuffix("\r")
                if (line.length > 8_192) throw IOException("Header line too long")
                line.append(byte.toChar())
            }
        }
    }
}

/**
 * The bytes a single `Range: bytes=…` header asks for out of [length], clamped to the file, or
 * null when it can't be met. Covers `a-b`, open-ended `a-` and suffix `-n`; a list of ranges
 * isn't something a video element asks for.
 */
internal fun parseRange(header: String, length: Long): LongRange? {
    val spec = header.removePrefix("bytes=").takeIf { it != header && ',' !in it }?.trim() ?: return null
    val dash = spec.indexOf('-')
    if (dash < 0 || length <= 0) return null
    val from = spec.substring(0, dash).trim()
    val to = spec.substring(dash + 1).trim()
    return when {
        from.isEmpty() -> {
            val suffix = to.toLongOrNull()?.takeIf { it > 0 } ?: return null
            (length - suffix).coerceAtLeast(0)..<length
        }
        else -> {
            val start = from.toLongOrNull()?.takeIf { it in 0..<length } ?: return null
            val end = if (to.isEmpty()) length - 1 else to.toLongOrNull()?.takeIf { it >= start } ?: return null
            start..end.coerceAtMost(length - 1)
        }
    }
}

private fun OutputStream.writeHead(code: Int, reason: String, headers: Map<String, String>) {
    val head = buildString {
        append("HTTP/1.1 $code $reason\r\n")
        headers.forEach { (name, value) -> append("$name: $value\r\n") }
        // The receiver is a web page on another origin.
        append("Access-Control-Allow-Origin: *\r\n")
        append("Access-Control-Allow-Headers: Range\r\n")
        append("Access-Control-Allow-Methods: GET, HEAD, OPTIONS\r\n")
        append("Access-Control-Expose-Headers: Content-Length, Content-Range, Accept-Ranges\r\n")
        append("Connection: close\r\n\r\n")
    }
    write(head.toByteArray(Charsets.ISO_8859_1))
}

private fun InputStream.skipFully(count: Long) {
    var left = count
    while (left > 0) {
        val skipped = skip(left)
        if (skipped <= 0) {
            if (read() == -1) throw IOException("Unexpected end of file")
            left--
        } else {
            left -= skipped
        }
    }
}

private fun InputStream.copyTo(out: OutputStream, count: Long) {
    val buffer = ByteArray(64 * 1024)
    var left = count
    while (left > 0) {
        val read = read(buffer, 0, minOf(buffer.size.toLong(), left).toInt())
        if (read == -1) break
        out.write(buffer, 0, read)
        left -= read
    }
}
