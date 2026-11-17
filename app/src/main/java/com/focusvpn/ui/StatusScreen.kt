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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import com.focusvpn.DnsFilter
import com.focusvpn.Strictness
import com.focusvpn.StrictnessStore
import com.focusvpn.blocklist.Category
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What the status screen tells the user. Plain words only: no "VPN" or "DNS" (PRD §3). */
enum class Protection(val title: String, val detail: String, val needsButton: Boolean) {
    ON("Protected ✓", "Adult sites are blocked on this phone.", false),
    NEEDS_OK("Not protected", "Tap below to turn the filter on. Android will ask about a \"connection request\". That's normal, and nothing leaves your phone.", true),
    PRIVATE_DNS("Not protected", "A phone setting is going around the filter. In Settings, search for \"Private\" and set it to Automatic.", false),
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

/** The "Test a site" answer for [DnsFilter.blockedBy] bits (PRD §4.3). Never says which list or why. */
fun testSiteAnswer(bits: Int): String = when {
    bits == 0 -> "Not blocked"
    bits and Category.ADULT != 0 -> "Blocked: adult site"
    bits and Category.GAMBLING != 0 -> "Blocked: gambling site"
    bits and Category.SOCIAL != 0 -> "Blocked: social site"
    bits and Category.CUSTOM != 0 -> "Blocked: a site you added"
    else -> "Blocked: a site that gets around the filter"
}

/** Answer for what the user typed, or a nudge when it isn't a site at all. */
fun testSite(input: String, blockedBy: (String) -> Int): String {
    val site = Strictness.normalizeSite(input) ?: return "Type a site, like example.com"
    return testSiteAnswer(blockedBy(site))
}

@Composable
fun StatusScreen(onTurnOn: () -> Unit, onMore: () -> Unit, onAdvanced: () -> Unit = {}) {
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
    var site by rememberSaveable { mutableStateOf("") }
    var answer by rememberSaveable { mutableStateOf<String?>(null) }
    var titleTaps by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()

    MaterialTheme {
        Surface(Modifier.fillMaxSize()) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Hidden way in to Advanced (PRD §4.5): tap the title 7 times, like Android's developer options.
                Text(
                    state.title, fontSize = 36.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                        if (++titleTaps >= 7) { titleTaps = 0; onAdvanced() }
                    },
                )
                Spacer(Modifier.height(16.dp))
                Text(state.detail, fontSize = 20.sp, textAlign = TextAlign.Center)
                if (state.needsButton) {
                    Spacer(Modifier.height(32.dp))
                    Button(onClick = onTurnOn, modifier = Modifier.fillMaxWidth().height(64.dp)) {
                        Text("Turn on the filter", fontSize = 20.sp)
                    }
                }
                Spacer(Modifier.height(48.dp))
                Text("Test a site", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = site, onValueChange = { site = it; answer = null }, singleLine = true,
                    placeholder = { Text("example.com") }, modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        scope.launch {
                            answer = withContext(Dispatchers.Default) {
                                // This screen may run before the filter has loaded anything in this process.
                                DnsFilter.loadBlocklist(context)
                                DnsFilter.apply(StrictnessStore.load(context))
                                testSite(site, DnsFilter::blockedBy)
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                ) { Text("Check", fontSize = 18.sp) }
                answer?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(32.dp))
                OutlinedButton(onClick = onMore, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                    Text("Block more sites", fontSize = 18.sp)
                }
            }
        }
    }
}
