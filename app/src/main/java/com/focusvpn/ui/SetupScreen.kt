package com.focusvpn.ui

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focusvpn.DnsFilter

/** Whether the first-run setup (PRD §4.5) has been finished. */
object SetupStore {
    private const val PREFS = "setup"
    private const val DONE = "done"
    fun isDone(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(DONE, false)
    fun markDone(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(DONE, true).apply()
}

enum class PhoneMaker { SAMSUNG, PIXEL, OTHER }

fun phoneMaker(manufacturer: String, brand: String = ""): PhoneMaker = when {
    manufacturer.equals("samsung", ignoreCase = true) -> PhoneMaker.SAMSUNG
    manufacturer.equals("google", ignoreCase = true) || brand.equals("google", ignoreCase = true) -> PhoneMaker.PIXEL
    else -> PhoneMaker.OTHER
}

/**
 * Steps to switch on "Always-on" for FocusVPN. Menu names are quoted exactly as the phone shows them,
 * so these are the only place the word "VPN" appears: the user has to find a menu with that name.
 */
fun alwaysOnSteps(maker: PhoneMaker): List<String> = when (maker) {
    PhoneMaker.SAMSUNG -> listOf(
        "Tap \"Open settings\" below. If it opens the main Settings, go to \"Connections\", then \"More connection settings\", then \"VPN\".",
        "Tap the gear next to FocusVPN.",
        "Turn on \"Always-on VPN\".",
        "Come back here and tap \"Done, I turned it on\".",
    )
    PhoneMaker.PIXEL -> listOf(
        "Tap \"Open settings\" below. If it opens the main Settings, go to \"Network & internet\", then \"VPN\".",
        "Tap the gear next to FocusVPN.",
        "Turn on \"Always-on VPN\".",
        "Come back here and tap \"Done, I turned it on\".",
    )
    PhoneMaker.OTHER -> listOf(
        "Tap \"Open settings\" below. If it opens the main Settings, search for \"VPN\".",
        "Find FocusVPN in the list and open its settings (often a gear).",
        "Turn on \"Always-on VPN\".",
        "Come back here and tap \"Done, I turned it on\".",
    )
}

/** Screens 1 to 4; finishing screen 4 is screen 5, "Done": the status screen (PRD §4.5). */
@Composable
fun SetupScreen(onTurnOn: () -> Unit, onDone: () -> Unit) {
    val context = LocalContext.current
    var step by rememberSaveable { mutableIntStateOf(0) }
    var extras by rememberSaveable { mutableStateOf(false) }

    if (extras) {
        androidx.activity.compose.BackHandler { extras = false }
        MoreBlockingScreen(onBack = { extras = false })
        return
    }
    androidx.activity.compose.BackHandler(enabled = step > 0) { step-- }

    SetupPage {
        when (step) {
            0 -> {
                Title("Welcome")
                Body("FocusVPN blocks adult sites on this phone, in every app and browser.")
                Body("It's built to be permanent. Once it's on, there's no off switch and nothing can be unblocked.")
                Next("Next") { step = 1 }
            }
            1 -> {
                Title("Block more?")
                Body("You can also block gambling sites, social sites, or any site you name. All of this is optional.")
                Body("Anything you add stays blocked for good, so take your time. You can add more later too.")
                Spacer(Modifier.height(32.dp))
                OutlinedButton(onClick = { extras = true }, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                    Text("Choose what else to block", fontSize = 18.sp)
                }
                Next("Next") { step = 2 }
            }
            2 -> {
                val running by DnsFilter.running.collectAsState()
                Title("Turn on the filter")
                Body("Android will ask about a \"connection request\". That's normal: tap OK. Nothing leaves your phone.")
                if (running) {
                    Body("The filter is on ✓")
                    Next("Next") { step = 3 }
                } else {
                    Next("Turn on the filter", onTurnOn)
                    Spacer(Modifier.height(16.dp))
                    OutlinedButton(onClick = { step = 3 }, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                        Text("I already tapped OK", fontSize = 18.sp)
                    }
                }
            }
            else -> {
                val maker = phoneMaker(Build.MANUFACTURER, Build.BRAND)
                Title("Keep it on")
                Body("One more setting keeps the filter on after a restart, and stops other apps switching it off.")
                Spacer(Modifier.height(16.dp))
                alwaysOnSteps(maker).forEachIndexed { i, text ->
                    Card(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                        Text("${i + 1}. $text", fontSize = 18.sp, modifier = Modifier.padding(16.dp))
                    }
                }
                Next("Open settings") { openAlwaysOnSettings(context) }
                Spacer(Modifier.height(16.dp))
                OutlinedButton(onClick = onDone, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                    Text("Done, I turned it on", fontSize = 18.sp)
                }
            }
        }
    }
}

private fun openAlwaysOnSettings(context: Context) {
    val vpn = Intent(Settings.ACTION_VPN_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(vpn) }
        .onFailure { runCatching { context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
}

@Composable
internal fun SetupPage(content: @Composable () -> Unit) {
    MaterialTheme {
        Surface(Modifier.fillMaxSize()) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) { content() }
        }
    }
}

@Composable
internal fun Title(text: String) {
    Text(text, fontSize = 34.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    Spacer(Modifier.height(16.dp))
}

@Composable
internal fun Body(text: String) {
    Text(text, fontSize = 20.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 6.dp))
}

@Composable
private fun Next(label: String, onClick: () -> Unit) {
    Spacer(Modifier.height(32.dp))
    Button(onClick = onClick, modifier = Modifier.fillMaxWidth().height(64.dp)) { Text(label, fontSize = 20.sp) }
}
