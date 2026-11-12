package com.tanuki

import com.tanuki.runtime.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class RuntimeDiagnosticsTest {
    private val healthy = CommandResult(0, "LOCAL_OK", "")

    @Test fun initializationDoesNotNeedNetworkAndDoesNotClaimItWasChecked() = runBlocking {
        val states = mutableListOf<NetworkDiagnostic>()
        assertSame(healthy, runRuntimeDiagnostics(local = { healthy }, onNetwork = states::add))
        assertEquals(listOf(NetworkDiagnosticState.UNCHECKED), states.map { it.state })
    }

    @Test fun offlineNetworkFailurePreservesLocalSuccessAndVisibleError() = runBlocking {
        val states = mutableListOf<NetworkDiagnostic>()
        val result = runRuntimeDiagnostics(local = { healthy },
            network = { CommandResult(6, "", "Could not resolve host") }, onNetwork = states::add)
        assertSame(healthy, result)
        assertEquals(listOf(NetworkDiagnosticState.UNCHECKED, NetworkDiagnosticState.CHECKING, NetworkDiagnosticState.FAILED), states.map { it.state })
        assertTrue(states.last().detail.contains("exit=6"))
        assertTrue(states.last().detail.contains("Could not resolve host"))
    }

    @Test fun networkTimeoutIsReportedWithoutFailingLocalHealthOrLeakingTokens() = runBlocking {
        var outcome = NetworkDiagnostic()
        assertSame(healthy, runRuntimeDiagnostics(local = { healthy },
            network = { throw IOException("timeout token=synthetic-secret") }, onNetwork = { outcome = it }))
        assertEquals(NetworkDiagnosticState.FAILED, outcome.state)
        assertTrue(outcome.detail.contains("timeout"))
        assertFalse(outcome.detail.contains("synthetic-secret"))
    }

    @Test fun localFailureDoesNotRunNetworkOrMarkHealthSuccessful() = runBlocking {
        var requests = 0
        var outcome = NetworkDiagnostic(NetworkDiagnosticState.REACHABLE)
        try {
            runRuntimeDiagnostics(local = { CommandResult(17, "", "local broken") },
                network = { requests++; healthy }, onNetwork = { outcome = it })
            fail("local failure ignored")
        } catch (error: IllegalStateException) { assertTrue(error.message.orEmpty().contains("exit=17")) }
        assertEquals(0, requests)
        assertEquals(NetworkDiagnosticState.UNCHECKED, outcome.state)
    }

    @Test fun networkSuccessIsSeparateFromTheLocalReturnValue() = runBlocking {
        var outcome = NetworkDiagnostic()
        assertSame(healthy, runRuntimeDiagnostics(local = { healthy }, network = { CommandResult(0, "HTTPS_STATUS=200", "") },
            onNetwork = { outcome = it }))
        assertEquals(NetworkDiagnosticState.REACHABLE, outcome.state)
    }

    @Test fun userCancellationIsNeverConvertedToNetworkFailure() = runBlocking {
        val cancelled = CancellationException("synthetic user cancel")
        var outcome = NetworkDiagnostic()
        try {
            runRuntimeDiagnostics(local = { healthy }, network = { throw cancelled }, onNetwork = { outcome = it })
            fail("cancellation swallowed")
        } catch (error: CancellationException) { assertSame(cancelled, error) }
        assertEquals(NetworkDiagnosticState.UNCHECKED, outcome.state)
    }
}
