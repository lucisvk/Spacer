package com.example.spacer.events

import android.widget.Toast
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.spacer.network.SupabaseManager
import com.example.spacer.profile.ProfileRepository
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun EventChatScreen(
    eventId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = remember { EventRepository() }
    var messages by remember { mutableStateOf<List<EventChatMessageUi>>(emptyList()) }
    var presence by remember { mutableStateOf<List<ChatPresenceUi>>(emptyList()) }
    var draft by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf("all_members") }
    var currentUserId by remember { mutableStateOf<String?>(null) }
    var currentUserName by remember { mutableStateOf("Event chat") }
    var currentUserAvatarUrl by remember { mutableStateOf<String?>(null) }
    var showedRealtimeFallbackToast by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    LaunchedEffect(eventId) {
        currentUserId = SupabaseManager.client.auth.currentUserOrNull()?.id
        currentUserId?.let { me ->
            withContext(Dispatchers.IO) { ProfileRepository().getPublicProfile(me) }
                .onSuccess {
                    currentUserName = it.fullName.ifBlank { "Event chat" }
                    currentUserAvatarUrl = it.avatarUrl
                }
        }
        mode = withContext(Dispatchers.IO) { repo.getEventChatMode(eventId) }.getOrDefault("all_members")
        repo.subscribeEventChatMessages(eventId).collect { result ->
            result.onSuccess { messages = it }
            result.onFailure {
                if (!showedRealtimeFallbackToast) {
                    showedRealtimeFallbackToast = true
                    Toast.makeText(context, "Realtime unavailable, using backup refresh.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    LaunchedEffect("event-chat-poll-$eventId") {
        while (true) {
            withContext(Dispatchers.IO) { repo.listEventChatMessages(eventId) }
                .onSuccess { messages = it }
            delay(1500L)
        }
    }
    LaunchedEffect("presence-$eventId") {
        repo.subscribeEventChatPresence(eventId).collect { result ->
            result.onSuccess { presence = it }
        }
    }
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ChatAvatar(name = currentUserName, avatarUrl = currentUserAvatarUrl, contentDescription = "Profile avatar")
            Column {
                Text(currentUserName, style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold))
                Text("Event chat", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
            }
        }
        Spacer(Modifier.height(10.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
        )
        Spacer(Modifier.height(8.dp))

        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
            modifier = Modifier.fillMaxSize()
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Surface(
                    shape = RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            when (mode) {
                                "host_cohosts_only" -> "Only host/co-host can send messages"
                                "disabled" -> "Chat is disabled for this event"
                                else -> "All event members can chat"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
                        )
                        if (presence.isNotEmpty()) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "In chat: " + presence.joinToString(", ") {
                                    when (it.role) {
                                        "host" -> "${it.displayName} (Host)"
                                        "cohost" -> "${it.displayName} (Co-host)"
                                        else -> it.displayName
                                    }
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(messages, key = { it.id }) { msg ->
                        AnimatedMessageRow {
                            val mine = currentUserId != null && msg.senderId == currentUserId
                            ChatMessageBubble(
                                mine = mine,
                                senderName = msg.senderName,
                                body = msg.body
                            )
                        }
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        label = { Text("Message...") },
                        modifier = Modifier.weight(1f),
                        maxLines = 4
                    )
                    Button(
                        onClick = {
                            scope.launch {
                                withContext(Dispatchers.IO) { repo.sendEventChatMessage(eventId, draft) }
                                    .onSuccess {
                                        draft = ""
                                        withContext(Dispatchers.IO) { repo.listEventChatMessages(eventId) }
                                            .onSuccess { messages = it }
                                        if (messages.isNotEmpty()) {
                                            listState.animateScrollToItem(messages.lastIndex)
                                        }
                                    }
                                    .onFailure {
                                        Toast.makeText(context, it.message ?: "Couldn't send message.", Toast.LENGTH_SHORT).show()
                                    }
                            }
                        },
                        modifier = Modifier.height(54.dp)
                    ) { Text("Send") }
                }
            }
        }
    }
}
