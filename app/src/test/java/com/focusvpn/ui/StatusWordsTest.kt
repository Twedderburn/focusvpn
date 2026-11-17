package com.focusvpn.ui

import com.focusvpn.blocklist.Category
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StatusWordsTest {
    @Test fun eachCategoryHasItsOwnPlainAnswer() {
        assertEquals("Not blocked", testSiteAnswer(0))
        assertEquals("Blocked: adult site", testSiteAnswer(Category.ADULT))
        assertEquals("Blocked: gambling site", testSiteAnswer(Category.GAMBLING))
        assertEquals("Blocked: social site", testSiteAnswer(Category.SOCIAL))
        assertEquals("Blocked: a site you added", testSiteAnswer(Category.CUSTOM))
        assertEquals("Blocked: a site that gets around the filter", testSiteAnswer(Category.BYPASS))
    }

    @Test fun adultWinsWhenOnSeveralLists() {
        assertEquals("Blocked: adult site", testSiteAnswer(Category.ADULT or Category.GAMBLING))
    }

    @Test fun testSiteNormalisesWhatWasTyped() {
        var asked: String? = null
        val answer = testSite("  HTTPS://Example.COM/path ") { asked = it; Category.GAMBLING }
        assertEquals("example.com", asked)
        assertEquals("Blocked: gambling site", answer)
        assertEquals("Type a site, like example.com", testSite("   ") { 0 })
    }

    @Test fun noJargonInAnyUserFacingWords() {
        val words = Protection.entries.flatMap { listOf(it.title, it.detail) } +
            listOf(0, 1, 2, 4, 8, 0x80).map(::testSiteAnswer)
        for (w in words) for (bad in listOf("VPN", "DNS", "domain")) {
            assertFalse("\"$w\" says $bad", w.contains(bad, ignoreCase = true))
        }
    }

    @Test fun phoneMakerPicksTheRightCards() {
        assertEquals(PhoneMaker.SAMSUNG, phoneMaker("samsung"))
        assertEquals(PhoneMaker.PIXEL, phoneMaker("Google", "google"))
        assertEquals(PhoneMaker.OTHER, phoneMaker("OnePlus", "OnePlus"))
        for (m in PhoneMaker.entries) assertTrue(alwaysOnSteps(m).any { "Always-on" in it })
    }
}
