package com.focusvpn

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotifyAskTest {
    @Test fun asksWhenFilterIsOkdAndNotificationsAreOff() {
        assertTrue(NotifyAsk.shouldAsk(hasConsent = true, canNotify = false, askedThisLaunch = false))
    }

    @Test fun neverBeforeAndroidHasGivenItsOk() {
        assertFalse(NotifyAsk.shouldAsk(hasConsent = false, canNotify = false, askedThisLaunch = false))
    }

    @Test fun notWhenNotificationsAreAlreadyAllowed() {
        assertFalse(NotifyAsk.shouldAsk(hasConsent = true, canNotify = true, askedThisLaunch = false))
    }

    @Test fun onlyOncePerLaunch() {
        assertFalse(NotifyAsk.shouldAsk(hasConsent = true, canNotify = false, askedThisLaunch = true))
    }
}
