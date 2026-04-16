package com.example.spacer.profile

import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import com.example.spacer.events.formatEventDateNoTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException

@Composable
fun PublicProfileScreen(
    userId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { ProfileRepository() }

    var loading by remember { mutableStateOf(true) }
    var snapshot by remember { mutableStateOf<PublicProfileSnapshot?>(null) }

    suspend fun load() {
        loading = true
        withContext(Dispatchers.IO) { repository.getPublicProfile(userId) }
            .onSuccess { snapshot = it }
            .onFailure {
                Toast.makeText(context, "Couldn't open this profile right now.", Toast.LENGTH_LONG).show()
            }
        loading = false
    }

    LaunchedEffect(userId) { load() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Back") }
        Spacer(modifier = Modifier.height(12.dp))

        if (loading) {
            Text("Loading profile...")
            return@Column
        }
        val s = snapshot
        if (s == null) {
            Text("Profile unavailable.")
            return@Column
        }
        val now = OffsetDateTime.now()
        fun eventSortKey(value: String): Pair<Int, Long> {
            val parsed = try {
                OffsetDateTime.parse(value)
            } catch (_: DateTimeParseException) {
                null
            }
            val epoch = parsed?.toEpochSecond() ?: Long.MIN_VALUE
            val upcomingGroup = if (parsed != null && parsed.isAfter(now)) 0 else 1
            // Upcoming ascending, past descending.
            val order = if (upcomingGroup == 0) epoch else -epoch
            return upcomingGroup to order
        }
        val hostedSorted = s.hostedEvents.sortedWith(
            compareBy<EventRow> { eventSortKey(it.startsAt).first }
                .thenBy { eventSortKey(it.startsAt).second }
        )
        val attendedSorted = s.attendedEvents.sortedWith(
            compareBy<EventRow> { eventSortKey(it.startsAt).first }
                .thenBy { eventSortKey(it.startsAt).second }
        )
        val friendsSorted = s.friends.sortedBy { it.fullName.lowercase() }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (s.avatarUrl.isNullOrBlank()) {
                Spacer(
                    modifier = Modifier
                        .size(70.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                )
            } else {
                Image(
                    painter = rememberAsyncImagePainter(model = Uri.parse(s.avatarUrl)),
                    contentDescription = "Profile image",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(70.dp)
                        .clip(CircleShape)
                )
            }
            Spacer(modifier = Modifier.size(12.dp))
            Column {
                Text(s.fullName, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold))
                Text("@${s.username}", style = MaterialTheme.typography.bodyMedium)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                s.aboutMe,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(12.dp)
            )
        }
        Spacer(modifier = Modifier.height(12.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedButton(
                onClick = {
                    scope.launch {
                        withContext(Dispatchers.IO) { repository.sendFriendRequest(s.id) }
                            .onSuccess { Toast.makeText(context, "Friend request sent", Toast.LENGTH_SHORT).show() }
                            .onFailure { Toast.makeText(context, "Couldn't send request right now.", Toast.LENGTH_SHORT).show() }
                    }
                },
                modifier = Modifier.weight(1f)
            ) { Text("Add friend") }
            OutlinedButton(
                onClick = {
                    scope.launch {
                        withContext(Dispatchers.IO) { repository.blockUser(s.id) }
                            .onSuccess { Toast.makeText(context, "User blocked", Toast.LENGTH_SHORT).show() }
                            .onFailure { Toast.makeText(context, "Couldn't block right now.", Toast.LENGTH_SHORT).show() }
                    }
                },
                modifier = Modifier.weight(1f)
            ) { Text("Block") }
            Button(
                onClick = {
                    scope.launch {
                        withContext(Dispatchers.IO) { repository.reportUser(s.id, "Reported from profile") }
                            .onSuccess { Toast.makeText(context, "Report sent", Toast.LENGTH_SHORT).show() }
                            .onFailure { Toast.makeText(context, "Couldn't send report right now.", Toast.LENGTH_SHORT).show() }
                    }
                },
                modifier = Modifier.weight(1f)
            ) { Text("Report") }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Hosted ${hostedSorted.size}", style = MaterialTheme.typography.labelLarge)
            Text("Attended ${attendedSorted.size}", style = MaterialTheme.typography.labelLarge)
            Text("Friends ${friendsSorted.size}", style = MaterialTheme.typography.labelLarge)
        }
        Spacer(modifier = Modifier.height(12.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Text(
                    "Hosted events",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Spacer(modifier = Modifier.height(4.dp))
            }
            items(hostedSorted.take(4), key = { it.id }) { ev ->
                Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                    Column(Modifier.padding(10.dp)) {
                        Text(ev.title, fontWeight = FontWeight.SemiBold)
                        Text(formatEventDateNoTime(ev.startsAt), style = MaterialTheme.typography.bodySmall)
                        ev.location?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text("Attended events", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                Spacer(modifier = Modifier.height(4.dp))
            }
            items(attendedSorted.take(4), key = { it.id }) { ev ->
                Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                    Column(Modifier.padding(10.dp)) {
                        Text(ev.title, fontWeight = FontWeight.SemiBold)
                        Text(formatEventDateNoTime(ev.startsAt), style = MaterialTheme.typography.bodySmall)
                        ev.location?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text("Friends", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                Spacer(modifier = Modifier.height(4.dp))
            }
            items(friendsSorted.take(6), key = { it.id }) { f ->
                Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                    Column(Modifier.padding(10.dp)) {
                        Text(f.fullName, fontWeight = FontWeight.SemiBold)
                        Text("@${f.username}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}
