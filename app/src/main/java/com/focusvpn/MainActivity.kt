package com.focusvpn

import android.net.VpnService
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import com.focusvpn.ui.StatusScreen

class MainActivity : ComponentActivity() {
    private val consent = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        DnsFilter.start(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DnsFilter.lockIn(this)
        setContent {
            StatusScreen(onTurnOn = ::turnOn)
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
