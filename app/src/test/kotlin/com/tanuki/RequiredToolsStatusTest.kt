package com.tanuki

import com.tanuki.runtime.RequiredToolsStatus
import org.junit.Assert.*
import org.junit.Test

class RequiredToolsStatusTest {
    @Test fun samePreparedUbuntuCanReuseSuccessfulCheck() {
        val checked = RequiredToolsStatus(true, emptyList(), "synthetic-ubuntu-a", 123)
        assertTrue(checked.passedFor("synthetic-ubuntu-a"))
    }

    @Test fun changedOrUnknownUbuntuAndIncompleteChecksMustNotSkipPreparation() {
        val checked = RequiredToolsStatus(true, emptyList(), "synthetic-ubuntu-a", 123)
        assertFalse(checked.passedFor("synthetic-ubuntu-b"))
        assertFalse(checked.passedFor(""))
        assertFalse(checked.copy(checked = false).passedFor("synthetic-ubuntu-a"))
        assertFalse(checked.copy(missing = listOf("libatomic1")).passedFor("synthetic-ubuntu-a"))
        assertFalse(checked.copy(ubuntuMarker = "").passedFor(""))
    }
}
