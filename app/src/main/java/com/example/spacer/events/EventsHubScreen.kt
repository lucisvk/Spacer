package com.example.spacer.events

import android.widget.Toast
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import coil.compose.AsyncImage
import com.example.spacer.Navigation.AppRoutes
import com.example.spacer.location.PlacesRepository
import com.example.spacer.profile.EventRow
import com.example.spacer.social.FindPeopleScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val tabs = listOf("Invites & hosting", "Find people")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventsHubScreen(
    innerEventsNav: NavHostController,
    outerNav: NavHostController,
    onOpenInvite: (String) -> Unit,
    onOpenHostEvent: (String) -> Unit,
    onOpenPublicProfile: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var tabIndex by remember { mutableIntStateOf(0) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val eventRepo = remember { EventRepository() }
    val placesRepo = remember { PlacesRepository() }
    val notificationsRepo = remember { NotificationsRepository() }

    var pending by remember { mutableStateOf<List<PendingInviteUi>>(emptyList()) }
    var myEvents by remember { mutableStateOf<List<MyEventHubItem>>(emptyList()) }
    var publicEvents by remember { mutableStateOf<List<EventRow>>(emptyList()) }
    var eventPhotoUrls by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var loading by remember { mutableStateOf(true) }

    val innerRoute by innerEventsNav.currentBackStackEntryAsState()

    suspend fun loadHubLists() {
        loading = true
        withContext(Dispatchers.IO) {
            eventRepo.listPendingInvites()
        }.onSuccess { pending = it }
            .onFailure {
                Toast.makeText(context, "We couldn't load your invitations right now. Please try again.", Toast.LENGTH_LONG).show()
            }
        withContext(Dispatchers.IO) {
            eventRepo.listMyHostingAndAttendingEvents()
        }.onSuccess { myEvents = it }
            .onFailure {
                Toast.makeText(context, "We couldn't load your events right now. Please try again.", Toast.LENGTH_LONG).show()
            }
        withContext(Dispatchers.IO) {
            eventRepo.listPublicDiscoverableEvents()
        }.onSuccess { publicEvents = it }

        val eventLocations = (myEvents.map { it.event.id to (it.event.location ?: "") } +
            pending.map { it.eventId to (it.location ?: "") } +
            publicEvents.map { it.id to (it.location ?: "") })
            .distinctBy { it.first }
            .filter { it.second.isNotBlank() }
        eventPhotoUrls = withContext(Dispatchers.IO) {
            eventLocations.mapNotNull { (eventId, location) ->
                val photoName = placesRepo.searchText(location)
                    .getOrDefault(emptyList())
                    .firstOrNull()
                    ?.primaryPhotoName
                val url = photoName?.let { placesRepo.photoMediaUrl(it, 350) }
                if (url != null) eventId to url else null
            }.toMap()
        }
        val unread = withContext(Dispatchers.IO) { notificationsRepo.listUnread() }.getOrDefault(emptyList())
        if (unread.isNotEmpty()) {
            val summary = unread.joinToString("\n") { n -> "${n.title}: ${n.body}" }.take(800)
            withContext(Dispatchers.Main) {
                Toast.makeText(context, summary, Toast.LENGTH_LONG).show()
            }
            withContext(Dispatchers.IO) {
                unread.forEach { notificationsRepo.markRead(it.id) }
            }
        }
        loading = false
    }

    LaunchedEffect(innerRoute?.destination?.route, tabIndex) {
        if (tabIndex == 0 && innerRoute?.destination?.route == "events_hub") {
            loadHubLists()
        }
    }

    DisposableEffect(outerNav) {
        val listener = NavController.OnDestinationChangedListener { _, destination, _ ->
            if (destination.route == AppRoutes.Events) {
                scope.launch { loadHubLists() }
            }
        }
        outerNav.addOnDestinationChangedListener(listener)
        onDispose { outerNav.removeOnDestinationChangedListener(listener) }
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
                myEvents = myEvents,
                publicEvents = publicEvents,
                eventPhotoUrls = eventPhotoUrls,
                onOpenInvite = onOpenInvite,
                onOpenHostEvent = onOpenHostEvent
            )
            1 -> FindPeopleScreen(
                onOpenProfile = onOpenPublicProfile,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
private fun InvitesHostingTab(
    loading: Boolean,
    pending: List<PendingInviteUi>,
    myEvents: List<MyEventHubItem>,
    publicEvents: List<EventRow>,
    eventPhotoUrls: Map<String, String>,
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
                "Your events",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
            Text(
                "Newest first. Showing your latest 6.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
        if (loading) {
            item { Text("Loading…") }
        } else if (myEvents.isEmpty()) {
            item {
                Text(
                    "No personal events yet. Create one from the Create tab, accept an invite below, or browse public events.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                )
            }
        } else {
            items(myEvents.take(6), key = { it.event.id }) { item ->
                val ev = item.event
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (item.isHosting) onOpenHostEvent(ev.id)
                            else onOpenInvite(ev.id)
                        },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Row(
                        Modifier.padding(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        eventPhotoUrls[ev.id]?.let { imageUrl ->
                            AsyncImage(
                                model = imageUrl,
                                contentDescription = "Event place image",
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.Crop
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(ev.title, fontWeight = FontWeight.SemiBold)
                                RoleChip(isHosting = item.isHosting)
                            }
                            Text(formatEventDateNoTime(ev.startsAt), style = MaterialTheme.typography.bodySmall)
                            ev.location?.takeIf { it.isNotBlank() }?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "Public events",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
            Text(
                "Newest public listings. Showing latest 6.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
        if (!loading && publicEvents.isEmpty()) {
            item {
                Text(
                    "No public listings yet. New events you create are registered here when the table exists.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                )
            }
        } else if (!loading) {
            items(publicEvents.take(6), key = { it.id }) { ev ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenInvite(ev.id) },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Row(
                        Modifier.padding(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        eventPhotoUrls[ev.id]?.let { imageUrl ->
                            AsyncImage(
                                model = imageUrl,
                                contentDescription = "Event place image",
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.Crop
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(ev.title, fontWeight = FontWeight.SemiBold)
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.25f)
                                ) {
                                    Text(
                                        "Public",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                            Text(formatEventDateNoTime(ev.startsAt), style = MaterialTheme.typography.bodySmall)
                            ev.location?.takeIf { it.isNotBlank() }?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "Pending invitations",
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
                    Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        eventPhotoUrls[inv.eventId]?.let { imageUrl ->
                            AsyncImage(
                                model = imageUrl,
                                contentDescription = "Event place image",
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.Crop
                            )
                        }
                        Column {
                            Text(inv.title, fontWeight = FontWeight.SemiBold)
                            Text("From ${inv.hostDisplayName}", style = MaterialTheme.typography.bodySmall)
                            Text(formatEventDateNoTime(inv.startsAt), style = MaterialTheme.typography.bodySmall)
                            inv.location?.takeIf { it.isNotBlank() }?.let {
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
private fun RoleChip(isHosting: Boolean) {
    val label = if (isHosting) "Hosting" else "Attending"
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}
