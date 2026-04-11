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
import com.example.spacer.ui.theme.SpacerPurpleOutline
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

    var userName by remember { mutableStateOf("User") }
    var locationLabel by remember { mutableStateOf("Not set") }
    var profileImageUri by remember { mutableStateOf<String?>(null) }
    var isRefreshing by remember { mutableStateOf(false) }
    var upcomingEvents by remember { mutableStateOf<List<EventRow>>(emptyList()) }
    var eventPhotoUrls by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var selectedCategory by remember { mutableStateOf<String?>(null) }
    var homeEventsLoading by remember { mutableStateOf(true) }

    suspend fun loadDiscoverable() {
        homeEventsLoading = true
        val list = withContext(Dispatchers.IO) { eventRepo.listUpcomingDiscoverableEvents(limit = 24) }
            .getOrDefault(emptyList())
        upcomingEvents = list
        val locs = list.map { it.id to (it.location ?: "") }.filter { it.second.isNotBlank() }
        eventPhotoUrls = withContext(Dispatchers.IO) {
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
        StarFieldBackground()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(scrollState)
        ) {
            HeaderRow(
                userName = userName,
                locationLabel = locationLabel,
                profileImageUri = profileImageUri
            )

            Spacer(modifier = Modifier.height(12.dp))
            SearchBarPlaceholder()
            Spacer(modifier = Modifier.height(10.dp))

            Button(
                onClick = {
                    val hasFine = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED
                    val hasCoarse = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED

                    if (hasFine || hasCoarse) {
                        val label = resolveLocationLabel(context)
                        locationLabel = label
                        sessionPrefs.saveLocationLabel(label)
                    } else {
                        locationPermissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            )
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Use my location")
            }

            Spacer(modifier = Modifier.height(16.dp))
            SectionHeader("Upcoming Events", "VIEW ALL", onAction = onViewAllEvents)
            Spacer(modifier = Modifier.height(10.dp))

            val tagOptions = remember(upcomingEvents) {
                val fromDb = upcomingEvents.mapNotNull { it.category?.takeIf { c -> c.isNotBlank() } }.distinct()
                if (fromDb.isEmpty()) EventCategories.all else fromDb.sorted()
            }
            val filtered = remember(upcomingEvents, selectedCategory) {
                if (selectedCategory == null) upcomingEvents
                else upcomingEvents.filter { it.category == selectedCategory }
            }

            if (homeEventsLoading) {
                Text("Loading events…", style = MaterialTheme.typography.bodyMedium)
            } else if (filtered.isEmpty()) {
                Text(
                    "No upcoming public events yet. Create one from Create or check the Events tab.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.85f)
                )
            } else {
                val firstRow = filtered.take(2)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    firstRow.forEach { ev ->
                        Box(modifier = Modifier.weight(1f, fill = true)) {
                            DiscoverEventCard(
                                event = ev,
                                imageUrl = eventPhotoUrls[ev.id],
                                onJoin = {
                                    sessionPrefs.incrementAttendedCount()
                                    onViewAllEvents()
                                }
                            )
                        }
                    }
                    if (firstRow.size == 1) {
                        Spacer(modifier = Modifier.weight(1f, fill = true))
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))
            SectionHeader("Choose By Category", "VIEW ALL", onAction = onViewAllEvents)
            Spacer(modifier = Modifier.height(10.dp))
            CategoryChipRow(
                tags = tagOptions,
                selected = selectedCategory,
                onSelected = { selectedCategory = it }
            )
            Spacer(modifier = Modifier.height(14.dp))

            if (!homeEventsLoading && filtered.isNotEmpty()) {
                filtered.drop(2).forEach { ev ->
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
            Spacer(modifier = Modifier.height(24.dp))
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
    profileImageUri: String?
) {
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
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                    )
                } else {
                    Image(
                        painter = rememberAsyncImagePainter(profileImageUri),
                        contentDescription = "User profile picture",
                        modifier = Modifier.size(52.dp).clip(CircleShape)
                    )
                }

                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF22C55E))
                )
            }

            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    "Hi! Welcome to Spacer !",
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

        Column(horizontalAlignment = Alignment.End) {
            Text(
                "Current location",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.85f)
            )
            Text(
                locationLabel,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onBackground
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
private fun SearchBarPlaceholder() {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Find amazing events",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(SpacerPurpleOutline.copy(alpha = 0.35f))
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String, action: String, onAction: () -> Unit = {}) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(
            title,
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            action,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
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
            colors = FilterChipDefaults.filterChipColors()
        )
        tags.forEach { tag ->
            val on = selected == tag
            FilterChip(
                selected = on,
                onClick = { onSelected(if (on) null else tag) },
                label = { Text(tag) },
                colors = FilterChipDefaults.filterChipColors()
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
    val place = event.location?.takeIf { it.isNotBlank() } ?: "Location TBD"
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
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
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = onJoin, shape = RoundedCornerShape(10.dp)) {
                Text("JOIN NOW")
            }
        }
    }
    Spacer(modifier = Modifier.height(10.dp))
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
        val code = first?.postalCode ?: "Unknown ZIP"
        "$city, $code"
    } catch (_: Exception) {
        "Location unavailable"
    }
}

private fun pickBestLocation(first: Location?, second: Location?): Location? {
    if (first == null) return second
    if (second == null) return first
    return if (first.accuracy <= second.accuracy) first else second
}
