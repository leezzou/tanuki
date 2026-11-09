package com.tanuki

import com.tanuki.runtime.NpmLockTrust
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.security.MessageDigest
import java.util.Base64

class NpmLockTrustTest {
    private fun sri(seed: Int) = "sha512-" + Base64.getEncoder().encodeToString(
        MessageDigest.getInstance("SHA-512").digest(ByteArray(64) { (it + seed).toByte() }))

    private val entrySri = sri(1)
    private val dependencySri = sri(2)
    private val alteredSri = sri(3)
    private val entryPath = "/@deepseek-ai/dsh/-/dsh-0.2.1-rc.5.tgz"
    private val depPath = "/dep/-/dep-1.0.0.tgz"

    private fun lock(
        registry: String,
        entryIntegrity: String = entrySri,
        dependencyIntegrity: String = dependencySri,
        dependencyVersion: String = "1.0.0",
        dependencyPath: String = depPath,
        includeDependency: Boolean = true,
        extraDependency: Boolean = false,
    ): ByteArray {
        val packages = JSONObject()
            .put("", JSONObject().put("dependencies", JSONObject()
                .put("@deepseek-ai/dsh", "0.2.1-rc.5")))
            .put("node_modules/@deepseek-ai/dsh", JSONObject()
                .put("version", "0.2.1-rc.5")
                .put("resolved", registry + entryPath)
                .put("integrity", entryIntegrity))
        if (includeDependency) {
            packages.put("node_modules/dep", JSONObject()
                .put("version", dependencyVersion)
                .put("resolved", registry + dependencyPath)
                .put("integrity", dependencyIntegrity))
        }
        if (extraDependency) {
            packages.put("node_modules/extra", JSONObject()
                .put("version", "1.0.0")
                .put("resolved", registry + "/extra/-/extra-1.0.0.tgz")
                .put("integrity", dependencySri))
        }
        return JSONObject()
            .put("lockfileVersion", 3)
            .put("packages", packages)
            .toString().toByteArray(Charsets.UTF_8)
    }

    private fun rejects(message: String, block: () -> Unit) {
        try {
            block()
            fail(message)
        } catch (error: IllegalArgumentException) {
            assertTrue(error.message.orEmpty().contains("不一致") || error.message.orEmpty().contains("SHA-512"))
        }
    }

    @Test fun officialAndMirrorLocksWithSameArchivesAndSrisAreAccepted() {
        val trusted = lock("https://registry.npmjs.org")
        val candidate = lock("https://registry.npmmirror.com")
        NpmLockTrust.requireSameArchives(trusted, candidate)
        // 归一化前后的入口 SRI 必须保持不变。
        assertEquals(entrySri, JSONObject(String(candidate, Charsets.UTF_8))
            .getJSONObject("packages").getJSONObject("node_modules/@deepseek-ai/dsh").getString("integrity"))
    }

    @Test fun transitiveSriTamperedByMirrorIsRejected() {
        val trusted = lock("https://registry.npmjs.org")
        val candidate = lock("https://registry.npmmirror.com", dependencyIntegrity = alteredSri)
        rejects("tampered transitive SRI accepted") { NpmLockTrust.requireSameArchives(trusted, candidate) }
    }

    @Test fun entrySriTamperedByMirrorIsRejectedEvenWhenTransitiveSrisMatch() {
        val trusted = lock("https://registry.npmjs.org")
        val candidate = lock("https://registry.npmmirror.com", entryIntegrity = alteredSri)
        rejects("tampered entry SRI accepted") { NpmLockTrust.requireSameArchives(trusted, candidate) }
    }

    @Test fun transitiveVersionOrOfficialPathTamperedByMirrorIsRejected() {
        val trusted = lock("https://registry.npmjs.org")
        val candidate = lock("https://registry.npmmirror.com", dependencyVersion = "2.0.0",
            dependencyPath = "/dep/-/dep-2.0.0.tgz")
        rejects("tampered transitive path accepted") { NpmLockTrust.requireSameArchives(trusted, candidate) }
    }

    @Test fun missingOrExtraTransitivePackagesAreRejected() {
        val trusted = lock("https://registry.npmjs.org")
        rejects("missing transitive package accepted") {
            NpmLockTrust.requireSameArchives(trusted,
                lock("https://registry.npmmirror.com", includeDependency = false))
        }
        rejects("extra transitive package accepted") {
            NpmLockTrust.requireSameArchives(trusted,
                lock("https://registry.npmmirror.com", extraDependency = true))
        }
    }

    @Test fun nonSha512IntegrityIsRejected() {
        val trusted = lock("https://registry.npmjs.org")
        val candidate = lock("https://registry.npmmirror.com", dependencyIntegrity = "sha1-unsafe")
        rejects("non-SHA-512 integrity accepted") { NpmLockTrust.requireSameArchives(trusted, candidate) }
    }
}
