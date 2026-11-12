package com.tanuki

import com.tanuki.runtime.*
import org.junit.Test
import org.junit.Assert.*
import org.json.JSONObject

class LocalPreparationTest {
    private fun metadata(version: String) = JSONObject().put("ready", true).put("ubuntu", "24.04.5").put("node", "24.22.2").put("dsh", version)

    @Test fun newInstallUsesPinnedDefaultAndCanRepairMissingDefaultProgram() {
        assertEquals("0.2.1-rc.5", initializationDshVersion(null, emptySet(), "0.2.1-rc.5"))
        assertEquals("0.2.1-rc.5", initializationDshVersion(metadata("0.2.1-rc.5"), emptySet(), "0.2.1-rc.5"))
    }

    @Test fun existingNonDefaultVersionIsPreservedWhenOnlyOtherComponentsNeedPreparation() {
        assertEquals("0.3.0", initializationDshVersion(metadata("0.3.0"), setOf("0.3.0", "0.2.1-rc.5"), "0.2.1-rc.5"))
    }

    @Test fun missingSelectedProgramCannotFallBackToAnotherInstalledVersion() {
        try {
            initializationDshVersion(metadata("0.3.0"), setOf("0.2.1-rc.5"), "0.2.1-rc.5")
            fail("missing selected version silently replaced")
        } catch (error: IllegalStateException) {
            assertTrue(error.message.orEmpty().contains("0.3.0"))
            assertTrue(error.message.orEmpty().contains("不会改装默认版本"))
        }
    }

    @Test fun incompleteMetadataAndInvalidVersionRemainRejected() {
        for (invalid in listOf(metadata("0.3.0").put("ready", false), metadata("0.3.0").apply { remove("node") }, metadata("../outside"))) {
            try { initializationDshVersion(invalid, setOf("0.3.0"), "0.2.1-rc.5"); fail("invalid metadata accepted") }
            catch (_: IllegalStateException) { }
        }
    }
    private val components = ComponentInventory(true, true, true, ubuntuVersion = "24.04.5")
    private val tools = RequiredToolsStatus(true, emptyList(), "24.04.5 synthetic-hash", 123)
    private val ready = Snapshot(environment = EnvironmentState.READY)

    @Test fun ubuntuReadyAloneCannotStartOrOpenChat() {
        assertEquals(LocalPreparationAction.INITIALIZE,
            localPreparationAction(ready, ComponentInventory(ubuntuInstalled = true), tools))
    }

    @Test fun completeProgramsWithoutMatchingToolsRequireToolPreparation() {
        assertEquals(LocalPreparationAction.TOOLS, localPreparationAction(ready, components, RequiredToolsStatus()))
        assertEquals(LocalPreparationAction.TOOLS, localPreparationAction(ready, components, tools.copy(ubuntuMarker = "22.04.5 old")))
        assertEquals(LocalPreparationAction.TOOLS, localPreparationAction(ready, components, tools.copy(missing = listOf("git"))))
    }

    @Test fun installedProgramsWithoutReadyMetadataCannotSkipPreparation() {
        assertEquals(LocalPreparationAction.INITIALIZE,
            localPreparationAction(ready.copy(environment = EnvironmentState.REPAIR), components, tools))
    }

    @Test fun currentOperationWinsOverHistoricalFailure() {
        assertEquals(LocalPreparationAction.BUSY,
            localPreparationAction(ready.copy(service = ServiceState.STARTING, error = "synthetic previous failure"), components, tools))
        assertEquals(LocalPreparationAction.BUSY,
            localPreparationAction(ready.copy(operation = "runtime-recovery"), components, tools))
    }

    @Test fun recoveryJournalCannotBeBypassedByReadyComponents() {
        assertEquals(LocalPreparationAction.RECOVERY,
            localPreparationAction(ready.copy(update = UpdateState.RECOVERY_REQUIRED), components, tools))
    }

    @Test fun fullyInstalledStoppedRuntimeStartsWithoutReinstallationOrNetworkCheck() {
        assertEquals(LocalPreparationAction.START, localPreparationAction(ready, components, tools))
    }

    @Test fun navigationNeedsRunningAndActualLocalHttpHealth() {
        assertEquals(LocalPreparationAction.HEALTH,
            localPreparationAction(ready.copy(service = ServiceState.RUNNING), components, tools))
        assertEquals(LocalPreparationAction.OPEN_CHAT,
            localPreparationAction(ready.copy(service = ServiceState.RUNNING, webHealthy = true), components, tools))
    }
}
