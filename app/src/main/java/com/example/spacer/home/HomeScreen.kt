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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import coil.compose.rememberAsyncImagePainter
import com.example.spacer.network.SessionPrefs
import com.example.spacer.ui.theme.SpacerPurpleBackground
import com.example.spacer.ui.theme.SpacerPurpleOutline
import com.example.spacer.ui.theme.SpacerPurpleSurface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.random.Random

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun HomeScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val sessionPrefs = remember { SessionPrefs(context) }
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()

    var userName by remember { mutableStateOf("User") }
    var locationLabel by remember { mutableStateOf("Not set") }
    var profileImageUri by remember { mutableStateOf<String?>(null) }
    var isRefreshing by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        userName = sessionPrefs.getProfileName().ifBlank { "User" }
        locationLabel = sessionPrefs.getLocationLabel().ifBlank { "Not set" }
        profileImageUri = sessionPrefs.getProfileImageUri()
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
                isRefreshing = false
            }
        }
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(SpacerPurpleBackground)
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
            SectionHeader("Upcoming Events", "VIEW ALL")
            Spacer(modifier = Modifier.height(10.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(modifier = Modifier.weight(1f, fill = true)) {
                    DummyEventCard(
                        title = "K-Town Karaoke",
                        date = "25 March, 2026",
                        place = "K-Town, NY",
                        onJoin = { sessionPrefs.incrementAttendedCount() }
                    )
                }
                Box(modifier = Modifier.weight(1f, fill = true)) {
                    DummyEventCard(
                        title = "Friendsgivings 2026",
                        date = "20 November, 2026",
                        place = "New York, NY",
                        onJoin = { sessionPrefs.incrementAttendedCount() }
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))
            SectionHeader("Choose By Category", "VIEW ALL")
            Spacer(modifier = Modifier.height(10.dp))
            CategoryChipRow()
            Spacer(modifier = Modifier.height(14.dp))

            DummyCategoryRow(
                title = "Library Study Group Session",
                date = "12 April, 2026",
                place = "New York Public Library, NY",
                onJoin = { sessionPrefs.incrementAttendedCount() }
            )
            DummyCategoryRow(
                title = "Exam Prep Study Session",
                date = "20 April, 2026",
                place = "New York Public Library, NY",
                onJoin = { sessionPrefs.incrementAttendedCount() }
            )
            DummyCategoryRow(
                title = "Study Session",
                date = "30 April, 2026",
                place = "New York Public Library, NY",
                onJoin = { sessionPrefs.incrementAttendedCount() }
            )
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
                Text("Hi! Welcome to Spacer !", style = MaterialTheme.typography.bodySmall)
                Text(
                    userName,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            }
        }

        Column(horizontalAlignment = Alignment.End) {
            Text("Current location", style = MaterialTheme.typography.bodySmall)
            Text(
                locationLabel,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
            )
        }
    }
}

@Composable
private fun StarFieldBackground() {
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
            drawCircle(
                color = Color.White.copy(alpha = star.alpha),
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
        color = SpacerPurpleSurface.copy(alpha = 0.35f),
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
                style = MaterialTheme.typography.bodyMedium.copy(color = Color(0xFFB6AEFF))
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
private fun SectionHeader(title: String, action: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(
            title,
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
        )
        Text(
            action,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold)
        )
    }
}

@Composable
private fun CategoryChipRow() {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        SimpleChip("Studying")
        SimpleChip("Adventure")
        SimpleChip("Birthday")
        SimpleChip("Potluck")
    }
}

@Composable
private fun SimpleChip(label: String) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = Color(0xFF6D40FF).copy(alpha = 0.35f),
        modifier = Modifier
    ) {
        Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            Text(label, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold))
        }
    }
}

@Composable
private fun DummyEventCard(
    title: String,
    date: String,
    place: String,
    onJoin: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF12084A).copy(alpha = 0.6f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF2A1A63))
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(title, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = Color.White)
            Spacer(modifier = Modifier.height(4.dp))
            Text(date, style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFFB9B1FF)))
            Text(place, style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFFB9B1FF)))
            Spacer(modifier = Modifier.height(10.dp))
            Button(onClick = onJoin, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
                Text("JOIN NOW")
            }
        }
    }
}

@Composable
private fun DummyCategoryRow(
    title: String,
    date: String,
    place: String,
    onJoin: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF2B1C70).copy(alpha = 0.65f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF6D40FF).copy(alpha = 0.35f))
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), color = Color.White)
                Spacer(modifier = Modifier.height(2.dp))
                Text(date, style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFFB9B1FF)))
                Text(place, style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFFB9B1FF)))
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
