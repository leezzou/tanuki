package com.tanuki

import com.tanuki.download.ContentRange
import com.tanuki.download.Downloader
import com.tanuki.download.parseContentRange
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.security.MessageDigest
import java.util.Base64
import java.util.concurrent.atomic.AtomicInteger

class DownloadRangeTest {
    private val url = "https://registry.npmmirror.com/pkg/-/pkg-1.0.0.tgz"
    private val payload = ByteArray(2048) { (it % 239).toByte() }
    private fun sri(bytes: ByteArray) = "sha512-" +
        Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-512").digest(bytes))

    private class Response(url: URL, private val status: Int, private val body: ByteArray,
                           private val range: String? = null) : HttpURLConnection(url) {
        override fun connect() = Unit
        override fun disconnect() = Unit
        override fun usingProxy() = false
        override fun getResponseCode() = status
        override fun getContentLengthLong() = body.size.toLong()
        override fun getHeaderField(name: String): String? = when (name) {
            "Content-Range" -> range
            "ETag" -> "\"range-test\""
            else -> null
        }
        override fun getInputStream(): InputStream = body.inputStream()
    }

    private fun fixture(block: (File) -> Unit) {
        val directory = Files.createTempDirectory("tanuki-download-range").toFile()
        try { block(File(directory, "archive.tgz")) } finally { directory.deleteRecursively() }
    }

    @Test fun parsesOnlyWellFormedContentRanges() {
        assertEquals(ContentRange(10, 19, 20), parseContentRange("bytes 10-19/20"))
        assertEquals(ContentRange(0, 0, null), parseContentRange("bytes 0-0/*"))
        assertNull(parseContentRange(null))
        assertNull(parseContentRange("items 0-9/10"))
        assertNull(parseContentRange("bytes 5-3/10"))
        assertNull(parseContentRange("bytes 0-9/9"))
        assertNull(parseContentRange("bytes 0-9"))
        assertNull(parseContentRange("bytes -1-9/10"))
    }

    @Test fun malformedPartialResponseRestartsFromZeroAndCommitsFullArchive() = fixture { target ->
        val requests = AtomicInteger()
        val downloader = Downloader({
            if (requests.getAndIncrement() == 0) Response(URL(url), 206, payload, "bytes 5-3/10")
            else Response(URL(url), 200, payload)
        }, { _, _ -> })
        runBlocking { downloader.fetchIntegrity(url, target, sri(payload), maxBytes = 100_000) }
        assertEquals(2, requests.get())
        assertArrayEquals(payload, target.readBytes())
    }
}
