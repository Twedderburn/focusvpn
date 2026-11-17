package com.focusvpn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckTest {
    @Test fun readsTheTagFromGithubJson() {
        assertEquals("1.2", UpdateCheck.latestVersion("""{"url":"x","tag_name": "v1.2","name":"FocusVPN 1.2"}"""))
        assertEquals("0.10.1", UpdateCheck.latestVersion("""{"tag_name":"0.10.1"}"""))
        assertNull(UpdateCheck.latestVersion("""{"message":"Not Found"}"""))
        assertNull(UpdateCheck.latestVersion("""{"tag_name":"nightly"}"""))
    }

    @Test fun comparesVersionsByNumberNotText() {
        assertTrue(UpdateCheck.isNewer("0.2", "0.1"))
        assertTrue(UpdateCheck.isNewer("0.10", "0.9"))
        assertTrue(UpdateCheck.isNewer("1.0.1", "1.0"))
        assertTrue(UpdateCheck.isNewer("v2", "1.9.9"))
        assertFalse(UpdateCheck.isNewer("0.1", "0.1"))
        assertFalse(UpdateCheck.isNewer("1.0", "1.0.0"))
        assertFalse(UpdateCheck.isNewer("0.9", "0.10"))
    }

    @Test fun checksAtMostOnceADay() {
        val day = 24L * 60 * 60 * 1000
        assertTrue(UpdateCheck.isDue(now = 5_000, lastChecked = 0))
        assertFalse(UpdateCheck.isDue(now = day + 999, lastChecked = 1_000))
        assertTrue(UpdateCheck.isDue(now = day + 1_000, lastChecked = 1_000))
        assertTrue("clock went backwards", UpdateCheck.isDue(now = 1_000, lastChecked = 5_000))
    }

    @Test fun messageIsPlain() {
        assertEquals("A new version with more blocked sites is ready", UpdateCheck.MESSAGE)
    }
}
