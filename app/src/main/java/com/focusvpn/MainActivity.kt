package com.focusvpn

import android.Manifest
import android.net.VpnService
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.focusvpn.ui.AdvancedScreen
import com.focusvpn.ui.MoreBlockingScreen
import com.focusvpn.ui.SetupScreen
import com.focusvpn.ui.SetupStore
import com.focusvpn.ui.StatusScreen

class MainActivity : ComponentActivity() {
    private val consent = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        DnsFilter.start(this)
        askNotify()
    }
    private val notify = registerForActivityResult(ActivityResultContracts.RequestPermission()) { FilterAlert.update(this) }
    private var onProtected: ((Boolean) -> Unit)? = null
    private val protect = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        RemovalGuard.sync(this)
        FilterAlert.update(this)
        onProtected?.invoke(DnsFilter.isRemovalBlocked(this))
        onProtected = null
    }
    private var askedThisVisit = false
    private var askedRemovalThisVisit = false
    private var askedNotify = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DnsFilter.lockIn(this)
        setContent {
            var setupDone by rememberSaveable { mutableStateOf(SetupStore.isDone(this)) }
            var screen by rememberSaveable { mutableStateOf("status") }
            if (!setupDone) {
                SetupScreen(onTurnOn = ::turnOn, onProtect = ::askProtect, onDone = { SetupStore.markDone(this); setupDone = true; askNotify() })
                return@setContent
            }
            BackHandler(enabled = screen != "status") { screen = "status" }
            when (screen) {
                "more" -> MoreBlockingScreen(onBack = { screen = "status" })
                "advanced" -> AdvancedScreen(onBack = { screen = "status" })
                else -> StatusScreen(onTurnOn = ::turnOn, onMore = { screen = "more" }, onAdvanced = { screen = "advanced" })
            }
        }
    }

    override fun onResume() {
        super.onResume()
        DnsFilter.start(this) // No-op when already running or when Android still needs the user's OK.
        RemovalGuard.sync(this)
        FilterAlert.update(this)
        UpdateCheck.maybeCheck(this)
        // Covers phones where the filter was OK'd before this permission was asked for. Once per launch.
        if (SetupStore.isDone(this)) askNotify()
        // The filter was on once and Android wants its OK again: ask straight away, once per visit (PRD §4.4).
        if (SetupStore.isDone(this) && !askedThisVisit && !DnsFilter.hasConsent(this)) {
            askedThisVisit = true
            turnOn()
        } else if (!askedRemovalThisVisit && RemovalGuard.isOffAgain(this)) {
            // Protection was on and someone switched it off in Settings: ask again, once per visit.
            askedRemovalThisVisit = true
            askProtect {}
        }
    }

    /** Opens Android's "activate" screen for "Stop FocusVPN being removed"; reports whether it is now on. */
    private fun askProtect(onResult: (Boolean) -> Unit) {
        onProtected = onResult
        runCatching { protect.launch(DnsFilter.removalBlockIntent(this)) }.onFailure { onProtected = null; onResult(false) }
    }

    override fun onStop() {
        super.onStop()
        askedThisVisit = false
        askedRemovalThisVisit = false
    }

    /** Shows Android's VPN consent dialog if needed, then starts the filter. */
    private fun turnOn() {
        val intent = VpnService.prepare(this)
        if (intent != null) consent.launch(intent) else { DnsFilter.start(this); askNotify() }
    }

    /**
     * Asks to show notifications so "Your filter is off" can reach the user later. Called from every
     * route the filter can be turned on by (OK tapped, OK already given, setup finished, app reopened),
     * because on a real phone the OK was already given and the old single ask never ran.
     */
    private fun askNotify() {
        if (!NotifyAsk.shouldAsk(DnsFilter.hasConsent(this), FilterAlert.canNotify(this), askedNotify)) return
        askedNotify = true
        notify.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
