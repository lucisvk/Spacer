package com.example.spacer.events

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.spacer.profile.EventRow
import com.example.spacer.profile.ProfileRepository
import com.example.spacer.social.FindPeopleScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val tabs = listOf("Invites & hosting", "Find people")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventsHubScreen(
    onOpenInvite: (String) -> Unit,
    onOpenHostEvent: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var tabIndex by remember { mutableIntStateOf(0) }
    val context = LocalContext.current
    val eventRepo = remember { EventRepository() }

    var pending by remember { mutableStateOf<List<PendingInviteUi>>(emptyList()) }
    var hosting by remember { mutableStateOf<List<EventRow>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        loading = true
        withContext(Dispatchers.IO) {
            eventRepo.listPendingInvites()
        }.onSuccess { pending = it }
            .onFailure {
                Toast.makeText(context, it.message ?: "Could not load invites", Toast.LENGTH_LONG).show()
            }
        withContext(Dispatchers.IO) {
            eventRepo.listUpcomingHostedEvents()
        }.onSuccess { hosting = it }
            .onFailure {
                Toast.makeText(context, it.message ?: "Could not load hosting", Toast.LENGTH_LONG).show()
            }
        loading = false
    }

    Column(modifier = modifier.fillMaxSize()) {
        ScrollableTabRow(selectedTabIndex = tabIndex) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = index == tabIndex,
                    onClick = { tabIndex = index },
                    text = { Text(title) }
                )
            }
        }
        when (tabIndex) {
            0 -> InvitesHostingTab(
                loading = loading,
                pending = pending,
                hosting = hosting,
                onOpenInvite = onOpenInvite,
                onOpenHostEvent = onOpenHostEvent
            )
            1 -> FindPeopleScreen(modifier = Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun InvitesHostingTab(
    loading: Boolean,
    pending: List<PendingInviteUi>,
    hosting: List<EventRow>,
    onOpenInvite: (String) -> Unit,
    onOpenHostEvent: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                "You’re hosting",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
            Text(
                "Open an event to see when guests are free (availability they share in the app).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
        if (loading) {
            item { Text("Loading…") }
        } else if (hosting.isEmpty()) {
            item {
                Text(
                    "No upcoming hosted events. Create one from the Create tab.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                )
            }
        } else {
            items(hosting, key = { it.id }) { ev ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenHostEvent(ev.id) },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text(ev.title, fontWeight = FontWeight.SemiBold)
                        Text(ev.startsAt, style = MaterialTheme.typography.bodySmall)
                        ev.location?.takeIf { it.isNotBlank() }?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "Invitations",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
            Spacer(modifier = Modifier.height(4.dp))
        }

        if (!loading && pending.isEmpty()) {
            item {
                Text(
                    "No pending invites. When a host adds you, invitations appear here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                )
            }
        } else {
            items(pending, key = { it.inviteId }) { inv ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenInvite(inv.eventId) },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text(inv.title, fontWeight = FontWeight.SemiBold)
                        Text("From ${inv.hostDisplayName}", style = MaterialTheme.typography.bodySmall)
                        Text(inv.startsAt, style = MaterialTheme.typography.bodySmall)
                        inv.location?.takeIf { it.isNotBlank() }?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}
