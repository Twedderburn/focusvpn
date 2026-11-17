package com.focusvpn

import com.focusvpn.blocklist.Blocklist
import com.focusvpn.blocklist.Category
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class StrictnessTest {
    @Before fun clean() {
        DnsFilter.resetStrictnessForTest()
        DnsFilter.setBlocklistForTest(LongArray(0))
    }

    @After fun tidy() = clean()

    private fun everyStep(s: Strictness): List<Strictness> = listOfNotNull(
        s.withGambling(), s.withYoutubeRestricted(), s.withCustomSite("example.org"), s.withCustomSite("not a site"),
    ) + SocialSite.entries.map { s.withSocial(it) }

    // ---- No loosening path (PRD §3, §9) ----

    @Test fun everyOperationIsAtLeastAsStrict() {
        var states = listOf(Strictness.NONE)
        repeat(3) { states = states.flatMap { s -> everyStep(s).onEach { assertTrue("$it vs $s", it.atLeast(s)) } }.distinct() }
    }

    @Test fun publicApiHasNoWayToSwitchOff() {
        val names = Strictness::class.java.methods.map { it.name }.toSet()
        listOf("copy", "remove", "without", "disable", "minus", "clear", "set").forEach { bad ->
            assertTrue("Strictness has $bad*", names.none { it.startsWith(bad, ignoreCase = true) })
        }
    }

    @Test fun applyingALooserSettingChangesNothing() {
        val strict = Strictness.NONE.withGambling().withYoutubeRestricted().withSocial(SocialSite.REDDIT)
            .withCustomSite("example.org")!!
        DnsFilter.apply(strict)
        DnsFilter.apply(Strictness.NONE) // e.g. stale or tampered settings
        assertEquals(strict, DnsFilter.strictness)
        assertTrue(DnsFilter.isBlocked("www.reddit.com"))
        assertTrue(DnsFilter.isBlocked("example.org"))
    }

    @Test fun unionKeepsEverything() {
        val a = Strictness.NONE.withGambling().withSocial(SocialSite.X)
        val b = Strictness.NONE.withYoutubeRestricted().withSocial(SocialSite.TIKTOK).withCustomSite("a.com")!!
        val u = a.union(b)
        assertTrue(u.atLeast(a) && u.atLeast(b))
        assertEquals(u, b.union(a))
    }

    // ---- What each switch blocks ----

    @Test fun socialSitesBlockOnlyWhenSwitchedOnAndOnlyThatSite() {
        assertFalse(DnsFilter.isBlocked("reddit.com"))
        DnsFilter.apply(Strictness.NONE.withSocial(SocialSite.REDDIT))
        listOf("reddit.com", "www.reddit.com", "i.redd.it", "old.reddit.com")
            .forEach { assertEquals(it, Category.SOCIAL, DnsFilter.blockedBy(it)) }
        listOf("x.com", "instagram.com", "youtube.com", "notreddit.com").forEach { assertFalse(it, DnsFilter.isBlocked(it)) }
    }

    @Test fun youtubeIsNeverASocialSite() {
        SocialSite.entries.flatMap { it.domains }.forEach { assertFalse(it, it.contains("youtube")) }
        // Facebook must not take Instagram and WhatsApp images with it.
        assertFalse(SocialSite.FACEBOOK.domains.contains("fbcdn.net"))
    }

    @Test fun gamblingSwitchTurnsOnTheGamblingCategory() {
        DnsFilter.setBlocklistForTest(Blocklist.buildTable(sequenceOf("casino.example" to Category.GAMBLING)))
        assertFalse(DnsFilter.isBlocked("www.casino.example"))
        DnsFilter.apply(Strictness.NONE.withGambling())
        assertEquals(Category.GAMBLING, DnsFilter.blockedBy("www.casino.example"))
    }

    @Test fun customSitesBlockTheSiteAndSubdomains() {
        DnsFilter.apply(Strictness.NONE.withCustomSite("https://www.Time-Waster.example/page?x=1")!!)
        assertEquals(Category.CUSTOM, DnsFilter.blockedBy("time-waster.example"))
        assertEquals(Category.CUSTOM, DnsFilter.blockedBy("m.time-waster.example"))
        assertFalse(DnsFilter.isBlocked("example"))
    }

    @Test fun customSiteInputIsCleanedOrRejected() {
        assertEquals("example.com", Strictness.normalizeSite("  HTTPS://www.example.com:443/a/b "))
        assertEquals("sub.example.co.uk", Strictness.normalizeSite("sub.example.co.uk."))
        listOf("", "com", "not a site", "http://", "exa_mple.com", "-bad.com").forEach { assertNull(it, Strictness.normalizeSite(it)) }
    }

    @Test fun youtubeRestrictedModeOnlyWhenSwitchedOn() {
        assertNull(SafeSearch.target("www.youtube.com"))
        listOf("www.youtube.com", "m.youtube.com", "youtubei.googleapis.com", "youtube.googleapis.com", "www.youtube-nocookie.com")
            .forEach { assertEquals(it, "restrict.youtube.com", SafeSearch.target(it, youtubeRestricted = true)) }
        assertEquals("forcesafesearch.google.com", SafeSearch.target("google.com", youtubeRestricted = true))
        assertNull(SafeSearch.target("music.youtube.com", youtubeRestricted = true))
    }
}
