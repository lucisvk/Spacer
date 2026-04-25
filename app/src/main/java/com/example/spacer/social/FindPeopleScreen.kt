package com.example.spacer.social

import android.widget.Toast
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.spacer.profile.ProfileRepository
import com.example.spacer.profile.SearchUserRow
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    val scrollState = rememberScrollState()

    LaunchedEffect(Unit) {
        snapshotFlow { query }
            .debounce(350L)
            .distinctUntilChanged()
            .collectLatest { q ->
                loading = true
                try {
                    val result = withContext(Dispatchers.IO) { repository.searchUsers(q) }
                    result
                        .onSuccess { results = it }
                        .onFailure {
                            Toast.makeText(context, "Couldn't search right now. Please try again.", Toast.LENGTH_LONG).show()
                            results = emptyList()
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
            text = "Find People",
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

        val trimmed = query.trim()
        val queryReady = trimmed.isNotEmpty()

        when {
            !queryReady && !loading -> Text(
                "Type to search profiles by name, username, or email prefix.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
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
                            onAddFriend = {
                                scope.launch {
                                    withContext(Dispatchers.IO) { repository.sendFriendRequest(user.id) }
                                        .onSuccess {
                                            Toast.makeText(context, "Friend request sent", Toast.LENGTH_SHORT).show()
                                        }
                                        .onFailure {
                                            Toast.makeText(context, "Couldn't send request. Try again.", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            onBlock = {
                                scope.launch {
                                    withContext(Dispatchers.IO) { repository.blockUser(user.id) }
                                        .onSuccess {
                                            Toast.makeText(context, "User blocked", Toast.LENGTH_SHORT).show()
                                        }
                                        .onFailure {
                                            Toast.makeText(context, "Couldn't block right now. Try again.", Toast.LENGTH_SHORT).show()
                                        }
                                }
                            },
                            onReport = {
                                scope.launch {
                                    withContext(Dispatchers.IO) {
                                        repository.reportUser(user.id, "Reported from find people")
                                    }.onSuccess {
                                        Toast.makeText(context, "Report sent", Toast.LENGTH_SHORT).show()
                                    }.onFailure {
                                        Toast.makeText(context, "Couldn't send report. Try again.", Toast.LENGTH_SHORT).show()
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
    onViewProfile: () -> Unit,
    onAddFriend: () -> Unit,
    onBlock: () -> Unit,
    onReport: () -> Unit
) {
    Surface(
        shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = user.fullName?.ifBlank { user.username ?: "User" } ?: (user.username ?: "User"),
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                text = "@${user.username ?: "user"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onViewProfile) { Text("View") }
                OutlinedButton(onClick = onAddFriend) { Text("Add") }
                OutlinedButton(onClick = onBlock) { Text("Block") }
                OutlinedButton(onClick = onReport) { Text("Report") }
            }
        }
    }
}

