package com.focusvpn

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RemovalGuardTest {
    @Test fun setupAsksAgainOnceBeforeAllowingSkip() {
        assertFalse(RemovalGuard.canSkipInSetup(0))
        assertFalse(RemovalGuard.canSkipInSetup(1))
        assertTrue(RemovalGuard.canSkipInSetup(2))
    }

    @Test fun alertsWhenSwitchedOffAfterBeingOn() {
        assertTrue(RemovalGuard.needsAlert(setupDone = true, wasOn = true, blocked = false))
    }

    @Test fun leavesPhonesAloneWhereItWasNeverOn() {
        assertFalse(RemovalGuard.needsAlert(setupDone = true, wasOn = false, blocked = false))
    }

    @Test fun noAlertWhileOnOrBeforeSetupIsDone() {
        assertFalse(RemovalGuard.needsAlert(setupDone = true, wasOn = true, blocked = true))
        assertFalse(RemovalGuard.needsAlert(setupDone = false, wasOn = true, blocked = false))
    }
}
