package com.focusvpn.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focusvpn.DnsFilter

/** The command that makes FocusVPN the device owner; the app then locks itself in on next launch (PRD §4.5). */
const val DEVICE_OWNER_COMMAND = "adb shell dpm set-device-owner com.focusvpn/.FocusAdminReceiver"

/** Hidden: reached by tapping the status title 7 times. Never part of setup. */
@Composable
fun AdvancedScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val owner = DnsFilter.isDeviceOwner(context)
    SetupPage {
        Title("Advanced: lock it in")
        if (owner) {
            Body("This phone is already locked in ✓")
        } else {
            Card(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Text(
                    "Warning: this is very hard to undo. Afterwards the filter can't be removed, switched off or " +
                        "bypassed from the phone. Undoing it needs a computer with adb, or a factory reset that " +
                        "erases everything on the phone.",
                    fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(16.dp),
                )
            }
            Body("For people comfortable with a computer:")
            Body("1. Remove every account from this phone (Settings, Accounts). Android refuses otherwise. You can add them back afterwards.")
            Body("2. Turn on Developer options and USB debugging, and connect the phone to a computer with adb.")
            Body("3. Run this on the computer:")
            SelectionContainer {
                Text(DEVICE_OWNER_COMMAND, fontFamily = FontFamily.Monospace, fontSize = 15.sp, modifier = Modifier.padding(12.dp))
            }
            Body("4. Open FocusVPN again. It keeps itself always on, blocks the settings that could get around it, and can't be uninstalled.")
        }
        Spacer(Modifier.height(24.dp))
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text("Back", fontSize = 18.sp) }
    }
}
