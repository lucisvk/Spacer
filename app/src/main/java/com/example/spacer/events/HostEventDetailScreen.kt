package com.example.spacer.events

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.spacer.profile.EventRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun HostEventDetailScreen(
    eventId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val repo = remember { EventRepository() }
    var event by remember { mutableStateOf<EventRow?>(null) }
    var availability by remember { mutableStateOf<List<AvailabilityEntryUi>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(eventId) {
        loading = true
        withContext(Dispatchers.IO) { repo.getEvent(eventId) }
            .onSuccess { event = it }
            .onFailure {
                Toast.makeText(context, it.message ?: "Failed to load", Toast.LENGTH_LONG).show()
            }
        withContext(Dispatchers.IO) { repo.listAvailabilityForHost(eventId) }
            .onSuccess { availability = it }
            .onFailure {
                Toast.makeText(context, it.message ?: "Could not load availability", Toast.LENGTH_LONG).show()
            }
        loading = false
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("Guest availability", style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold))
        Spacer(Modifier.padding(8.dp))

        if (loading || event == null) {
            Text("Loading…")
            return@Column
        }

        val e = event!!
        Text(e.title, style = MaterialTheme.typography.titleLarge)
        Text(e.startsAt, style = MaterialTheme.typography.bodySmall)
        e.location?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall) }

        Spacer(Modifier.padding(12.dp))
        Text(
            "When people are free (from the app)",
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            "This is what invitees saved in Spacer. For a full merge with Google Calendar, a future release can connect to Google’s API with your permission.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
            modifier = Modifier.padding(bottom = 8.dp)
        )

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (availability.isEmpty()) {
                item {
                    Text(
                        "No availability yet. Guests can open their invite and tap time presets after accepting.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                    )
                }
            } else {
                items(availability, key = { it.userId }) { row ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text(row.displayName, fontWeight = FontWeight.SemiBold)
                            val slots = row.presetSlots.split(",")
                                .map { it.trim() }
                                .filter { it.isNotEmpty() }
                                .joinToString(", ")
                            if (slots.isNotBlank()) {
                                Text("Times: $slots", style = MaterialTheme.typography.bodySmall)
                            }
                            row.notes?.takeIf { it.isNotBlank() }?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
                            }
                        }
                    }
                }
            }
        }

        OutlinedButton(onClick = onBack, modifier = Modifier.padding(top = 16.dp)) {
            Text("Back")
        }
    }
}
