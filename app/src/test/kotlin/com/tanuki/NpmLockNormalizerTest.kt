package com.tanuki

import com.tanuki.runtime.NpmArchiveMapping
import com.tanuki.runtime.NpmLockNormalizer
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import java.security.MessageDigest
import java.util.Base64

class NpmLockNormalizerTest {
    private val sri = "sha512-" + Base64.getEncoder().encodeToString(
        MessageDigest.getInstance("SHA-512").digest("locked package".toByteArray()))
    private val path = "/pkg/-/pkg-1.0.0.tgz"

    private fun lock(resolved: String): ByteArray = JSONObject()
        .put("name", "tanuki-dsh-runtime")
        .put("lockfileVersion", 3)
        .put("packages", JSONObject()
            .put("", JSONObject().put("dependencies", JSONObject().put("pkg", "1.0.0")))
            .put("node_modules/pkg", JSONObject()
                .put("version", "1.0.0")
                .put("resolved", resolved)
                .put("integrity", sri)))
        .toString().toByteArray(Charsets.UTF_8)

    @Test fun mirrorResolvedUrlsBecomeOfficialWhileIntegrityAndVersionStayExact() {
        NpmArchiveMapping.MIRROR_REGISTRIES.forEach { registry ->
            val normalized = JSONObject(String(NpmLockNormalizer.normalize(lock(registry + path)), Charsets.UTF_8))
            val entry = normalized.getJSONObject("packages").getJSONObject("node_modules/pkg")
            assertEquals(NpmArchiveMapping.OFFICIAL_REGISTRY + path, entry.getString("resolved"))
            assertEquals(sri, entry.getString("integrity"))
            assertEquals("1.0.0", entry.getString("version"))
        }
    }

    @Test fun alreadyOfficialLockBytesAreReturnedUnchanged() {
        val original = lock(NpmArchiveMapping.OFFICIAL_REGISTRY + path)
        assertArrayEquals(original, NpmLockNormalizer.normalize(original))
    }

    @Test fun unknownHostsUnsafePathsAndNonHttpsAreRejected() {
        listOf(
            "https://evil.invalid$path",
            "http://registry.npmmirror.com$path",
            "https://registry.npmmirror.com/pkg/-/pkg-1.0.0.tgz?download=1",
            "https://registry.npmmirror.com/pkg/../pkg/-/pkg-1.0.0.tgz",
            "https://registry.npmmirror.com/pkg/-/pkg-1.0.0.zip",
        ).forEach { resolved ->
            try {
                NpmLockNormalizer.normalize(lock(resolved))
                fail("unsafe lock URL accepted: $resolved")
            } catch (_: IllegalArgumentException) { }
        }
    }
}
