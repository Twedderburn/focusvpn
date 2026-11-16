package com.focusvpn.blocklist

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Category bits. Each table entry keeps its categories in the low byte, so one lookup says which
 * category matched (the status screen shows "Blocked: adult site").
 */
object Category {
    const val ADULT = 1
    const val GAMBLING = 2 // reserved: opt-in, PRD §4.2
    const val SOCIAL = 4 // reserved: opt-in, PRD §4.2
    /** Not a list category: the built-in encrypted-DNS / bypass names. Never stored in the table. */
    const val BYPASS = 0x80

    /** List files are blocklists/<name>-domains.txt. */
    val LISTS = linkedMapOf("adult" to ADULT, "gambling" to GAMBLING, "social" to SOCIAL)
}

/**
 * The blocklist format, shared by the build ([buildSrc] compiles this same file) and the app, so both
 * hash names identically.
 *
 * Each domain is one Long: the high 56 bits are a hash of the lowercase name, the low 8 bits are its
 * categories. The table is sorted, so a lookup is a binary search on the hash bits. ~640k domains take
 * about 5 MB, against ~65 MB for a HashSet<String>.
 *
 * File layout (big-endian): "FVBL", version, entry count, reserved (16-byte header, so the entries are
 * 8-byte aligned when memory-mapped), then the entries.
 */
object Blocklist {
    const val FILE_NAME = "blocklist.bin"
    const val MAGIC = 0x4656424C // "FVBL"
    const val VERSION = 1
    const val HEADER_BYTES = 16
    const val HASH_MASK = -0x100L // high 56 bits
    const val CATEGORY_MASK = 0xFFL

    private val FNV_OFFSET = 0xcbf29ce484222325UL.toLong()
    private const val FNV_PRIME = 0x100000001b3L
    private val MIX1 = 0xff51afd7ed558ccdUL.toLong()
    private val MIX2 = 0xc4ceb9fe1a85ec53UL.toLong()

    fun normalize(domain: String): String = domain.trim().trimEnd('.').lowercase()

    /**
     * FNV-1a 64 over the UTF-8 bytes, then the MurmurHash3 finaliser so the kept high bits are well mixed.
     * Never change this without bumping [VERSION]: the build and every installed app must agree.
     */
    fun hash(normalizedDomain: String): Long {
        var h = fnv1a64(normalizedDomain)
        h = h xor (h ushr 33)
        h *= MIX1
        h = h xor (h ushr 33)
        h *= MIX2
        h = h xor (h ushr 33)
        return h and HASH_MASK
    }

    fun fnv1a64(s: String): Long {
        var h = FNV_OFFSET
        for (b in s.encodeToByteArray()) h = (h xor (b.toLong() and 0xFF)) * FNV_PRIME
        return h
    }

    /** Domains from a list file: one per line, blanks and # comments skipped. */
    fun parseList(lines: Sequence<String>): Sequence<String> =
        lines.map { normalize(it) }.filter { it.isNotEmpty() && !it.startsWith('#') }

    /** Sorted table with one entry per distinct name; a name in several lists gets all their bits. */
    fun buildTable(entries: Sequence<Pair<String, Int>>): LongArray {
        var a = LongArray(1 shl 16)
        var n = 0
        for ((domain, bits) in entries) {
            if (n == a.size) a = a.copyOf(n * 2)
            a[n++] = hash(normalize(domain)) or (bits.toLong() and CATEGORY_MASK)
        }
        a = a.copyOf(n)
        a.sort()
        var m = 0
        for (i in 0 until n) {
            if (m > 0 && (a[m - 1] and HASH_MASK) == (a[i] and HASH_MASK)) a[m - 1] = a[m - 1] or a[i] else a[m++] = a[i]
        }
        return a.copyOf(m)
    }

    fun encode(table: LongArray): ByteArray {
        val buf = ByteBuffer.allocate(HEADER_BYTES + table.size * 8).order(ByteOrder.BIG_ENDIAN)
        buf.putInt(MAGIC).putInt(VERSION).putInt(table.size).putInt(0)
        buf.asLongBuffer().put(table)
        return buf.array()
    }

    /** Reads a table written by [encode]. Throws if the data is the wrong format, truncated or unsorted. */
    fun decode(data: ByteBuffer): LongArray {
        val buf = data.duplicate().order(ByteOrder.BIG_ENDIAN)
        require(buf.remaining() >= HEADER_BYTES) { "blocklist too short" }
        require(buf.getInt() == MAGIC) { "not a blocklist" }
        val version = buf.getInt()
        require(version == VERSION) { "blocklist version $version, expected $VERSION" }
        val count = buf.getInt()
        buf.getInt()
        require(count >= 0 && buf.remaining() == count * 8) { "blocklist size mismatch" }
        val table = LongArray(count)
        buf.asLongBuffer().get(table)
        for (i in 1 until count) require(table[i - 1] < table[i]) { "blocklist not sorted at $i" }
        return table
    }

    /** Category bits listed for exactly this normalized name, or 0. */
    fun categoriesOf(table: LongArray, normalizedDomain: String): Int {
        val key = hash(normalizedDomain)
        var lo = 0
        var hi = table.size - 1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            val v = table[mid] and HASH_MASK
            when {
                v < key -> lo = mid + 1
                v > key -> hi = mid - 1
                else -> return (table[mid] and CATEGORY_MASK).toInt()
            }
        }
        return 0
    }

    /**
     * Walks the name and its parents (a.b.example.com, b.example.com, example.com, com) and returns the bits,
     * within [mask], of the most specific listed one. 0 means not blocked.
     */
    fun match(table: LongArray, normalizedDomain: String, mask: Int): Int {
        var n = normalizedDomain
        while (n.isNotEmpty()) {
            val bits = categoriesOf(table, n) and mask
            if (bits != 0) return bits
            n = n.substringAfter('.', "")
        }
        return 0
    }

    /**
     * Grow-only check: names from a previous release's list that [table] no longer blocks for [category],
     * either directly or through a listed parent.
     */
    fun noLongerBlocked(previous: Sequence<String>, table: LongArray, category: Int): List<String> =
        previous.filter { match(table, it, category) == 0 }.toList()
}
