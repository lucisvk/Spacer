package com.example.spacer.profile

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.spacer.network.SessionPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
    onLogout: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val sessionPrefs = remember { SessionPrefs(context) }
    val repository = remember { ProfileRepository() }
    val scope = rememberCoroutineScope()
    var showDeleteDialog by remember { mutableStateOf(false) }
    var deletionReason by remember { mutableStateOf("") }
    val scrollState = rememberScrollState()
    var calendarSharingEnabled by remember {
        mutableStateOf(sessionPrefs.isCalendarAvailabilitySharingEnabled())
    }
    var calendarConflictNotify by remember {
        mutableStateOf(sessionPrefs.isCalendarConflictNotificationsEnabled())
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.Top
    ) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
        )
        Spacer(modifier = Modifier.height(14.dp))

        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "Appearance",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                androidx.compose.foundation.layout.Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(if (isDarkTheme) "Dark mode enabled" else "Light mode enabled")
                    Switch(checked = isDarkTheme, onCheckedChange = { onToggleTheme() })
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "Calendar & availability",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Let hosts see when you’re generally free on invites, and optionally warn you if a time clashes with your calendar.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f, fill = true).padding(end = 8.dp)) {
                        Text(
                            text = "Share availability with hosts",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = if (calendarSharingEnabled) "Connected — hosts can see your saved invite availability." else "Disconnected — availability won’t be saved for invites.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    Switch(
                        checked = calendarSharingEnabled,
                        onCheckedChange = { on ->
                            calendarSharingEnabled = on
                            sessionPrefs.setCalendarAvailabilitySharingEnabled(on)
                            if (!on) {
                                calendarConflictNotify = false
                                sessionPrefs.setCalendarConflictNotificationsEnabled(false)
                            }
                        }
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f, fill = true).padding(end = 8.dp)) {
                        Text(
                            text = "Conflict notifications",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "Coming soon: alert when an invite overlaps busy calendar time.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    Switch(
                        checked = calendarConflictNotify,
                        enabled = calendarSharingEnabled,
                        onCheckedChange = { on ->
                            calendarConflictNotify = on
                            sessionPrefs.setCalendarConflictNotificationsEnabled(on)
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))
        Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text("Back to profile")
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(onClick = onLogout, modifier = Modifier.fillMaxWidth()) {
            Text("Log out")
        }
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = { showDeleteDialog = true },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
        ) {
            Text("Delete account")
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete account") },
            text = {
                Column {
                    Text("This creates an account deletion request and logs you out.")
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = deletionReason,
                        onValueChange = { deletionReason = it },
                        label = { Text("Reason (optional)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            val result = withContext(Dispatchers.IO) {
                                repository.requestAccountDeletion(deletionReason)
                            }
                            result.onSuccess {
                                Toast.makeText(context, "Deletion request submitted", Toast.LENGTH_LONG).show()
                                showDeleteDialog = false
                                onLogout()
                            }.onFailure {
                                Toast.makeText(
                                    context,
                                    it.message ?: "Failed to submit deletion request",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Submit") }
            },
            dismissButton = {
                OutlinedButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
            }
        )
    }
}

