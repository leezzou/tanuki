package com.tanuki

import com.tanuki.runtime.NpmArchiveMapping
import com.tanuki.runtime.NpmArchiveSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class NpmArchiveMappingTest {
    private val path = "/@deepseek-ai/dsh/-/dsh-0.2.1-rc.5.tgz"
    private val official = "https://registry.npmjs.org$path"

    @Test fun officialTarballMapsOnlyItsPathToWhitelistedMirrors() {
        NpmArchiveMapping.MIRROR_REGISTRIES.forEach { registry ->
            assertEquals(registry + path, NpmArchiveMapping.urlFor(registry, official))
        }
        assertEquals(official, NpmArchiveMapping.urlFor(NpmArchiveMapping.OFFICIAL_REGISTRY, official))
    }

    @Test fun mirrorLockUrlsNormalizeBackToOfficialWithoutChangingThePath() {
        NpmArchiveMapping.MIRROR_REGISTRIES.forEach { registry ->
            assertEquals(official, NpmArchiveMapping.toOfficial(registry + path))
        }
        assertEquals(official, NpmArchiveMapping.toOfficial(official))
    }

    @Test fun arbitraryHostsPortsAndUrlPartsAreRejected() {
        listOf(
            "http://registry.npmjs.org$path",
            "https://evil.invalid$path",
            "https://registry.npmjs.org:8443$path",
            "https://" + "user" + "@" + "registry.npmjs.org$path",
            "https://registry.npmjs.org$path?download=1",
            "https://registry.npmjs.org$path#fragment",
            "https://registry.npmjs.org/pkg/-/pkg-1.0.0.zip",
            "https://registry.npmjs.org/$path",
        ).forEach { candidate ->
            try {
                NpmArchiveMapping.officialPath(candidate)
                fail("unsafe official archive accepted: $candidate")
            } catch (_: IllegalArgumentException) { }
        }
        try {
            NpmArchiveMapping.urlFor("https://evil.invalid", official)
            fail("arbitrary mirror host accepted")
        } catch (_: IllegalArgumentException) { }
    }

    @Test fun traversalEncodingBackslashAndControlCharactersAreRejected() {
        listOf(
            "https://registry.npmjs.org/a/../b/-/b-1.0.0.tgz",
            "https://registry.npmjs.org/a/%2e%2e/b/-/b-1.0.0.tgz",
            "https://registry.npmjs.org/a//b/-/b-1.0.0.tgz",
            "https://registry.npmjs.org/a\\b/-/b-1.0.0.tgz",
            "https://registry.npmjs.org/a/\u0000b/-/b-1.0.0.tgz",
            "https://registry.npmjs.org/a/-/b 1.0.0.tgz",
        ).forEach { candidate ->
            try {
                NpmArchiveMapping.officialPath(candidate)
                fail("unsafe archive path accepted: $candidate")
            } catch (_: IllegalArgumentException) { }
        }
        try {
            NpmArchiveMapping.toOfficial("https://evil.invalid$path")
            fail("arbitrary lock host accepted")
        } catch (_: IllegalArgumentException) { }
        try {
            NpmArchiveMapping.toOfficial("http://registry.npmmirror.com$path")
            fail("plain HTTP mirror accepted")
        } catch (_: IllegalArgumentException) { }
    }

    @Test fun knownRegistryCheckIsLimitedToShippedMirrors() {
        assertTrue(NpmArchiveMapping.isKnownRegistry("https://registry.npmjs.org"))
        NpmArchiveMapping.MIRROR_REGISTRIES.forEach { assertTrue(NpmArchiveMapping.isKnownRegistry(it)) }
        assertFalse(NpmArchiveMapping.isKnownRegistry("https://registry.npmjs.org.evil"))
        assertFalse(NpmArchiveMapping.isKnownRegistry("https://registry.npmjs.org/path"))
        assertTrue(NpmArchiveMapping.isKnownRegistry("https://registry.npmjs.org/"))
        assertFalse(NpmArchiveMapping.isKnownRegistry("http://registry.npmjs.org"))
    }

    @Test fun sourceExposesItsHostForPerHostLimiting() {
        val source = NpmArchiveSource("npmmirror", "npmmirror", "https://registry.npmmirror.com")
        assertEquals("registry.npmmirror.com", source.host)
        assertEquals("https://registry.npmmirror.com$path", source.urlFor(official))
    }
}
