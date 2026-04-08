package com.example.spacer.home

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import coil.compose.rememberAsyncImagePainter
import com.example.spacer.events.EventCategories
import com.example.spacer.events.EventRepository
import com.example.spacer.events.formatEventDateNoTime
import com.example.spacer.location.PlacesRepository
import com.example.spacer.network.SessionPrefs
import com.example.spacer.profile.EventRow
import com.example.spacer.profile.PresenceStatus
import com.example.spacer.profile.ProfileRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.random.Random

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun HomeScreen(
    onViewAllEvents: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val sessionPrefs = remember { SessionPrefs(context) }
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    val eventRepo = remember { EventRepository() }
    val placesRepo = remember { PlacesRepository() }
    val profileRepo = remember { ProfileRepository() }

    var userName by remember { mutableStateOf("User") }
    var locationLabel by remember { mutableStateOf("Not set") }
    var profileImageUri by remember { mutableStateOf<String?>(null) }
    var isRefreshing by remember { mutableStateOf(false) }
    var upcomingEvents by remember { mutableStateOf<List<EventRow>>(emptyList()) }
    var eventPhotoUrls by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var selectedCategory by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var homeEventsLoading by remember { mutableStateOf(true) }
    var myPresence by remember { mutableStateOf(PresenceStatus.OFFLINE) }

    suspend fun loadDiscoverable() {
        homeEventsLoading = true
        val list = withContext(Dispatchers.IO) { eventRepo.listHomeFeedEvents(limit = 36) }
            .getOrDefault(emptyList())
            .filter { event ->
                runCatching { OffsetDateTime.parse(event.startsAt) }.getOrNull()?.isAfter(OffsetDateTime.now()) == true
            }
        upcomingEvents = list
        val locs = list.map { it.id to (it.location ?: "") }.filter { it.second.isNotBlank() }
        eventPhotoUrls = withContext(Dispatchers.IO) {
            if (!PlacesRepository.isApiKeyConfigured()) return@withContext emptyMap()
            locs.mapNotNull { (eventId, location) ->
                val photoName = placesRepo.searchText(location)
                    .getOrDefault(emptyList())
                    .firstOrNull()
                    ?.primaryPhotoName
                val url = photoName?.let { placesRepo.photoMediaUrl(it, 350) }
                if (url != null) eventId to url else null
            }.toMap()
        }
        homeEventsLoading = false
    }

    LaunchedEffect(Unit) {
        userName = sessionPrefs.getProfileName().ifBlank { "User" }
        locationLabel = sessionPrefs.getLocationLabel().ifBlank { "Not set" }
        profileImageUri = sessionPrefs.getProfileImageUri()
        myPresence = PresenceStatus.fromDb(sessionPrefs.getPresenceStatus())
        val live = withContext(Dispatchers.IO) { profileRepo.load().getOrNull() }?.profile?.presenceStatus
        if (!live.isNullOrBlank()) {
            myPresence = PresenceStatus.fromDb(live)
            sessionPrefs.savePresenceStatus(myPresence.dbValue)
        }
        loadDiscoverable()
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            val label = resolveLocationLabel(context)
            locationLabel = label
            sessionPrefs.saveLocationLabel(label)
        }
    }

    val pullRefreshState = rememberPullRefreshState(
        refreshing = isRefreshing,
        onRefresh = {
            scope.launch {
                isRefreshing = true
                userName = sessionPrefs.getProfileName().ifBlank { "User" }
                profileImageUri = sessionPrefs.getProfileImageUri()

                val hasFine = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
                val hasCoarse = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED

                if (hasFine || hasCoarse) {
                    val label = withContext(Dispatchers.IO) { resolveLocationLabel(context) }
                    locationLabel = label
                    sessionPrefs.saveLocationLabel(label)
                }
                loadDiscoverable()
                isRefreshing = false
            }
        }
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .pullRefresh(pullRefreshState)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(scrollState)
        ) {
            HeaderRow(
                userName = userName,
                locationLabel = locationLabel,
                profileImageUri = profileImageUri,
                presenceStatus = myPresence
            )

            Spacer(modifier = Modifier.height(12.dp))
            SearchBar(
                query = searchQuery,
                onQueryChange = { searchQuery = it }
            )
            Spacer(modifier = Modifier.height(14.dp))

            val tagOptions = remember(upcomingEvents) {
                val fromDb = upcomingEvents.mapNotNull { it.category?.takeIf { c -> c.isNotBlank() } }.distinct()
                if (fromDb.isEmpty()) EventCategories.all else fromDb.sorted()
            }
            val filtered = remember(upcomingEvents, selectedCategory) {
                if (selectedCategory == null) upcomingEvents
                else upcomingEvents.filter { it.category == selectedCategory }
            }
            val userStateCode = remember(locationLabel) { extractUsStateCode(locationLabel) }
            val zoned = remember(filtered, searchQuery, userStateCode) {
                if (searchQuery.trim().isNotBlank() || userStateCode == null) {
                    filtered
                } else {
                    filtered.filter { ev ->
                        val eventState = extractUsStateCode(ev.location)
                        eventState == null || eventState == userStateCode
                    }
                }
            }
            val searched = remember(zoned, searchQuery) {
                val q = searchQuery.trim().lowercase()
                if (q.isBlank()) zoned
                else zoned.filter { ev ->
                    ev.title.lowercase().contains(q) ||
                        (ev.location?.lowercase()?.contains(q) == true) ||
                        (ev.category?.lowercase()?.contains(q) == true)
                }
            }

            Text(
                "UP NEXT FOR YOU",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.72f)
            )
            Spacer(modifier = Modifier.height(8.dp))

            if (homeEventsLoading) {
                Text("Loading events…", style = MaterialTheme.typography.bodyMedium)
            } else if (searched.isEmpty()) {
                Text(
                    "No events match that search right now.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.85f)
                )
            } else {
                UpNextCard(
                    event = searched.first(),
                    onOpenChat = onViewAllEvents,
                    onDetails = onViewAllEvents
                )
            }

            Spacer(modifier = Modifier.height(14.dp))
            Spacer(modifier = Modifier.height(10.dp))
            CategoryChipRow(
                tags = tagOptions,
                selected = selectedCategory,
                onSelected = { selectedCategory = it }
            )
            Spacer(modifier = Modifier.height(16.dp))
            SectionHeader("Near you", "View all", onAction = onViewAllEvents)
            Spacer(modifier = Modifier.height(10.dp))

            if (!homeEventsLoading && searched.isNotEmpty()) {
                searched.take(4).forEach { ev ->
                    DiscoverCategoryRow(
                        event = ev,
                        imageUrl = eventPhotoUrls[ev.id],
                        onJoin = {
                            sessionPrefs.incrementAttendedCount()
                            onViewAllEvents()
                        }
                    )
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
            HostOwnCard(onClick = onViewAllEvents)
            Spacer(modifier = Modifier.height(28.dp))
        }

        PullRefreshIndicator(
            refreshing = isRefreshing,
            state = pullRefreshState,
            modifier = Modifier.align(Alignment.TopCenter)
        )
    }
}

