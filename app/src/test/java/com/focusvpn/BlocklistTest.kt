package com.focusvpn

import com.focusvpn.blocklist.Blocklist
import com.focusvpn.blocklist.Category
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer

class BlocklistTest {
    private fun table(vararg entries: Pair<String, Int>) = Blocklist.buildTable(entries.asSequence())

    // ---- Hash ----

    @Test fun fnvMatchesPublishedVectors() {
        assertEquals(0xcbf29ce484222325UL.toLong(), Blocklist.fnv1a64(""))
        assertEquals(0xaf63dc4c8601ec8cUL.toLong(), Blocklist.fnv1a64("a"))
        assertEquals(0x85944171f73967e8UL.toLong(), Blocklist.fnv1a64("foobar"))
    }

    @Test fun hashKeepsLowByteForCategoriesAndIgnoresCaseAndTrailingDot() {
        val h = Blocklist.hash("example.com")
        assertEquals(0L, h and 0xFF)
        assertEquals(h, Blocklist.hash(Blocklist.normalize(" Example.COM. ")))
        assertTrue(h != Blocklist.hash("example.co"))
    }

    // ---- Lookup ----

    @Test fun matchesNameAndEverySubdomainButNotLookalikes() {
        val t = table("bad.com" to Category.ADULT)
        listOf("bad.com", "www.bad.com", "a.b.c.bad.com").forEach { assertEquals(it, Category.ADULT, Blocklist.match(t, it, 0xFF)) }
        listOf("notbad.com", "bad.co", "bad.com.au", "com", "").forEach { assertEquals(it, 0, Blocklist.match(t, it, 0xFF)) }
    }

    @Test fun reportsWhichCategoryMatchedAndHonoursTheMask() {
        val t = table("casino.example" to Category.GAMBLING, "porn.example" to Category.ADULT, "both.example" to Category.ADULT,
            "both.example" to Category.SOCIAL)
        assertEquals(Category.GAMBLING, Blocklist.match(t, "www.casino.example", 0xFF))
        assertEquals(0, Blocklist.match(t, "www.casino.example", Category.ADULT)) // gambling not switched on
        assertEquals(Category.ADULT, Blocklist.match(t, "porn.example", Category.ADULT))
        assertEquals(Category.ADULT or Category.SOCIAL, Blocklist.categoriesOf(t, "both.example"))
        assertEquals(3, t.size) // a name in two lists is one entry
    }

    @Test fun mostSpecificListedNameWins() {
        val t = table("example.com" to Category.SOCIAL, "x.example.com" to Category.ADULT)
        assertEquals(Category.ADULT, Blocklist.match(t, "a.x.example.com", 0xFF))
        assertEquals(Category.SOCIAL, Blocklist.match(t, "y.example.com", 0xFF))
        assertEquals(Category.SOCIAL, Blocklist.match(t, "a.x.example.com", Category.SOCIAL))
    }

    @Test fun dnsFilterReportsCategoryAndBypass() {
        DnsFilter.setBlocklistForTest(table("listed.example" to Category.ADULT, "casino.example" to Category.GAMBLING))
        assertEquals(Category.ADULT, DnsFilter.blockedBy("www.listed.example."))
        assertEquals(Category.ADULT, DnsFilter.blockedBy("pornhub.com")) // built-in
        assertEquals(Category.BYPASS, DnsFilter.blockedBy("dns.google"))
        assertFalse(DnsFilter.isBlocked("casino.example")) // gambling is opt-in and off
        DnsFilter.setBlocklistForTest(LongArray(0))
    }

    // ---- File format ----

    @Test fun encodeDecodeRoundTrips() {
        val t = table("a.com" to 1, "b.com" to 2, "c.com" to 4)
        val bytes = Blocklist.encode(t)
        assertEquals(Blocklist.HEADER_BYTES + 3 * 8, bytes.size)
        assertTrue(t.contentEquals(Blocklist.decode(ByteBuffer.wrap(bytes))))
    }

