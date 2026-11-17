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
        // Ask to show notifications once the filter is on, so "Your filter is off" can reach the user later.
        if (DnsFilter.hasConsent(this) && !FilterAlert.canNotify(this)) notify.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
    private val notify = registerForActivityResult(ActivityResultContracts.RequestPermission()) { FilterAlert.update(this) }
    private var askedThisVisit = false
    private var askedNotify = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DnsFilter.lockIn(this)
        setContent {
            var setupDone by rememberSaveable { mutableStateOf(SetupStore.isDone(this)) }
            var screen by rememberSaveable { mutableStateOf("status") }
            if (!setupDone) {
                SetupScreen(onTurnOn = ::turnOn, onDone = { SetupStore.markDone(this); setupDone = true })
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
        FilterAlert.update(this)
        // Covers phones where the filter was OK'd before this permission was asked for. Once per launch.
        if (SetupStore.isDone(this) && !askedNotify && DnsFilter.hasConsent(this) && !FilterAlert.canNotify(this)) {
            askedNotify = true
            notify.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        // The filter was on once and Android wants its OK again: ask straight away, once per visit (PRD §4.4).
        if (SetupStore.isDone(this) && !askedThisVisit && !DnsFilter.hasConsent(this)) {
            askedThisVisit = true
            turnOn()
        }
    }

    override fun onStop() {
        super.onStop()
        askedThisVisit = false
    }

    /** Shows Android's VPN consent dialog if needed, then starts the filter. */
    private fun turnOn() {
        val intent = VpnService.prepare(this)
        if (intent != null) consent.launch(intent) else DnsFilter.start(this)
    }
}