@Composable
private fun HeaderRow(
    userName: String,
    locationLabel: String,
    profileImageUri: String?,
    presenceStatus: PresenceStatus
) {
    val screenWidth = LocalConfiguration.current.screenWidthDp
    val avatarSize = when {
        screenWidth < 360 -> 44.dp
        screenWidth > 420 -> 60.dp
        else -> 52.dp
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box {
                if (profileImageUri.isNullOrBlank()) {
                    Spacer(
                        modifier = Modifier
                            .size(avatarSize)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                    )
                } else {
                    Image(
                        painter = rememberAsyncImagePainter(profileImageUri),
                        contentDescription = "User profile picture",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(avatarSize).clip(CircleShape)
                    )
                }

                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(presenceStatus.dotColor)
                )
            }

            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    "Hey,",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.85f)
                )
                Text(
                    userName,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
        }

        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        ) {
            Text(
                locationLabel,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
            )
        }
    }
}

@Composable
private fun StarFieldBackground() {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.45f
    val starBase = MaterialTheme.colorScheme.onBackground
    val stars = remember {
        List(100) {
            val x = Random(1234 + it).nextFloat()
            val y = Random(888 + it).nextFloat()
            val radius = 0.8f + Random(333 + it).nextFloat() * 1.2f
            val alpha = 0.2f + Random(222 + it).nextFloat() * 0.6f
            Star(x, y, radius, alpha)
        }
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        stars.forEach { star ->
            val a = if (dark) star.alpha else star.alpha * 0.35f
            drawCircle(
                color = starBase.copy(alpha = a),
                radius = star.radius,
                center = androidx.compose.ui.geometry.Offset(
                    x = star.x * size.width,
                    y = star.y * size.height
                )
            )
        }
    }
}

private data class Star(
    val x: Float,
    val y: Float,
    val radius: Float,
    val alpha: Float
)

@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            placeholder = { Text("Search events, places, or tags...") },
            singleLine = true
        )
    }
}

@Composable
private fun SectionHeader(title: String, action: String, onAction: () -> Unit = {}) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(
            title,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f)
        )
        Text(
            action,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.clickable { onAction() }
        )
    }
}

@Composable
private fun CategoryChipRow(
    tags: List<String>,
    selected: String?,
    onSelected: (String?) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val allSelected = selected == null
        FilterChip(
            selected = allSelected,
            onClick = { onSelected(null) },
            label = { Text("All") },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
            )
        )
        tags.forEach { tag ->
            val on = selected == tag
            FilterChip(
                selected = on,
                onClick = { onSelected(if (on) null else tag) },
                label = { Text(tag) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                )
            )
        }
    }
}

