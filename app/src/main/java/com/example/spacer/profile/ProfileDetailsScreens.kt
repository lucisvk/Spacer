package com.example.spacer.profile

import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.compose.rememberAsyncImagePainter
import com.example.spacer.events.EventRepository
import com.example.spacer.events.formatEventDateNoTime
import com.example.spacer.location.PlacesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException

@Composable
fun HostedEventsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val profileRepo = remember { ProfileRepository() }
    val eventRepo = remember { EventRepository() }
    val placesRepo = remember { PlacesRepository() }

    var loading by remember { mutableStateOf(true) }
    var events by remember { mutableStateOf<List<EventRow>>(emptyList()) }
    var photoUrls by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var cancelTarget by remember { mutableStateOf<EventRow?>(null) }
    var cancelling by remember { mutableStateOf(false) }

    suspend fun reload() {
        loading = true
        val result = withContext(Dispatchers.IO) { profileRepo.getAllHostedEvents() }
        val loaded = result.fold(
            onSuccess = { it },
            onFailure = { e ->
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, e.message ?: "Failed to load events", Toast.LENGTH_LONG).show()
                }
                emptyList()
            }
        )
        events = loaded
        val locs = loaded.map { it.id to (it.location ?: "") }.filter { it.second.isNotBlank() }
        photoUrls = withContext(Dispatchers.IO) {
            locs.mapNotNull { (eventId, location) ->
                val photoName = placesRepo.searchText(location)
                    .getOrDefault(emptyList())
                    .firstOrNull()
                    ?.primaryPhotoName
                val url = photoName?.let { placesRepo.photoMediaUrl(it, 350) }
                if (url != null) eventId to url else null
            }.toMap()
        }
        loading = false
    }

    LaunchedEffect(Unit) { reload() }

    fun isUpcoming(startsAt: String): Boolean {
        return try {
            OffsetDateTime.parse(startsAt).isAfter(OffsetDateTime.now())
        } catch (_: DateTimeParseException) {
            false
        }
    }

    cancelTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { cancelTarget = null },
            title = { Text("Cancel event?") },
            text = {
                Text(
                    "“${target.title}” will be removed and invitees will be notified (after you run the latest Supabase SQL migration).",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !cancelling,
                    onClick = {
                        cancelling = true
                        scope.launch {
                            val r = withContext(Dispatchers.IO) { eventRepo.cancelHostedEvent(target.id) }
                            cancelling = false
                            cancelTarget = null
                            r.onSuccess {
                                Toast.makeText(context, "Event cancelled", Toast.LENGTH_SHORT).show()
                                reload()
                            }.onFailure {
                                Toast.makeText(
                                    context,
                                    "Couldn't cancel this event right now. Please try again in a moment.",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    }
                ) { Text("Cancel event") }
            },
            dismissButton = {
                TextButton(onClick = { cancelTarget = null }, enabled = !cancelling) { Text("Keep") }
            }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        Text("Hosted Events", style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold))
        Spacer(modifier = Modifier.height(10.dp))
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Back") }
        Spacer(modifier = Modifier.height(10.dp))

        if (loading) {
            Text("Loading...")
        } else if (events.isEmpty()) {
            Text("No hosted events yet.")
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
                items(events, key = { it.id }) { event ->
                    val borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, borderColor),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val url = photoUrls[event.id]
                            if (!url.isNullOrBlank()) {
                                AsyncImage(
                                    model = url,
                                    contentDescription = "Event image",
                                    modifier = Modifier
                                        .size(64.dp)
                                        .clip(RoundedCornerShape(10.dp)),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Spacer(
                                    modifier = Modifier
                                        .size(64.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(event.title, style = MaterialTheme.typography.titleMedium)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    formatEventDateNoTime(event.startsAt),
                                    style = MaterialTheme.typography.bodySmall
                                )
                                event.location?.takeIf { it.isNotBlank() }?.let {
                                    Text(it, style = MaterialTheme.typography.bodySmall)
                                }
                                if (isUpcoming(event.startsAt)) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    OutlinedButton(onClick = { cancelTarget = event }) {
                                        Text("Cancel event")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AttendedEventsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    EventListScreen(
        title = "Attended Events",
        onBack = onBack,
        fetcher = { repo -> repo.getPastAttendedEvents() },
        modifier = modifier
    )
}

@Composable
private fun EventListScreen(
    title: String,
    onBack: () -> Unit,
    fetcher: suspend (ProfileRepository) -> Result<List<EventRow>>,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val repository = remember { ProfileRepository() }
    var loading by remember { mutableStateOf(true) }
    var events by remember { mutableStateOf<List<EventRow>>(emptyList()) }

    LaunchedEffect(Unit) {
        loading = true
        withContext(Dispatchers.IO) { fetcher(repository) }
            .onSuccess { events = it }
            .onFailure { Toast.makeText(context, it.message ?: "Failed to load events", Toast.LENGTH_LONG).show() }
        loading = false
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        Text(title, style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold))
        Spacer(modifier = Modifier.height(10.dp))
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Back") }
        Spacer(modifier = Modifier.height(10.dp))

        if (loading) {
            Text("Loading...")
        } else if (events.isEmpty()) {
            Text("No past events yet.")
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
                items(events) { event ->
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(event.title, style = MaterialTheme.typography.titleMedium)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(formatEventDateNoTime(event.startsAt), style = MaterialTheme.typography.bodySmall)
                            event.location?.takeIf { it.isNotBlank() }?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FriendsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val repository = remember { ProfileRepository() }
    val scope = rememberCoroutineScope()

    var loading by remember { mutableStateOf(true) }
    var friends by remember { mutableStateOf<List<FriendListItem>>(emptyList()) }

    suspend fun reload() {
        loading = true
        withContext(Dispatchers.IO) { repository.getFriends() }
            .onSuccess { friends = it }
            .onFailure {
                Toast.makeText(context, it.message ?: "Failed to load friends", Toast.LENGTH_LONG).show()
            }
        loading = false
    }

    LaunchedEffect(Unit) { reload() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        Text("Friends", style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold))
        Spacer(modifier = Modifier.height(10.dp))
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Back") }
        Spacer(modifier = Modifier.height(10.dp))

        if (loading) {
            Text("Loading...")
        } else if (friends.isEmpty()) {
            Text("No friends yet.")
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
                items(friends) { friend ->
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (friend.avatarUrl.isNullOrBlank()) {
                                    Spacer(
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary)
                                    )
                                } else {
                                    Image(
                                        painter = rememberAsyncImagePainter(Uri.parse(friend.avatarUrl)),
                                        contentDescription = "Friend avatar",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.size(44.dp).clip(CircleShape)
                                    )
                                }
                                Spacer(modifier = Modifier.size(10.dp))
                                Column {
                                    Text(friend.fullName, style = MaterialTheme.typography.titleSmall)
                                    Text("@${friend.username}", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = {
                                    scope.launch {
                                        withContext(Dispatchers.IO) { repository.unfriend(friend.id) }
                                            .onSuccess {
                                                Toast.makeText(context, "Friend removed", Toast.LENGTH_SHORT).show()
                                                reload()
                                            }
                                            .onFailure {
                                                Toast.makeText(context, it.message ?: "Failed", Toast.LENGTH_SHORT).show()
                                            }
                                    }
                                }) { Text("Unfriend") }
                                Button(onClick = {
                                    scope.launch {
                                        withContext(Dispatchers.IO) { repository.blockUser(friend.id) }
                                            .onSuccess {
                                                Toast.makeText(context, "User blocked", Toast.LENGTH_SHORT).show()
                                                reload()
                                            }
                                            .onFailure {
                                                Toast.makeText(context, it.message ?: "Failed", Toast.LENGTH_SHORT).show()
                                            }
                                    }
                                }) { Text("Block") }
                            }
                        }
                    }
                }
            }
        }
    }
}