    @Test fun decodeRejectsBadFiles() {
        val good = Blocklist.encode(table("a.com" to 1, "b.com" to 1))
        val truncated = good.copyOf(good.size - 1)
        val wrongMagic = good.copyOf().also { it[0] = 0 }
        val wrongVersion = good.copyOf().also { it[7] = 99 }
        val unsorted = good.copyOf().also { // swap the two entries
            val a = it.copyOfRange(16, 24); System.arraycopy(it, 24, it, 16, 8); System.arraycopy(a, 0, it, 24, 8)
        }
        listOf(ByteArray(3), truncated, wrongMagic, wrongVersion, unsorted).forEach { bad ->
            assertTrue(runCatching { Blocklist.decode(ByteBuffer.wrap(bad)) }.isFailure)
        }
    }

    // ---- Grow-only check (the build runs this against blocklists/released) ----

    @Test fun growOnlyCheckFlagsDroppedNamesOnly() {
        val released = sequenceOf("a.com", "b.com", "sub.c.com", "d.com")
        // b.com dropped; sub.c.com now covered by its parent c.com; d.com moved to another category.
        val now = table("a.com" to Category.ADULT, "c.com" to Category.ADULT, "d.com" to Category.GAMBLING)
        assertEquals(listOf("b.com", "d.com"), Blocklist.noLongerBlocked(released, now, Category.ADULT))
        val grown = table("a.com" to 1, "b.com" to 1, "c.com" to 1, "d.com" to 1, "new.com" to 1)
        assertEquals(emptyList<String>(), Blocklist.noLongerBlocked(released, grown, Category.ADULT))
    }

    @Test fun shippedBlocklistPassesGrowOnlyAgainstLastRelease() {
        val released = listOf("../blocklists", "blocklists").map { java.io.File(it, "released/adult-domains.txt.gz") }
            .firstOrNull { it.exists() } ?: return
        val path = System.getProperty("focusvpn.blocklist") ?: error("run through Gradle: focusvpn.blocklist not set")
        val t = Blocklist.decode(ByteBuffer.wrap(java.io.File(path).readBytes()))
        val dropped = java.util.zip.GZIPInputStream(released.inputStream()).bufferedReader().useLines {
            Blocklist.noLongerBlocked(Blocklist.parseList(it), t, Category.ADULT)
        }
        assertEquals(emptyList<String>(), dropped)
    }

    // ---- Cost (PRD §5 target: about 10 MB of RAM; adult + gambling is ~10.4 MB) ----

    @Test fun shippedBlocklistLoadCost() {
        val path = System.getProperty("focusvpn.blocklist") ?: error("run through Gradle: focusvpn.blocklist not set")
        val rt = Runtime.getRuntime()
        fun used(): Long { repeat(3) { System.gc() }; return rt.totalMemory() - rt.freeMemory() }
        val before = used()
        val start = System.nanoTime()
        val t = java.io.RandomAccessFile(path, "r").use { f ->
            Blocklist.decode(f.channel.map(java.nio.channels.FileChannel.MapMode.READ_ONLY, 0, f.length()))
        }
        val ms = (System.nanoTime() - start) / 1_000_000
        val heapMb = (used() - before) / 1e6
        val lookups = 100_000
        val lookStart = System.nanoTime()
        repeat(lookups) { Blocklist.match(t, "www.site$it.example.com", 0xFF) }
        val usPerLookup = (System.nanoTime() - lookStart) / 1000.0 / lookups
        println("blocklist.bin: ${t.size} entries, load $ms ms, heap +%.1f MB, %.2f µs per 4-level lookup".format(heapMb, usPerLookup))
        assertTrue("table ${t.size * 8 / 1e6} MB", t.size * 8L < 12_000_000L)
        assertTrue(t.isNotEmpty())
    }
}
