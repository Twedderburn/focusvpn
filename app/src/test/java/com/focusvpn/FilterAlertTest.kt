package com.focusvpn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FilterAlertTest {
    @Test fun neverBeforeSetupIsFinished() {
        for (c in listOf(false, true)) for (b in listOf(false, true)) assertFalse(FilterAlert.needsAlert(false, c, b))
    }

    @Test fun alertsWhenAndroidNeedsTheOkAgain() = assertTrue(FilterAlert.needsAlert(true, hasConsent = false, bypass = false))

    @Test fun alertsWhenASettingGoesAroundTheFilter() = assertTrue(FilterAlert.needsAlert(true, hasConsent = true, bypass = true))

    @Test fun quietWhenAllIsWell() = assertFalse(FilterAlert.needsAlert(true, hasConsent = true, bypass = false))

    @Test fun wordingIsPlain() {
        assertEquals("Your filter is off - tap to turn it back on", FilterAlert.TEXT)
        for (bad in listOf("VPN", "DNS", "domain")) assertFalse(FilterAlert.TEXT.contains(bad, ignoreCase = true))
    }
}
