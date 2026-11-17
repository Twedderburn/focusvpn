package com.focusvpn

/** When to ask Android for permission to show notifications. Kept pure so it can be unit-tested. */
object NotifyAsk {
    /**
     * Ask only once the filter has Android's OK, only if notifications aren't allowed yet,
     * and at most once per app launch so the question doesn't nag.
     */
    fun shouldAsk(hasConsent: Boolean, canNotify: Boolean, askedThisLaunch: Boolean): Boolean =
        hasConsent && !canNotify && !askedThisLaunch
}
