package com.focusvpn.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focusvpn.DnsFilter
import kotlinx.coroutines.delay

/** What the status screen tells the user. Plain words only: no "VPN" or "DNS" (PRD §3). */
enum class Protection(val title: String, val detail: String, val needsButton: Boolean) {
    ON("Protected ✓", "Adult sites are blocked on this phone.", false),
    NEEDS_OK("Not protected", "Tap below to turn the filter on. Android will call this a VPN. Nothing leaves your phone.", true),
    PRIVATE_DNS("Not protected", "A \"Private DNS\" setting is going around the filter. Set it to Automatic in Settings.", false),
    STARTING("Starting…", "The filter is switching on.", false),
}

/**
 * Picks the one state to show, from three facts:
 *  - hasConsent: Android has OK'd the filter (false on first run, or after another VPN took over)
 *  - running: the filter service is up and answering lookups
 *  - privateDnsBypass: a strict Private DNS setting sends lookups around the filter
 */
fun protectionState(hasConsent: Boolean, running: Boolean, privateDnsBypass: Boolean): Protection {
    // Consent comes first: without it nothing else can be fixed from this screen.
    // A strict Private DNS beats "running": the service may be up yet see no lookups.
    return when {
        !hasConsent -> Protection.NEEDS_OK
        privateDnsBypass -> Protection.PRIVATE_DNS
        !running -> Protection.STARTING
        else -> Protection.ON
    }
}

@Composable
fun StatusScreen(onTurnOn: () -> Unit, onMore: () -> Unit) {
    val context = LocalContext.current
    val running by DnsFilter.running.collectAsState()
    var hasConsent by remember { mutableStateOf(DnsFilter.hasConsent(context)) }
    var bypass by remember { mutableStateOf(DnsFilter.privateDnsBypass(context)) }
    LaunchedEffect(Unit) {
        while (true) { // Consent and Private DNS change outside the app, so re-check them.
            hasConsent = DnsFilter.hasConsent(context)
            bypass = DnsFilter.privateDnsBypass(context)
            delay(2_000)
        }
    }
    val state = protectionState(hasConsent, running, bypass)

    MaterialTheme {
        Surface(Modifier.fillMaxSize()) {
            Column(
                Modifier.fillMaxSize().padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(state.title, fontSize = 36.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(16.dp))
                Text(state.detail, fontSize = 20.sp, textAlign = TextAlign.Center)
                if (state.needsButton) {
                    Spacer(Modifier.height(32.dp))
                    Button(onClick = onTurnOn, modifier = Modifier.fillMaxWidth().height(64.dp)) {
                        Text("Turn on the filter", fontSize = 20.sp)
                    }
                }
                Spacer(Modifier.height(48.dp))
                OutlinedButton(onClick = onMore, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                    Text("Block more sites", fontSize = 18.sp)
                }
            }
        }
    }
}
