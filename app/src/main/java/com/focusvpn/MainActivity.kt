package com.focusvpn

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
import com.focusvpn.ui.MoreBlockingScreen
import com.focusvpn.ui.StatusScreen

class MainActivity : ComponentActivity() {
    private val consent = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        DnsFilter.start(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DnsFilter.lockIn(this)
        setContent {
            var more by rememberSaveable { mutableStateOf(false) }
            BackHandler(enabled = more) { more = false }
            if (more) MoreBlockingScreen(onBack = { more = false })
            else StatusScreen(onTurnOn = ::turnOn, onMore = { more = true })
        }
    }

    override fun onResume() {
        super.onResume()
        DnsFilter.start(this) // No-op when already running or when Android still needs the user's OK.
    }

    /** Shows Android's VPN consent dialog if needed, then starts the filter. */
    private fun turnOn() {
        val intent = VpnService.prepare(this)
        if (intent != null) consent.launch(intent) else DnsFilter.start(this)
    }
}
