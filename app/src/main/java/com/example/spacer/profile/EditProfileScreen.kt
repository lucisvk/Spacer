package com.example.spacer.profile

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import com.example.spacer.network.SessionPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun EditProfileScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val sessionPrefs = remember { SessionPrefs(context) }
    val repository = remember { ProfileRepository() }
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    var loading by remember { mutableStateOf(true) }
    var fullName by remember { mutableStateOf("") }
    var aboutMe by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var avatarUrl by remember { mutableStateOf<String?>(null) }

    fun loadFromCache() {
        val cachedName = sessionPrefs.getProfileName().trim()
        if (fullName.isBlank()) fullName = cachedName
        if (aboutMe.isBlank()) aboutMe = sessionPrefs.getAboutMe()
        if (avatarUrl.isNullOrBlank()) avatarUrl = sessionPrefs.getProfileImageUri()
    }

    LaunchedEffect(Unit) {
        loadFromCache()
        loading = true
        withContext(Dispatchers.IO) { repository.load() }
            .onSuccess {
                fullName = it.profile.fullName.orEmpty()
                aboutMe = it.profile.aboutMe.orEmpty()
                username = it.profile.username.orEmpty()
                email = it.profile.email.orEmpty()
                avatarUrl = it.profile.avatarUrl
            }
            .onFailure {
                Toast.makeText(context, it.message ?: "Failed to load profile", Toast.LENGTH_LONG).show()
                loadFromCache()
            }
        loading = false
    }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { selectedUri ->
        avatarUrl = selectedUri?.toString()
        sessionPrefs.saveProfileImageUri(avatarUrl)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        Text(
            text = "Edit Profile",
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
        )
        Spacer(modifier = Modifier.height(16.dp))

        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (avatarUrl.isNullOrBlank()) {
                    Spacer(
                        modifier = Modifier
                            .size(90.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                            .clickable { imagePicker.launch("image/*") }
                    )
                } else {
                    Image(
                        painter = rememberAsyncImagePainter(Uri.parse(avatarUrl)),
                        contentDescription = "Profile image",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(90.dp)
                            .clip(CircleShape)
                            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                            .clickable { imagePicker.launch("image/*") }
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))
                OutlinedTextField(
                    value = fullName,
                    onValueChange = { fullName = it },
                    label = { Text("Full name") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !loading
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = aboutMe,
                    onValueChange = { aboutMe = it },
                    label = { Text("About me") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !loading
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = email,
                    onValueChange = {},
                    label = { Text("Email") },
                    enabled = false,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = username,
                    onValueChange = {},
                    label = { Text("Username") },
                    enabled = false,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))
        Button(
            onClick = {
                scope.launch {
                    withContext(Dispatchers.IO) {
                        repository.updateProfile(
                            fullName = fullName.trim(),
                            aboutMe = aboutMe.trim()
                        )
                    }.onSuccess {
                        val label = fullName.trim().ifBlank {
                            username.trim().ifBlank { email.trim().substringBefore("@") }
                        }
                        if (label.isNotBlank()) {
                            sessionPrefs.saveProfileName(label)
                        }
                        sessionPrefs.saveAboutMe(aboutMe.trim())
                        sessionPrefs.saveProfileImageUri(avatarUrl)
                        Toast.makeText(context, "Profile saved", Toast.LENGTH_SHORT).show()
                        onBack()
                    }.onFailure {
                        Toast.makeText(context, it.message ?: "Failed to save profile", Toast.LENGTH_LONG).show()
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !loading
        ) {
            Text("Save")
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text("Cancel")
        }
    }
}

