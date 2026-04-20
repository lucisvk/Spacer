package com.example.spacer.profile

import android.net.Uri
import android.widget.Toast
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import coil.compose.rememberAsyncImagePainter
import com.example.spacer.Navigation.AppRoutes
import com.example.spacer.network.SessionPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ProfileScreen(
    navController: NavHostController,
    onOpenEditProfile: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenHostedEvents: () -> Unit,
    onOpenAttendedEvents: () -> Unit,
    onOpenFriends: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { ProfileRepository() }
    val sessionPrefs = remember { SessionPrefs(context) }
    val scrollState = rememberScrollState()

    var isLoading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var fullName by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var avatarUrl by remember { mutableStateOf<String?>(null) }

    var hostedCount by remember { mutableStateOf(0) }
    var attendedCount by remember { mutableStateOf(0) }
    var friendsCount by remember { mutableStateOf(0) }

    fun loadFromCache() {
        val cachedName = sessionPrefs.getProfileName().trim()
        if (username.isBlank() && fullName.isBlank()) {
            fullName = cachedName
            username = cachedName
        }
        avatarUrl = avatarUrl ?: sessionPrefs.getProfileImageUri()
        hostedCount = sessionPrefs.getHostedCount()
        attendedCount = sessionPrefs.getAttendedCount()
        friendsCount = sessionPrefs.getFriendsCount()
    }

    suspend fun loadProfile() {
        isLoading = true
        loadError = null
        val result = withContext(Dispatchers.IO) { repository.load() }
        result
            .onSuccess { snapshot ->
                fullName = snapshot.profile.fullName.orEmpty()
                username = snapshot.profile.username.orEmpty()
                email = snapshot.profile.email.orEmpty()
                avatarUrl = snapshot.profile.avatarUrl

                hostedCount = snapshot.stats.hostedCount
                attendedCount = snapshot.stats.attendedCount
                friendsCount = snapshot.stats.friendsCount
                val label = displayLabelFromProfile(snapshot.profile)
                if (label.isNotBlank()) {
                    sessionPrefs.saveProfileName(label)
                }
                sessionPrefs.saveProfileSnapshot(
                    profileName = label.ifBlank { username.ifBlank { fullName } },
                    aboutMe = snapshot.profile.aboutMe.orEmpty(),
                    profileImageUri = avatarUrl,
                    hostedCount = hostedCount,
                    attendedCount = attendedCount,
                    friendsCount = friendsCount
                )
            }
            .onFailure { e ->
                loadError = e.message ?: "Failed to load profile"
                loadFromCache()
            }
        isLoading = false
    }

    LaunchedEffect(Unit) {
        loadFromCache()
        loadProfile()
    }

    DisposableEffect(navController) {
        val listener = NavController.OnDestinationChangedListener { _, destination, _ ->
            if (destination.route == AppRoutes.Profile) {
                scope.launch { loadProfile() }
            }
        }
        navController.addOnDestinationChangedListener(listener)
        onDispose { navController.removeOnDestinationChangedListener(listener) }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.Top
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Profile",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Surface(
            shape = RoundedCornerShape(26.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val selectedImageUri = avatarUrl

                if (selectedImageUri.isNullOrBlank()) {
                    Spacer(
                        modifier = Modifier
                            .size(92.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                    )
                } else {
                    Image(
                        painter = rememberAsyncImagePainter(model = Uri.parse(selectedImageUri)),
                        contentDescription = "Profile picture",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(92.dp)
                            .clip(CircleShape)
                            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                // Primary headline: signup username (handle), not the optional "Name" field.
                val displayName = when {
                    isLoading -> ""
                    username.trim().isNotEmpty() -> username.trim()
                    fullName.trim().isNotEmpty() -> fullName.trim()
                    email.trim().isNotEmpty() -> email.trim().substringBefore("@")
                    else -> ""
                }
                val accountLabel = email.trim().takeIf { it.isNotEmpty() }?.substringBefore("@")
                // Second line only if they set a different display name (e.g. in Edit profile).
                val subtitle = when {
                    isLoading -> ""
                    username.isBlank() -> ""
                    fullName.isNotBlank() && !fullName.trim().equals(username.trim(), ignoreCase = true) ->
                        fullName.trim()
                    else -> ""
                }
                Text(
                    text = if (isLoading) {
                        "Loading..."
                    } else {
                        displayName.ifBlank { accountLabel ?: "Account" }
                    },
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold)
                )
                if (subtitle.isNotBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ProfileStatCard(
                value = hostedCount.toString(),
                label = "Hosted",
                modifier = Modifier.weight(1f),
                onClick = onOpenHostedEvents
            )
            ProfileStatCard(
                value = attendedCount.toString(),
                label = "Attended",
                modifier = Modifier.weight(1f),
                onClick = onOpenAttendedEvents
            )
            ProfileStatCard(
                value = friendsCount.toString(),
                label = "Friends",
                modifier = Modifier.weight(1f),
                onClick = onOpenFriends
            )
        }

        Spacer(modifier = Modifier.height(14.dp))
        Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                MenuRow("Edit Profile", onClick = onOpenEditProfile)
                MenuRow("Settings", onClick = onOpenSettings)
                MenuRow("Change Password")
                HorizontalDivider(modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp))
                MenuRow("Help & Support")
            }
        }
    }
}

@Composable
private fun ProfileStatCard(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier.clickable { onClick() }
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(vertical = 12.dp)
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
            Text(text = label, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun MenuRow(title: String, onClick: (() -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) Modifier.clickable { onClick() } else Modifier
            )
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = title, style = MaterialTheme.typography.bodyMedium)
        Text(
            text = ">",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
        )
    }
}
