package com.example.spacer.social

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import com.example.spacer.profile.ProfileRepository
import com.example.spacer.profile.SearchUserRow
import com.example.spacer.profile.FriendshipState
import com.example.spacer.profile.PresenceStatus
import com.example.spacer.profile.displayName
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

@OptIn(FlowPreview::class)
@Composable
fun FindPeopleScreen(
    onOpenProfile: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { ProfileRepository() }

    var query by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf<List<SearchUserRow>>(emptyList()) }
    var friendshipStates by remember { mutableStateOf<Map<String, FriendshipState>>(emptyMap()) }
    var refreshToken by remember { mutableIntStateOf(0) }
    val scrollState = rememberScrollState()
    val lifecycleOwner = LocalLifecycleOwner.current

    suspend fun refreshFriendshipStates(current: List<SearchUserRow>) {
        val ids = current.map { it.id }
        val states = withContext(Dispatchers.IO) { repository.getFriendshipStates(ids) }
            .getOrDefault(emptyMap())
        friendshipStates = states
    }

    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshToken++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(refreshToken) {
        snapshotFlow { query }
            .debounce(350L)
            .distinctUntilChanged()
            .collectLatest { q ->
                loading = true
                try {
                    val result = withContext(Dispatchers.IO) { repository.searchUsers(q) }
                    result
                        .onSuccess {
                            results = it
                            refreshFriendshipStates(it)
                        }
                        .onFailure {
                            Toast.makeText(context, "Couldn't search right now. Please try again.", Toast.LENGTH_LONG).show()
                            results = emptyList()
                            friendshipStates = emptyMap()
                        }
                } finally {
                    if (coroutineContext.isActive) loading = false
                }
            }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        Text(
            text = "Find people",
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
        )
        Spacer(modifier = Modifier.height(10.dp))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Search by name or username") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            trailingIcon = {
                if (loading) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .size(22.dp)
                            .padding(end = 4.dp),
                        strokeWidth = 2.dp
                    )
                }
            }
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            "RESULTS",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
        Spacer(modifier = Modifier.height(8.dp))

        val trimmed = query.trim()
        val queryReady = trimmed.isNotEmpty()

        when {
            !queryReady && !loading -> Text(
                "Search to discover more people",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                    .padding(vertical = 24.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            loading && results.isEmpty() -> Text("Searching…", style = MaterialTheme.typography.bodyMedium)
            queryReady && !loading && results.isEmpty() -> Text(
                "No users match that search. Try another name, username, or email prefix.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
            )
            else -> {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    results.forEach { user ->
                        UserResultCard(
                            user = user,
                            onViewProfile = { onOpenProfile(user.id) },
                            friendshipState = friendshipStates[user.id] ?: FriendshipState.NONE,
                            onPrimaryAction = {
                                scope.launch {
                                    val state = friendshipStates[user.id] ?: FriendshipState.NONE
                                    val result = withContext(Dispatchers.IO) {
                                        when (state) {
                                            FriendshipState.NONE -> repository.sendFriendRequest(user.id)
                                            FriendshipState.INCOMING_PENDING ->
                                                repository.respondToFriendRequest(user.id, accept = true)
                                            FriendshipState.ACCEPTED -> repository.unfriend(user.id)
                                            FriendshipState.OUTGOING_PENDING -> Result.success(Unit)
                                        }
                                    }
                                    result.onSuccess {
                                        val msg = when (state) {
                                            FriendshipState.NONE -> "Friend request sent"
                                            FriendshipState.INCOMING_PENDING -> "Friend request accepted"
                                            FriendshipState.ACCEPTED -> "Friend removed"
                                            FriendshipState.OUTGOING_PENDING -> "Request already sent"
                                        }
                                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                        refreshFriendshipStates(results)
                                    }.onFailure {
                                        Toast.makeText(context, it.message ?: "Action failed. Try again.", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            onBlock = {
                                scope.launch {
                                    withContext(Dispatchers.IO) { repository.blockUser(user.id) }
                                        .onSuccess {
                                            Toast.makeText(context, "User blocked", Toast.LENGTH_SHORT).show()
                                            results = results.filterNot { it.id == user.id }
                                            friendshipStates = friendshipStates - user.id
                                        }
                                        .onFailure {
                                            Toast.makeText(context, it.message ?: "Couldn't block right now. Try again.", Toast.LENGTH_SHORT).show()
                                        }
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun UserResultCard(
    user: SearchUserRow,
    friendshipState: FriendshipState,
    onViewProfile: () -> Unit,
    onPrimaryAction: () -> Unit,
    onBlock: () -> Unit
) {
    val screenWidth = LocalConfiguration.current.screenWidthDp
    val avatarSize = when {
        screenWidth < 360 -> 40.dp
        screenWidth > 420 -> 52.dp
        else -> 46.dp
    }
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (user.avatarUrl.isNullOrBlank()) {
                    Spacer(
                        modifier = Modifier
                            .size(avatarSize)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                    )
                } else {
                    Image(
                        painter = rememberAsyncImagePainter(model = user.avatarUrl),
                        contentDescription = "User avatar",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(avatarSize)
                            .clip(CircleShape)
                    )
                }
                Spacer(modifier = Modifier.size(10.dp))
                Column {
                    Text(
                        text = user.displayName(),
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "@${user.username ?: "user"} · ${PresenceStatus.fromDb(user.presenceStatus).label}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(PresenceStatus.fromDb(user.presenceStatus).dotColor)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            val compactButtons = screenWidth < 390
            val primaryLabel = when (friendshipState) {
                FriendshipState.NONE -> "Send request"
                FriendshipState.OUTGOING_PENDING -> "Sent ✓"
                FriendshipState.INCOMING_PENDING -> "Accept"
                FriendshipState.ACCEPTED -> "Unfriend"
            }
            val primaryEnabled = friendshipState != FriendshipState.OUTGOING_PENDING
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = onViewProfile,
                    modifier = if (compactButtons) Modifier.weight(1f) else Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f))
                ) { Text("View") }
                OutlinedButton(
                    onClick = onPrimaryAction,
                    enabled = primaryEnabled,
                    modifier = Modifier.weight(1f)
                ) { Text(primaryLabel, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                OutlinedButton(onClick = onBlock, modifier = Modifier.weight(1f)) { Text("Block") }
            }
        }
    }
}

