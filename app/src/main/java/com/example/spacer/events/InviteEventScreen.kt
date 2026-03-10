package com.example.spacer.events

import android.content.Intent
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.provider.CalendarContract
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import com.example.spacer.calendar.DeviceCalendarBusyChecker
import com.example.spacer.R
import com.example.spacer.network.SessionPrefs
import com.example.spacer.network.SupabaseManager
import com.example.spacer.profile.EventRow
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URLEncoder
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeParseException
import java.util.Locale

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
    onOpenEventChat: () -> Unit = {},
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
    var calendarGateEpoch by remember { mutableIntStateOf(0) }
    var calendarBusyOverlaps by remember { mutableStateOf(false) }
    var distanceLabel by remember { mutableStateOf<String?>(null) }
    var bringClaims by remember { mutableStateOf<List<BringItemClaimUi>>(emptyList()) }

    DisposableEffect(outerNavController) {
        val outer = outerNavController ?: return@DisposableEffect onDispose { }
        val listener = NavController.OnDestinationChangedListener { _, _, _ ->
            calendarSharingEnabled = sessionPrefs.isCalendarAvailabilitySharingEnabled()
            calendarGateEpoch++
        }
        outer.addOnDestinationChangedListener(listener)
        onDispose { outer.removeOnDestinationChangedListener(listener) }
    }

    LaunchedEffect(eventId, event?.id, calendarGateEpoch) {
        val ev = event
        calendarBusyOverlaps = if (ev == null || ev.id != eventId) {
            false
        } else {
            withContext(Dispatchers.IO) {
                if (!sessionPrefs.isDeviceCalendarReadEnabled()) return@withContext false
                if (!DeviceCalendarBusyChecker.hasReadCalendarPermission(context)) return@withContext false
                val w = DeviceCalendarBusyChecker.eventWindowMillis(ev.startsAt, ev.endsAt)
                    ?: return@withContext false
                DeviceCalendarBusyChecker.eventOverlapsBusyTime(context, w.first, w.second)
            }
        }
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
        distanceLabel = if (ev?.location.isNullOrBlank()) null else withContext(Dispatchers.IO) {
            estimateDistanceFromUser(context, ev?.location)
        }
        bringClaims = withContext(Dispatchers.IO) { repo.listBringItemClaims(eventId) }.getOrDefault(emptyList())
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
            val currentEvent = event ?: return@Column
            InviteEventBody(
                e = currentEvent,
                inviteStatus = inviteStatus,
                publicListingOnly = publicListingOnly,
                calendarSharingEnabled = calendarSharingEnabled,
                calendarBusyOverlaps = calendarBusyOverlaps,
                onOpenCalendarSettings = onOpenCalendarSettings,
                selectedPresets = selectedPresets,
                onPresetsChange = { selectedPresets = it },
                notes = notes,
                onNotesChange = { notes = it },
                onCalendarClick = {
                    val evForCalendar = event
                    if (evForCalendar == null) {
                        Toast.makeText(context, "Event data is still loading.", Toast.LENGTH_SHORT).show()
                        return@InviteEventBody
                    }
                    val startMillis = parseStartMillis(evForCalendar.startsAt, evForCalendar.endsAt)
                    if (startMillis == null) {
                        Toast.makeText(context, "Couldn't add this to calendar right now.", Toast.LENGTH_SHORT).show()
                    } else {
                        val endMillis = parseEndMillis(evForCalendar.startsAt, evForCalendar.endsAt) ?: (startMillis + 60 * 60 * 1000)
                        val intent = Intent(Intent.ACTION_INSERT).apply {
                            data = CalendarContract.Events.CONTENT_URI
                            putExtra(CalendarContract.Events.TITLE, evForCalendar.title)
                            putExtra(CalendarContract.Events.DESCRIPTION, evForCalendar.description ?: "")
                            putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, startMillis)
                            putExtra(CalendarContract.EXTRA_EVENT_END_TIME, endMillis)
                        }
                        runCatching { context.startActivity(Intent.createChooser(intent, "Add to calendar")) }
                            .onFailure {
                                Toast.makeText(context, "No calendar app found.", Toast.LENGTH_SHORT).show()
                            }
                    }
                },
                onOpenMaps = {
                    val location = event?.location?.trim().orEmpty()
                    if (location.isBlank()) {
                        Toast.makeText(context, "No venue address on this event.", Toast.LENGTH_SHORT).show()
                    } else {
                        openGoogleMapsForPlace(context, location)
                    }
                },
                distanceLabel = distanceLabel,
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
                            val overlap = withContext(Dispatchers.IO) {
                                if (!sessionPrefs.isDeviceCalendarReadEnabled()) return@withContext false
                                if (!DeviceCalendarBusyChecker.hasReadCalendarPermission(context)) return@withContext false
                                val ev = event ?: return@withContext false
                                val w = DeviceCalendarBusyChecker.eventWindowMillis(ev.startsAt, ev.endsAt)
                                    ?: return@withContext false
                                DeviceCalendarBusyChecker.eventOverlapsBusyTime(context, w.first, w.second)
                            }
                            val r = withContext(Dispatchers.IO) {
                                repo.submitAvailability(eventId, selectedPresets, notes, overlap)
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
                ,
                onOpenEventChat = onOpenEventChat
                ,
                bringClaims = bringClaims,
                onToggleBringClaim = { itemLabel, claim ->
                    scope.launch {
                        withContext(Dispatchers.IO) { repo.setBringItemClaim(eventId, itemLabel, claim) }
                            .onFailure {
                                Toast.makeText(context, it.message ?: "Couldn't update bring item", Toast.LENGTH_SHORT).show()
                            }
                        bringClaims = withContext(Dispatchers.IO) { repo.listBringItemClaims(eventId) }.getOrDefault(emptyList())
                    }
                }
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
    calendarBusyOverlaps: Boolean,
    onOpenCalendarSettings: () -> Unit,
    selectedPresets: Set<String>,
    onPresetsChange: (Set<String>) -> Unit,
    notes: String,
    onNotesChange: (String) -> Unit,
    onCalendarClick: () -> Unit,
    onOpenMaps: () -> Unit,
    distanceLabel: String?,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onSaveAvailability: () -> Unit,
    onBack: () -> Unit,
    onOpenEventChat: () -> Unit,
    bringClaims: List<BringItemClaimUi>,
    onToggleBringClaim: (itemLabel: String, claim: Boolean) -> Unit
) {
    Surface(
        shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                e.title,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
            )
            e.description?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
            }
            Text(
                "Starts: ${formatEventDateTime(e.startsAt)}",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp)
            )
            Text(
                "Time: ${formatEventTimeRange(e.startsAt, e.endsAt)}",
                style = MaterialTheme.typography.bodySmall
            )
            e.location?.takeIf { it.isNotBlank() }?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (!distanceLabel.isNullOrBlank()) {
                Text(
                    text = distanceLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
    Spacer(Modifier.height(10.dp))
    val bringItems = remember(e.bringItems) { EventRepository().parseBringItems(e.bringItems) }
    if (bringItems.isNotEmpty()) {
        Spacer(Modifier.padding(8.dp))
        Text("Bring list", style = MaterialTheme.typography.titleSmall)
        bringItems.forEach { item ->
            val key = item.trim().lowercase(Locale.US)
            val claim = bringClaims.firstOrNull { it.itemKey == key }
            val claimed = claim != null
            Text(
                if (claimed) "$item — ${claim?.claimedByName} is bringing this" else "$item — unclaimed",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .padding(top = 4.dp)
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            OutlinedButton(
                onClick = { onToggleBringClaim(item, !claimed) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
            ) {
                Text(if (claimed) "I’ll bring something else" else "I’ll bring this")
            }
        }
    }

    if (publicListingOnly) {
        Surface(
            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                "You’re viewing a public listing. Ask the host to invite you if you want to RSVP or share availability in the app.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                modifier = Modifier.padding(12.dp)
            )
        }
        Spacer(Modifier.padding(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(onClick = onCalendarClick, modifier = Modifier.weight(1f)) {
                Text("Add to calendar")
            }
            OutlinedButton(onClick = onOpenMaps, modifier = Modifier.weight(1f)) {
                Icon(
                    painter = androidx.compose.ui.res.painterResource(id = R.drawable.google__g__logo),
                    contentDescription = "Google Maps",
                    tint = androidx.compose.ui.graphics.Color.Unspecified
                )
                Text("  Open in Maps")
            }
        }
        Button(
            onClick = onOpenEventChat,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp)
        ) { Text("Open event chat") }
        OutlinedButton(
            onClick = onBack,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
        ) {
            Text("Back")
        }
        return
    }

    Spacer(Modifier.padding(12.dp))

    OutlinedButton(onClick = onCalendarClick, modifier = Modifier.fillMaxWidth()) {
        Text("Open in calendar app (Google / Apple / other)")
    }
    OutlinedButton(
        onClick = onOpenMaps,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
    ) {
        Icon(
            painter = androidx.compose.ui.res.painterResource(id = R.drawable.google__g__logo),
            contentDescription = "Google Maps",
            tint = androidx.compose.ui.graphics.Color.Unspecified
        )
        Text("  Open in Google Maps")
    }
    Text(
        "This opens your device calendar so you can save the time locally. Turn on “Read calendar busy times” in Settings if you want overlap warnings.",
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

    if (calendarBusyOverlaps) {
        Text(
            text = "Your device calendar shows another commitment overlapping this event’s time. The host may see a “busy at event time” note when you save availability.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(bottom = 10.dp)
        )
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
    OutlinedButton(
        onClick = onOpenEventChat,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
    ) { Text("Open event chat") }
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

private fun openGoogleMapsForPlace(context: android.content.Context, location: String) {
    val encoded = URLEncoder.encode(location, "UTF-8")
    val mapsUri = Uri.parse("https://www.google.com/maps/search/?api=1&query=$encoded")
    val intent = Intent(Intent.ACTION_VIEW, mapsUri).apply {
        setPackage("com.google.android.apps.maps")
    }
    runCatching { context.startActivity(intent) }
        .onFailure {
            runCatching {
                context.startActivity(Intent(Intent.ACTION_VIEW, mapsUri))
            }
        }
}

private fun estimateDistanceFromUser(context: android.content.Context, locationText: String?): String? {
    val venue = locationText?.trim()?.takeIf { it.isNotBlank() } ?: return null
    val geocoder = runCatching { android.location.Geocoder(context, Locale.getDefault()) }.getOrNull() ?: return null
    val venueAddress = runCatching { geocoder.getFromLocationName(venue, 1) }.getOrNull()?.firstOrNull() ?: return null
    val locationManager = context.getSystemService(android.content.Context.LOCATION_SERVICE) as? LocationManager ?: return null
    val hasFine = ContextCompat.checkSelfPermission(
        context,
        android.Manifest.permission.ACCESS_FINE_LOCATION
    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    val hasCoarse = ContextCompat.checkSelfPermission(
        context,
        android.Manifest.permission.ACCESS_COARSE_LOCATION
    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    if (!hasFine && !hasCoarse) return null
    val current = pickBestLocation(
        locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER),
        locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
    ) ?: return null
    val resultMeters = FloatArray(1)
    Location.distanceBetween(
        current.latitude,
        current.longitude,
        venueAddress.latitude,
        venueAddress.longitude,
        resultMeters
    )
    val miles = resultMeters[0] / 1609.344f
    return "Approx. ${"%.1f".format(Locale.US, miles)} miles away"
}

private fun pickBestLocation(first: Location?, second: Location?): Location? {
    if (first == null) return second
    if (second == null) return first
    return if (first.accuracy <= second.accuracy) first else second
}
