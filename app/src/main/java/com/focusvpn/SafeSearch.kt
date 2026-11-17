package com.focusvpn

/**
 * Forced SafeSearch (PRD §4.1), always on. Lookups for the search engines' home names are answered with the
 * addresses of their SafeSearch-only servers, so results are filtered whatever the browser or account says.
 */
object SafeSearch {
    // google.com, google.co.uk, google.com.au, google.de ... (and www.); not mail.google.com and the like.
    private val GOOGLE = Regex("""(www\.)?google\.(com|[a-z]{2}|co\.[a-z]{2}|com\.[a-z]{2})""")

    /** The names Google lists for YouTube Restricted Mode by DNS. */
    private val YOUTUBE = setOf(
        "www.youtube.com", "m.youtube.com", "youtubei.googleapis.com", "youtube.googleapis.com", "www.youtube-nocookie.com",
    )

    /**
     * The name whose addresses should answer a lookup for [name], or null if [name] is left alone.
     * [youtubeRestricted]: the user switched on YouTube Restricted Mode (PRD §4.2).
     */
    fun target(name: String, youtubeRestricted: Boolean = false): String? {
        val n = name.trimEnd('.').lowercase()
        return when {
            youtubeRestricted && n in YOUTUBE -> "restrict.youtube.com"
            GOOGLE.matches(n) -> "forcesafesearch.google.com"
            n == "bing.com" || n == "www.bing.com" -> "strict.bing.com"
            n == "duckduckgo.com" || n == "www.duckduckgo.com" -> "safe.duckduckgo.com"
            else -> null
        }
    }
}
