package com.focusvpn

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SafeSearchTest {
    @Test fun searchEnginesGoToTheirSafeServers() {
        listOf("google.com", "www.google.com", "google.co.uk", "www.google.com.au", "google.de", "WWW.Google.Com.")
            .forEach { assertEquals(it, "forcesafesearch.google.com", SafeSearch.target(it)) }
        listOf("bing.com", "www.bing.com").forEach { assertEquals(it, "strict.bing.com", SafeSearch.target(it)) }
        listOf("duckduckgo.com", "www.duckduckgo.com").forEach { assertEquals(it, "safe.duckduckgo.com", SafeSearch.target(it)) }
    }

    @Test fun otherNamesAreLeftAlone() {
        listOf(
            "mail.google.com", "forcesafesearch.google.com", "googleapis.com", "google.example.com", "notgoogle.com",
            "strict.bing.com", "safe.duckduckgo.com", "youtube.com", "example.com", "",
        ).forEach { assertNull(it, SafeSearch.target(it)) }
    }

    @Test fun builtQueryParsesBack() {
        val q = Dns.query(0xBEEF, "forcesafesearch.google.com", Dns.TYPE_AAAA)
        val (name, end) = Dns.questionName(q)!!
        assertEquals("forcesafesearch.google.com", name)
        assertEquals(end, q.size)
        assertEquals(Dns.TYPE_AAAA, Dns.questionType(q, end))
        assertEquals(0xBEEF, Packets.u16(q, 0))
    }

    private fun name(n: String) = n.split('.').flatMap { listOf(it.length.toByte()) + it.toByteArray().toList() } + 0.toByte()

    /** An upstream answer for strict.bing.com: a CNAME, then an A record reached through a compression pointer. */
    private fun bingUpstream(): ByteArray {
        val m = mutableListOf<Byte>()
        fun u16(v: Int) { m += (v shr 8).toByte(); m += v.toByte() }
        u16(0x0001); u16(0x8180); u16(1); u16(2); u16(0); u16(0)
        m += name("strict.bing.com"); u16(Dns.TYPE_A); u16(1)
        // strict.bing.com CNAME strict.edge.example
        u16(0xC00C); u16(5); u16(1); u16(0); u16(3600)
        val cname = name("strict.edge.example"); u16(cname.size); val cnameAt = m.size; m += cname
        // strict.edge.example A 204.79.197.220, TTL 60
        u16(0xC000 or cnameAt); u16(Dns.TYPE_A); u16(1); u16(0); u16(60); u16(4)
        m += listOf(204, 79, 197, 220).map { it.toByte() }
        return m.toByteArray()
    }

    @Test fun answersTheUsersQuestionWithTheSafeAddresses() {
        val user = Dns.query(0x4242, "www.bing.com", Dns.TYPE_A)
        val end = Dns.questionName(user)!!.second
        val r = Dns.addressAnswer(user, end, bingUpstream(), Dns.TYPE_A)!!
        assertEquals(0x4242, Packets.u16(r, 0))
        assertEquals(0x8180, Packets.u16(r, 2)) // response, recursion available, no error
        assertEquals(1, Packets.u16(r, 6)) // only the A record; the CNAME is dropped
        assertArrayEquals(user.copyOfRange(12, end), r.copyOfRange(12, end)) // the user's own question
        var i = end
        assertEquals(0xC00C, Packets.u16(r, i)) // owner: the user's name
        assertEquals(Dns.TYPE_A, Packets.u16(r, i + 2))
        assertEquals(0, Packets.u16(r, i + 6))
        assertEquals(60, Packets.u16(r, i + 8)) // TTL kept when under 5 minutes
        assertEquals(4, Packets.u16(r, i + 10))
        assertArrayEquals(listOf(204, 79, 197, 220).map { it.toByte() }.toByteArray(), r.copyOfRange(i + 12, i + 16))
        i += 16
        assertEquals(r.size, i)
    }

    @Test fun capsLongTtlsAndRejectsErrors() {
        val user = Dns.query(1, "google.com", Dns.TYPE_A)
        val end = Dns.questionName(user)!!.second
        val up = bingUpstream().also { it[it.size - 9] = 0x7F } // TTL high byte of the A record: very long
        val r = Dns.addressAnswer(user, end, up, Dns.TYPE_A)!!
        assertEquals(0, Packets.u16(r, end + 6))
        assertEquals(300, Packets.u16(r, end + 8))
        val failed = bingUpstream().also { it[3] = 0x82.toByte() } // SERVFAIL
        assertNull(Dns.addressAnswer(user, end, failed, Dns.TYPE_A))
    }

    @Test fun noDataKeepsTheQuestion() {
        val user = Dns.query(7, "google.com", 65)
        val end = Dns.questionName(user)!!.second
        val r = Dns.noData(user, end)
        assertEquals(0x8180, Packets.u16(r, 2))
        assertEquals(1, Packets.u16(r, 4))
        assertEquals(0, Packets.u16(r, 6))
        assertEquals(end, r.size)
    }
}
