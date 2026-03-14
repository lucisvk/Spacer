package com.example.spacer.profile

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import com.example.spacer.network.SessionPrefs

@Composable
fun ProfileScreen(
    onLogout: () -> Unit,
    onToggleTheme: () -> Unit,
    isDarkTheme: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val sessionPrefs = remember { SessionPrefs(context) }

    // Profile values are loaded from local session storage so they persist between launches.
    var profileName by remember { mutableStateOf("") }
    var aboutMe by remember { mutableStateOf("") }
    var profileImageUri by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        profileName = sessionPrefs.getProfileName()
        aboutMe = sessionPrefs.getAboutMe()
        profileImageUri = sessionPrefs.getProfileImageUri()
    }

    // Opens the image picker and stores the selected URI for future app launches.
    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { selectedUri ->
        val uri = selectedUri ?: return@rememberLauncherForActivityResult

        // Local persistence fallback: store the picked image URI in session preferences.
        // Supabase Storage upload is temporarily disabled until SDK API compatibility is finalized.
        profileImageUri = uri.toString()
        sessionPrefs.saveProfileImageUri(profileImageUri)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        verticalArrangement = Arrangement.Top
    ) {
        // Header row with theme toggle.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Profile",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
            )
            OutlinedButton(
                onClick = onToggleTheme,
                modifier = Modifier.padding(0.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(text = if (isDarkTheme) "Light" else "Dark")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val selectedImageUri = profileImageUri

                if (selectedImageUri.isNullOrBlank()) {
                    Spacer(
                        modifier = Modifier
                            .size(82.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                            .clickable { imagePicker.launch("image/*") }
                    )
                } else {
                    Image(
                        painter = rememberAsyncImagePainter(model = Uri.parse(selectedImageUri)),
                        contentDescription = "Profile picture",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(82.dp)
                            .clip(CircleShape)
                            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                            .clickable { imagePicker.launch("image/*") }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = profileName.ifBlank { "Your Name" },
                    style = MaterialTheme.typography.titleMedium
                )

                Spacer(modifier = Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    ProfileStat(sessionPrefs.getHostedCount().toString(), "Hosted")
                    ProfileStat(sessionPrefs.getAttendedCount().toString(), "Attended")
                    ProfileStat(sessionPrefs.getFriendsCount().toString(), "Friends")
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "About Me",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = aboutMe,
                    onValueChange = {
                        aboutMe = it
                        sessionPrefs.saveAboutMe(it)
                    },
                    placeholder = {
                        Text(
                            text = "Tell people about yourself...",
                            color = Color.Gray
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
        Button(
            onClick = onLogout,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Log out")
        }
    }
}

@Composable
private fun ProfileStat(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
        )
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
    }
}
