package com.focusvpn.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focusvpn.DnsFilter
import com.focusvpn.SocialSite
import com.focusvpn.Strictness
import com.focusvpn.StrictnessStore
import kotlinx.coroutines.launch

/** How long a block button must be held (PRD §4.2). */
const val HOLD_MILLIS = 3_000

/**
 * Optional extra blocking (PRD §4.2). Every switch only turns things on: once on, a row just says so, and there
 * is nothing to turn it off or remove a site. Each one needs a 3-second hold.
 */
@Composable
fun MoreBlockingScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var s by remember { mutableStateOf(StrictnessStore.load(context).union(DnsFilter.strictness)) }
    fun save(next: Strictness) {
        s = StrictnessStore.save(context, next)
    }
    var removalBlocked by remember { mutableStateOf(DnsFilter.isRemovalBlocked(context)) }
    LaunchedEffect(Unit) {
        while (true) { // Android's own screen switches this on, so re-check after coming back from it.
            removalBlocked = DnsFilter.isRemovalBlocked(context)
            delay(1_000)
        }
    }

    MaterialTheme {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)) {
                TextButton(onClick = onBack) { Text("‹ Back", fontSize = 18.sp) }
                Text("Block more", fontSize = 32.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Adult sites are always blocked. You can block more below. You won't be able to undo this.",
                    fontSize = 18.sp,
                )
                Spacer(Modifier.height(16.dp))

                OptionRow("Gambling", "Betting, casino and lottery sites", s.gambling) { save(s.withGambling()) }
                OptionRow("YouTube Restricted Mode", "Hides videos YouTube marks as for adults", s.youtubeRestricted) {
                    save(s.withYoutubeRestricted())
                }
                Spacer(Modifier.height(16.dp))
                Text("Social sites", fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                SocialSite.entries.forEach { site ->
                    OptionRow(site.label, null, site in s.social) { save(s.withSocial(site)) }
                }

                Spacer(Modifier.height(24.dp))
                Text("Other sites", fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                CustomSites(s) { save(it) }

                Spacer(Modifier.height(24.dp))
                Text("Keep it installed", fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                OptionRow(
                    "Stop FocusVPN being removed", "Android won't uninstall it while this is on",
                    removalBlocked, onText = "On ✓", holdText = "Hold to turn on",
                ) { runCatching { context.startActivity(DnsFilter.removalBlockIntent(context)) } }
            }
        }
    }
}

@Composable
private fun OptionRow(
    title: String, detail: String?, on: Boolean,
    onText: String = "Blocked ✓", holdText: String = "Hold to block", onConfirmed: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 20.sp)
                if (detail != null) Text(detail, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(12.dp))
            if (on) {
                Text(onText, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            } else {
                HoldToConfirm(holdText, Modifier.width(160.dp), onConfirmed)
            }
        }
        HorizontalDivider(Modifier.padding(top = 10.dp))
    }
}

@Composable
private fun CustomSites(s: Strictness, onSave: (Strictness) -> Unit) {
    var input by remember { mutableStateOf("") }
    val problem = if (input.isBlank()) null else Strictness.siteProblem(input)
    OutlinedTextField(
        value = input,
        onValueChange = { input = it },
        label = { Text("Website, e.g. example.com") },
        singleLine = true,
        isError = problem != null,
        supportingText = { if (problem != null) Text(problem) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        modifier = Modifier.fillMaxWidth(),
    )
    if (input.isNotBlank() && problem == null) {
        val site = Strictness.normalizeSite(input)!!
        if (site in s.customSites) {
            Text("$site is already blocked.", fontSize = 16.sp)
        } else {
            HoldToConfirm("Hold to block $site", Modifier.fillMaxWidth()) {
                s.withCustomSite(input)?.let(onSave)
                input = ""
            }
        }
    }
    Spacer(Modifier.height(8.dp))
    s.customSites.sorted().forEach { Text("$it  ·  Blocked ✓", fontSize = 18.sp, modifier = Modifier.padding(vertical = 4.dp)) }
}

/** A button that fires only after being held for [HOLD_MILLIS]; letting go early does nothing. */
@Composable
fun HoldToConfirm(label: String, modifier: Modifier = Modifier, onConfirmed: () -> Unit) {
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val shape = RoundedCornerShape(28.dp)
    Box(
        modifier
            .height(56.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.primaryContainer)
            .semantics { role = Role.Button; stateDescription = "Press and hold for 3 seconds" }
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    val run = scope.launch {
                        progress.snapTo(0f)
                        progress.animateTo(1f, tween(HOLD_MILLIS, easing = LinearEasing))
                        onConfirmed()
                    }
                    tryAwaitRelease()
                    if (progress.value < 1f) run.cancel()
                    scope.launch { progress.snapTo(0f) }
                })
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.align(Alignment.CenterStart).fillMaxHeight().fillMaxWidth(progress.value)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
        )
        Text(label, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onPrimaryContainer)
    }
}