@Composable
private fun DiscoverEventCard(
    event: EventRow,
    imageUrl: String?,
    onJoin: () -> Unit
) {
    val place = event.location?.takeIf { it.isNotBlank() } ?: "Location TBD"
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
            ) {
                if (!imageUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = imageUrl,
                        contentDescription = "Event image",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                event.title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                formatEventDateNoTime(event.startsAt),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                place,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            event.category?.takeIf { it.isNotBlank() }?.let { cat ->
                Text(
                    cat,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Button(onClick = onJoin, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
                Text("JOIN NOW")
            }
        }
    }
}

@Composable
private fun DiscoverCategoryRow(
    event: EventRow,
    imageUrl: String?,
    onJoin: () -> Unit
) {
    val place = event.location?.takeIf { it.isNotBlank() }?.let { shortenAddressNoStateZip(it) } ?: "Location TBD"
    val start = runCatching { OffsetDateTime.parse(event.startsAt) }.getOrNull()
    val month = start?.month?.name?.take(3) ?: "TBD"
    val day = start?.dayOfMonth?.toString() ?: "--"
    val time = formatTime12Hour(event.startsAt)
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(70.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(month, style = MaterialTheme.typography.labelSmall)
                    Text(day, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
                    Text(time, style = MaterialTheme.typography.labelSmall)
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    event.title,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    formatEventDateNoTime(event.startsAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    place,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    "${((event.id.hashCode() and 0x7fffffff) % 12) + 1} going",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = Color(0xFF2E8B57).copy(alpha = 0.25f)
            ) {
                Text(
                    "Public",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF9FF5BC),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                )
            }
        }
    }
    Spacer(modifier = Modifier.height(10.dp))
}

@Composable
private fun UpNextCard(
    event: EventRow,
    onOpenChat: () -> Unit,
    onDetails: () -> Unit
) {
    val place = event.location?.takeIf { it.isNotBlank() } ?: "Location TBD"
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    formatEventDateNoTime(event.startsAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
                )
                Text(
                    timeUntilLabel(event.startsAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF9FF5BC)
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                event.title,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                place,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onOpenChat,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) { Text("Open chat") }
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.35f),
                    modifier = Modifier.clickable { onDetails() }
                ) {
                    Text(
                        "Details",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

private fun timeUntilLabel(startsAt: String): String {
    val start = runCatching { OffsetDateTime.parse(startsAt) }.getOrNull() ?: return "Soon"
    val now = OffsetDateTime.now()
    val mins = java.time.Duration.between(now, start).toMinutes()
    if (mins <= 0) return "Started"
    if (mins < 60) return "In ${mins}m"
    val hours = mins / 60
    if (hours < 24) return "In ${hours}h"
    val days = hours / 24
    return "In ${days}d"
}

@Composable
private fun HostOwnCard(onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                Text("+", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onPrimary)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Host your own", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                Text(
                    "Plan something with your friends",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                )
            }
            Text("›", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

private fun resolveLocationLabel(context: Context): String {
    return try {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val gpsLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
        val networkLocation = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
        val location = pickBestLocation(gpsLocation, networkLocation) ?: return "Location unavailable"

        val geocoder = Geocoder(context, Locale.getDefault())
        val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)
        val first = addresses?.firstOrNull()
        val city = first?.locality ?: first?.subAdminArea ?: "Unknown city"
        val state = first?.adminArea?.takeIf { it.isNotBlank() } ?: ""
        val code = first?.postalCode ?: "Unknown ZIP"
        if (state.isNotBlank()) "$city, $state $code" else "$city, $code"
    } catch (_: Exception) {
        "Location unavailable"
    }
}

private fun pickBestLocation(first: Location?, second: Location?): Location? {
    if (first == null) return second
    if (second == null) return first
    return if (first.accuracy <= second.accuracy) first else second
}

private fun extractUsStateCode(value: String?): String? {
    val raw = value?.uppercase(Locale.US) ?: return null
    val match = Regex("\\b([A-Z]{2})\\s+\\d{5}(?:-\\d{4})?\\b").find(raw)
    return match?.groupValues?.getOrNull(1)
}

private fun formatTime12Hour(startsAt: String): String {
    val value = runCatching { OffsetDateTime.parse(startsAt) }.getOrNull() ?: return "--:--"
    return value.format(DateTimeFormatter.ofPattern("h:mm a", Locale.US))
}

private fun shortenAddressNoStateZip(full: String): String {
    val parts = full.split(",").map { it.trim() }.filter { it.isNotBlank() }
    return when {
        parts.size >= 2 -> "${parts[0]}, ${parts[1]}"
        parts.size == 1 -> parts[0]
        else -> full
    }
}
