package com.tanuki

import com.tanuki.download.DownloadHttpException
import com.tanuki.download.Downloader
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.security.MessageDigest
import java.util.Base64
import java.util.concurrent.atomic.AtomicInteger

class DownloaderRetryTest {
    private val url = "https://registry.npmjs.org/pkg/-/pkg-1.0.0.tgz"
    private val payload = ByteArray(4096) { (it % 251).toByte() }
    private fun sri(bytes: ByteArray) = "sha512-" +
        Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-512").digest(bytes))

    private class Response(
        url: URL,
        private val status: Int,
        private val payload: ByteArray,
        private val etag: String? = null,
        private val contentRange: String? = null,
        private val dropAfter: Int? = null,
    ) : HttpURLConnection(url) {
        override fun connect() = Unit
        override fun disconnect() = Unit
        override fun usingProxy() = false
        override fun getResponseCode() = status
        override fun getContentLengthLong() = payload.size.toLong()
        override fun getHeaderField(name: String): String? = when (name) {
            "ETag" -> etag
            "Content-Range" -> contentRange
            else -> null
        }
        override fun getInputStream(): InputStream {
            if (dropAfter == null) return payload.inputStream()
            return object : InputStream() {
                private var position = 0
                override fun read(): Int = if (position < dropAfter) payload[position++].toInt() and 255
                    else throw IOException("synthetic connection drop")
                override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                    if (position >= dropAfter) throw IOException("synthetic connection drop")
                    val count = minOf(length, dropAfter - position)
                    System.arraycopy(payload, position, buffer, offset, count)
                    position += count
                    return count
                }
            }
        }
    }

    private fun fixture(block: (File) -> Unit) {
        val directory = Files.createTempDirectory("tanuki-download-retry").toFile()
        try { block(File(directory, "archive.tgz")) } finally { directory.deleteRecursively() }
    }

    private fun downloader(responses: List<Response>, delays: MutableList<Int>): Downloader {
        val next = AtomicInteger()
        return Downloader({ responses[next.getAndIncrement()] }, { _, _ -> }) { retry -> delays += retry }
    }

    @Test fun retryable429IsRetriedOnceBeforeSuccess() = fixture { target ->
        val delays = mutableListOf<Int>()
        val responses = listOf(
            Response(URL(url), 429, payload),
            Response(URL(url), 200, payload, etag = "\"ok\""),
        )
        runBlocking { downloader(responses, delays).fetchIntegrity(url, target, sri(payload), maxBytes = 100_000) }
        assertArrayEquals(payload, target.readBytes())
        assertEquals(listOf(1), delays)
        assertFalse(File(target.path + ".part").exists())
    }

    @Test fun twoServerErrorsUseTwoExponentialDelaysThenSucceed() = fixture { target ->
        val delays = mutableListOf<Int>()
        val responses = listOf(
            Response(URL(url), 503, payload),
            Response(URL(url), 502, payload),
            Response(URL(url), 200, payload),
        )
        runBlocking { downloader(responses, delays).fetchIntegrity(url, target, sri(payload), maxBytes = 100_000) }
        assertArrayEquals(payload, target.readBytes())
        assertEquals(listOf(1, 2), delays)
    }

    @Test fun missingArchiveIsNotRetried() = fixture { target ->
        listOf(404, 410).forEach { status ->
            val delays = mutableListOf<Int>()
            val next = AtomicInteger()
            val downloader = Downloader({ Response(URL(url), status, payload).also { next.incrementAndGet() } },
                { _, _ -> }) { retry -> delays += retry }
            try {
                runBlocking { downloader.fetchIntegrity(url, target, sri(payload), maxBytes = 100_000) }
                fail("HTTP $status accepted")
            } catch (error: DownloadHttpException) {
                assertEquals(status, error.status)
            }
            assertEquals(1, next.get())
            assertTrue(delays.isEmpty())
            assertFalse(target.exists())
        }
    }

    @Test fun exhaustedServerErrorsSurfaceAfterTwoRetries() = fixture { target ->
        val delays = mutableListOf<Int>()
        val responses = listOf(
            Response(URL(url), 429, payload),
            Response(URL(url), 429, payload),
            Response(URL(url), 429, payload),
        )
        try {
            runBlocking { downloader(responses, delays).fetchIntegrity(url, target, sri(payload), maxBytes = 100_000) }
            fail("exhausted retries accepted")
        } catch (error: DownloadHttpException) { assertEquals(429, error.status) }
        assertEquals(listOf(1, 2), delays)
    }

    @Test fun partialBytesSurviveRetryAndResumeWithRange() = fixture { target ->
        val delays = mutableListOf<Int>()
        val first = Response(URL(url), 200, payload, etag = "\"stable\"", dropAfter = 5)
        val second = Response(URL(url), 206, payload.copyOfRange(5, payload.size), etag = "\"stable\"",
            contentRange = "bytes 5-${payload.lastIndex}/${payload.size}")
        var secondRequest: Response? = null
        val next = AtomicInteger()
        val downloader = Downloader({
            if (next.getAndIncrement() == 0) first
            else second.also { secondRequest = it }
        }, { _, _ -> }) { retry -> delays += retry }
        runBlocking { downloader.fetchIntegrity(url, target, sri(payload), maxBytes = 100_000) }
        val request = requireNotNull(secondRequest) { "second request missing" }
        assertEquals("bytes=5-", request.getRequestProperty("Range"))
        assertEquals("\"stable\"", request.getRequestProperty("If-Range"))
        assertEquals(listOf(1), delays)
        assertArrayEquals(payload, target.readBytes())
    }
}
