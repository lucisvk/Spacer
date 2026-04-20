package com.example.spacer.events

import android.content.Intent
import android.provider.CalendarContract
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import com.example.spacer.network.SessionPrefs
import com.example.spacer.network.SupabaseManager
import com.example.spacer.profile.EventRow
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeParseException

private val presetOptions = listOf(
    "morning" to "Morning (approx. 8–12)",
    "midday" to "Midday (approx. 12–3)",
    "afternoon" to "Afternoon (approx. 3–6)",
    "evening" to "Evening (approx. 6–10)"
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InviteEventScreen(
    eventId: String,
    onBack: () -> Unit,
    onOpenCalendarSettings: () -> Unit = {},
    outerNavController: NavHostController? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val sessionPrefs = remember { SessionPrefs(context) }
    val scope = rememberCoroutineScope()
    val repo = remember { EventRepository() }

    var event by remember { mutableStateOf<EventRow?>(null) }
    var loading by remember { mutableStateOf(true) }
    var inviteStatus by remember { mutableStateOf<String?>(null) }
    var publicListingOnly by remember { mutableStateOf(false) }
    var selectedPresets by remember { mutableStateOf(setOf<String>()) }
    var notes by remember { mutableStateOf("") }
    var calendarSharingEnabled by remember { mutableStateOf(sessionPrefs.isCalendarAvailabilitySharingEnabled()) }

    DisposableEffect(outerNavController) {
        val outer = outerNavController ?: return@DisposableEffect onDispose { }
        val listener = NavController.OnDestinationChangedListener { _, _, _ ->
            calendarSharingEnabled = sessionPrefs.isCalendarAvailabilitySharingEnabled()
        }
        outer.addOnDestinationChangedListener(listener)
        onDispose { outer.removeOnDestinationChangedListener(listener) }
    }

    LaunchedEffect(eventId) {
        loading = true
        publicListingOnly = false
        calendarSharingEnabled = sessionPrefs.isCalendarAvailabilitySharingEnabled()
        val ev = withContext(Dispatchers.IO) { repo.getEvent(eventId).getOrNull() }
        event = ev
        if (ev == null) {
            Toast.makeText(context, "Couldn't load this event right now.", Toast.LENGTH_LONG).show()
        }
        val status = withContext(Dispatchers.IO) { repo.getInviteStatusForEvent(eventId).getOrNull() }
        inviteStatus = status
        val uid = SupabaseManager.client.auth.currentUserOrNull()?.id
        publicListingOnly = uid != null && ev != null && ev.hostId != uid && status == null
        loading = false
    }

    val scroll = rememberScrollState()
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(scroll)
    ) {
        Text(
            text = if (publicListingOnly && !loading) "Public event" else "Invitation",
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
        )
        Spacer(Modifier.padding(8.dp))

        if (loading || event == null) {
            Text("Loading…")
        } else {
            InviteEventBody(
                e = event!!,
                inviteStatus = inviteStatus,
                publicListingOnly = publicListingOnly,
                calendarSharingEnabled = calendarSharingEnabled,
                onOpenCalendarSettings = onOpenCalendarSettings,
                selectedPresets = selectedPresets,
                onPresetsChange = { selectedPresets = it },
                notes = notes,
                onNotesChange = { notes = it },
                onCalendarClick = {
                    val startMillis = parseStartMillis(event!!.startsAt, event!!.endsAt)
                    if (startMillis == null) {
                        Toast.makeText(context, "Couldn't add this to calendar right now.", Toast.LENGTH_SHORT).show()
                    } else {
                        val endMillis = parseEndMillis(event!!.startsAt, event!!.endsAt) ?: (startMillis + 60 * 60 * 1000)
                        val intent = Intent(Intent.ACTION_INSERT).apply {
                            data = CalendarContract.Events.CONTENT_URI
                            putExtra(CalendarContract.Events.TITLE, event!!.title)
                            putExtra(CalendarContract.Events.DESCRIPTION, event!!.description ?: "")
                            putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, startMillis)
                            putExtra(CalendarContract.EXTRA_EVENT_END_TIME, endMillis)
                        }
                        runCatching { context.startActivity(Intent.createChooser(intent, "Add to calendar")) }
                            .onFailure {
                                Toast.makeText(context, "No calendar app found.", Toast.LENGTH_SHORT).show()
                            }
                    }
                },
                onAccept = {
                    scope.launch {
                        val r = withContext(Dispatchers.IO) { repo.respondToInvite(eventId, accept = true) }
                        r.onSuccess {
                            inviteStatus = "accepted"
                            Toast.makeText(context, "Accepted", Toast.LENGTH_SHORT).show()
                        }.onFailure {
                            Toast.makeText(context, "Couldn't accept right now. Try again.", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                onDecline = {
                    scope.launch {
                        val r = withContext(Dispatchers.IO) { repo.respondToInvite(eventId, accept = false) }
                        r.onSuccess {
                            Toast.makeText(context, "Declined", Toast.LENGTH_SHORT).show()
                            onBack()
                        }.onFailure {
                            Toast.makeText(context, "Couldn't decline right now. Try again.", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                onSaveAvailability = {
                    if (!sessionPrefs.isCalendarAvailabilitySharingEnabled()) {
                        Toast.makeText(
                            context,
                            "Turn on “Share availability with hosts” in Settings to save.",
                            Toast.LENGTH_LONG
                        ).show()
                    } else {
                        scope.launch {
                            val r = withContext(Dispatchers.IO) {
                                repo.submitAvailability(eventId, selectedPresets, notes)
                            }
                            r.onSuccess {
                                Toast.makeText(context, "Availability saved", Toast.LENGTH_SHORT).show()
                            }.onFailure {
                                Toast.makeText(context, "Couldn't save right now. Please try again.", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                },
                onBack = onBack
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InviteEventBody(
    e: EventRow,
    inviteStatus: String?,
    publicListingOnly: Boolean,
    calendarSharingEnabled: Boolean,
    onOpenCalendarSettings: () -> Unit,
    selectedPresets: Set<String>,
    onPresetsChange: (Set<String>) -> Unit,
    notes: String,
    onNotesChange: (String) -> Unit,
    onCalendarClick: () -> Unit,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onSaveAvailability: () -> Unit,
    onBack: () -> Unit
) {
    Text(e.title, style = MaterialTheme.typography.titleLarge)
    e.description?.takeIf { it.isNotBlank() }?.let {
        Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
    }
    Text("Starts: ${e.startsAt}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
    e.endsAt?.takeIf { it.isNotBlank() }?.let {
        Text("Ends: $it", style = MaterialTheme.typography.bodySmall)
    }
    e.location?.takeIf { it.isNotBlank() }?.let {
        Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
    }

    if (publicListingOnly) {
        Spacer(Modifier.padding(10.dp))
        Text(
            "You’re viewing a public listing. Ask the host to invite you if you want to RSVP or share availability in the app.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
        )
        Spacer(Modifier.padding(12.dp))
        OutlinedButton(onClick = onCalendarClick, modifier = Modifier.fillMaxWidth()) {
            Text("Open in calendar app (Google / Apple / other)")
        }
        Text(
            "This opens your device calendar so you can save the time locally.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            modifier = Modifier.padding(top = 6.dp)
        )
        OutlinedButton(
            onClick = onBack,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp)
        ) {
            Text("Back")
        }
        return
    }

    Spacer(Modifier.padding(12.dp))

    OutlinedButton(onClick = onCalendarClick, modifier = Modifier.fillMaxWidth()) {
        Text("Open in calendar app (Google / Apple / other)")
    }
    Text(
        "This opens your device calendar so you can save the time locally. Full calendar read‑sync can be added later with your permission.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
        modifier = Modifier.padding(top = 6.dp)
    )

    Spacer(Modifier.padding(16.dp))
    Text("Share availability with the host", style = MaterialTheme.typography.titleMedium)
    Text(
        "Tap the parts of the day that usually work. The host sees everyone’s choices together.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
        modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
    )

    if (!calendarSharingEnabled) {
        Text(
            text = "Calendar sharing is off. Turn it on in Settings → Calendar & availability to save your choices for the host.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        OutlinedButton(onClick = onOpenCalendarSettings, modifier = Modifier.fillMaxWidth()) {
            Text("Open calendar settings")
        }
        Spacer(Modifier.padding(8.dp))
    }

    val availabilityAlpha = if (calendarSharingEnabled) 1f else 0.45f
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.alpha(availabilityAlpha)
    ) {
        presetOptions.forEach { (key, label) ->
            val sel = key in selectedPresets
            AssistChip(
                onClick = {
                    if (calendarSharingEnabled) {
                        onPresetsChange(if (sel) selectedPresets - key else selectedPresets + key)
                    }
                },
                label = { Text(label) },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = if (sel) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant
                )
            )
        }
    }

    OutlinedTextField(
        value = notes,
        onValueChange = { if (calendarSharingEnabled) onNotesChange(it) },
        label = { Text("Notes (optional)") },
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .alpha(availabilityAlpha),
        minLines = 2,
        enabled = calendarSharingEnabled
    )

    Spacer(Modifier.padding(12.dp))

    if (inviteStatus == "pending") {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onAccept, modifier = Modifier.fillMaxWidth()) { Text("Accept invite") }
            OutlinedButton(onClick = onDecline, modifier = Modifier.fillMaxWidth()) { Text("Decline") }
        }
    }

    Button(
        onClick = onSaveAvailability,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        enabled = calendarSharingEnabled
    ) {
        Text("Save availability")
    }

    OutlinedButton(
        onClick = onBack,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
    ) {
        Text("Back")
    }
}

private fun parseStartMillis(startsAt: String, endsAt: String?): Long? {
    return try {
        val odt = OffsetDateTime.parse(startsAt)
        odt.atZoneSameInstant(ZoneId.systemDefault()).toInstant().toEpochMilli()
    } catch (_: DateTimeParseException) {
        null
    }
}

private fun parseEndMillis(startsAt: String, endsAt: String?): Long? {
    val end = endsAt?.takeIf { it.isNotBlank() } ?: return null
    return try {
        val odt = OffsetDateTime.parse(end)
        odt.atZoneSameInstant(ZoneId.systemDefault()).toInstant().toEpochMilli()
    } catch (_: DateTimeParseException) {
        null
    }
}
