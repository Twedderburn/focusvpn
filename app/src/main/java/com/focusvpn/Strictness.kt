package com.focusvpn

import android.content.Context

/** The opt-in social sites (PRD §4.2). Each is its own one-way switch. YouTube is deliberately not here. */
enum class SocialSite(val label: String, val domains: List<String>) {
    REDDIT("Reddit", listOf("reddit.com", "redd.it", "redditmedia.com", "redditstatic.com", "reddit.app.link")),
    X("X (Twitter)", listOf("x.com", "twitter.com", "t.co", "twimg.com")),
    TIKTOK("TikTok", listOf("tiktok.com", "tiktokv.com", "tiktokcdn.com", "tiktokcdn-us.com", "musical.ly")),
    INSTAGRAM("Instagram", listOf("instagram.com", "cdninstagram.com", "ig.me")),
    // Not fbcdn.net: Instagram and WhatsApp share it, so blocking it would break sites the user kept.
    FACEBOOK("Facebook", listOf("facebook.com", "fb.com", "fb.me", "facebook.net", "m.me")),
    SNAPCHAT("Snapchat", listOf("snapchat.com", "snap.com", "sc-cdn.net", "snapkit.com", "snap-dev.net")),
}

/**
 * What the user has switched on beyond the always-on adult filter. It can only grow (PRD §3): every operation
 * returns something at least as strict, and there is no way to switch anything off or remove a site.
 * Deliberately not a data class, so there is no copy() that could switch something off. Saving
 * ([StrictnessStore.save]) and applying ([DnsFilter.apply]) also merge, so even code that built a looser value
 * couldn't loosen the filter.
 */
class Strictness internal constructor(
    val gambling: Boolean = false,
    val youtubeRestricted: Boolean = false,
    val social: Set<SocialSite> = emptySet(),
    val customSites: Set<String> = emptySet(),
) {
    fun withGambling() = union(Strictness(gambling = true))
    fun withYoutubeRestricted() = union(Strictness(youtubeRestricted = true))
    fun withSocial(site: SocialSite) = union(Strictness(social = setOf(site)))

    /** Adds a site the user typed. Null if it isn't a usable site name or [siteProblem] refuses it. */
    fun withCustomSite(input: String): Strictness? =
        if (siteProblem(input) != null) null else union(Strictness(customSites = setOf(normalizeSite(input)!!)))

    /** Both together: what's on in either. Used when saving, so nothing already on can be lost. */
    fun union(other: Strictness) = Strictness(
        gambling || other.gambling,
        youtubeRestricted || other.youtubeRestricted,
        social + other.social,
        customSites + other.customSites,
    )

    /** True if this blocks at least everything [other] blocks. */
    fun atLeast(other: Strictness) = union(other) == this

    override fun equals(other: Any?) = other is Strictness && gambling == other.gambling &&
        youtubeRestricted == other.youtubeRestricted && social == other.social && customSites == other.customSites

    override fun hashCode() = listOf(gambling, youtubeRestricted, social, customSites).hashCode()

    override fun toString() = "Strictness(gambling=$gambling, youtube=$youtubeRestricted, social=$social, custom=$customSites)"

    companion object {
        /** Nothing beyond the always-on adult filter. */
        val NONE = Strictness()

        private val SITE = Regex("""[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?(\.[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?)+""")

        /**
         * Services a phone needs to work: sign-in, updates, maps, messages, the NHS and government, and this app's
         * own updates. Blocking one could never be undone, so custom sites refuse them and anything under them.
         */
        val ESSENTIAL = setOf(
            // Google and Android: sign-in, Play, updates, maps, Gmail
            "google.com", "googleapis.com", "gstatic.com", "googleusercontent.com", "gvt1.com", "gvt2.com",
            "android.com", "google.co.uk", "gmail.com",
            // Apple, Microsoft, Samsung
            "apple.com", "icloud.com", "mzstatic.com", "microsoft.com", "live.com", "outlook.com", "office.com",
            "microsoftonline.com", "windows.net", "samsung.com", "samsungcloud.com", "samsungapps.com",
            // Messages and calls
            "whatsapp.com", "whatsapp.net", "signal.org",
            // Health, government, money
            "nhs.uk", "gov.uk", "paypal.com",
            // The internet's plumbing: many sites load through these
            "cloudflare.com", "akamai.net", "akamaihd.net", "akamaized.net", "fastly.net", "cloudfront.net",
            "amazonaws.com",
            // FocusVPN's own update check and install guide
            "github.com", "githubusercontent.com", "github.io",
        )

        /** Endings shared by many unrelated sites, like co.uk. */
        private val SHARED_ENDING = Regex("""(co|com|org|net|gov|ac|edu|ltd|plc|me|nhs|sch|police|mod)\.[a-z]{2}""")

        /** Why [input] can't be added as a custom site, in plain words, or null if it can. */
        fun siteProblem(input: String): String? {
            val site = normalizeSite(input) ?: return "That doesn't look like a website address."
            if (SHARED_ENDING.matches(site)) return "That's the ending of many websites, not one website."
            val essential = generateSequence(site) { it.substringAfter('.', "").ifEmpty { null } }.firstOrNull { it in ESSENTIAL }
            if (essential != null) return "Your phone needs $essential to work, so it can't be blocked."
            return null
        }

        /** "https://www.Example.com/page" -> "example.com". Null if it isn't a site name. */
        fun normalizeSite(input: String): String? {
            var s = input.trim().lowercase()
            s = s.substringAfter("://").substringBefore('/').substringBefore('?').substringBefore('#')
            s = s.substringAfterLast('@').substringBefore(':').trimEnd('.').removePrefix("www.")
            return s.takeIf { it.length <= 253 && SITE.matches(it) }
        }
    }
}

/** Saves [Strictness] on the phone. Saving merges with what's stored, so a save can never switch anything off. */
object StrictnessStore {
    private const val PREFS = "strictness"

    fun load(context: Context): Strictness {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return Strictness(
            gambling = p.getBoolean("gambling", false),
            youtubeRestricted = p.getBoolean("youtube", false),
            social = p.getStringSet("social", emptySet())!!.mapNotNull { n -> SocialSite.entries.find { it.name == n } }.toSet(),
            customSites = p.getStringSet("custom", emptySet())!!.toSet(),
        )
    }

    /** Stores [s] merged with what's already stored, and applies the result to the running filter. */
    @Synchronized
    fun save(context: Context, s: Strictness): Strictness {
        val merged = load(context).union(s)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean("gambling", merged.gambling)
            .putBoolean("youtube", merged.youtubeRestricted)
            .putStringSet("social", merged.social.map { it.name }.toSet())
            .putStringSet("custom", merged.customSites)
            .commit()
        DnsFilter.apply(merged)
        return merged
    }
}
