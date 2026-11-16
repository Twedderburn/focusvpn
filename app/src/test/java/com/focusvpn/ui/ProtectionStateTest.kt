package com.focusvpn.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class ProtectionStateTest {
    @Test fun noConsentWinsOverEverything() {
        for (running in listOf(false, true)) for (bypass in listOf(false, true)) {
            assertEquals(Protection.NEEDS_OK, protectionState(hasConsent = false, running = running, privateDnsBypass = bypass))
        }
    }

    @Test fun privateDnsBypassBeatsRunning() {
        assertEquals(Protection.PRIVATE_DNS, protectionState(hasConsent = true, running = true, privateDnsBypass = true))
        assertEquals(Protection.PRIVATE_DNS, protectionState(hasConsent = true, running = false, privateDnsBypass = true))
    }

    @Test fun startingUntilServiceRuns() {
        assertEquals(Protection.STARTING, protectionState(hasConsent = true, running = false, privateDnsBypass = false))
    }

    @Test fun onlyOnWhenEverythingIsFine() {
        assertEquals(Protection.ON, protectionState(hasConsent = true, running = true, privateDnsBypass = false))
    }
}
