package com.tanuki

import com.tanuki.runtime.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import org.junit.Assert.*
import org.junit.Test

class RuntimeHealthCheckTest {
    private val components = ComponentInventory(true, true, true, ubuntuVersion = "24.04.5")
    private val tools = RequiredToolsStatus(true, emptyList(), "24.04.5 synthetic", 1)
    private fun running() = Snapshot(environment = EnvironmentState.READY, service = ServiceState.RUNNING,
        health = HealthState.HEALTHY, webHealthy = false)

    @Test fun runningUnhealthyActionExecutesOnlyCurrentHttpProbeThenCanOpenChat() = runBlocking {
        val owner = Any()
        val state = MutableStateFlow(running())
        var probes = 0
        assertEquals(LocalPreparationAction.HEALTH, localPreparationAction(state.value, components, tools))
        executeRuntimeHealthCheck(running = true, checkRunning = {
            probeRunningService(owner, 1, { 1 }, { state.value.service == ServiceState.RUNNING },
                probe = { probes++; true }, publish = { healthy -> state.update { it.copy(webHealthy = healthy) } })
        }, checkStopped = { fail("running HEALTH attempted pnpm, migration or environment diagnostics") })
        assertEquals(1, probes)
        assertEquals(HealthState.HEALTHY, state.value.health)
        assertEquals(ServiceState.RUNNING, state.value.service)
        assertEquals(LocalPreparationAction.OPEN_CHAT, localPreparationAction(state.value, components, tools))
    }

    @Test fun failedHttpProbeUpdatesActualHealthAndOffersExecutableRecheckWithoutStopping() = runBlocking {
        val state = MutableStateFlow(running().copy(webHealthy = true))
        var probes = 0
        executeRuntimeHealthCheck(true, {
            probeRunningService(Any(), 1, { 1 }, { true }, { probes++; false }, { healthy -> state.update { it.copy(webHealthy = healthy) } })
        }, { fail("must not execute guest preparation for a live service") })
        assertEquals(1, probes)
        assertFalse(state.value.webHealthy)
        assertEquals(ServiceState.RUNNING, state.value.service)
        assertEquals(HealthState.HEALTHY, state.value.health)
        assertEquals(LocalPreparationAction.HEALTH, localPreparationAction(state.value, components, tools))
    }

    @Test fun stopOrReplacementDuringHttpWaitCannotPublishOldHealthyResult() = runBlocking {
        val owner = Any()
        var generation = 1L
        var currentInstance = true
        val state = MutableStateFlow(running())
        probeRunningService(owner, 1, { generation }, { currentInstance }, probe = {
            synchronized(owner) {
                generation++
                currentInstance = false
                state.update { it.copy(service = ServiceState.STOPPED) }
            }
            true
        }, publish = { healthy -> state.update { it.copy(webHealthy = healthy) } })
        assertEquals(ServiceState.STOPPED, state.value.service)
        assertFalse(state.value.webHealthy)
        assertEquals(LocalPreparationAction.START, localPreparationAction(state.value, components, tools))
    }

    @Test fun staleInstanceCannotEvenBeginTheProbeAndStoppedCheckKeepsItsOwnBackend() = runBlocking {
        var localChecks = 0
        probeRunningService(Any(), 1, { 2 }, { false }, { fail("stale request probed a replacement"); true }, { fail("stale result published") })
        executeRuntimeHealthCheck(false, { fail("stopped environment used live HTTP branch") }, { localChecks++ })
        assertEquals(1, localChecks)
    }
}
