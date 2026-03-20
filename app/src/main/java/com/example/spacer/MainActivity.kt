package com.example.spacer

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.spacer.Navigation.SpacerAppScaffold
import com.example.spacer.network.AuthRepository
import com.example.spacer.network.LoginRequest
import com.example.spacer.network.SignupRequest
import com.example.spacer.network.SupabaseManager
import com.example.spacer.ui.theme.SpacerTheme
import io.github.jan.supabase.auth.handleDeeplinks
import io.github.jan.supabase.auth.providers.Discord
import io.github.jan.supabase.auth.providers.Github
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private object Routes {
    const val Splash = "splash"
    const val Login = "login"
    const val CreateAccount = "create_account"
    const val ForgotPassword = "forgot_password"
    const val App = "app"
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Required for OAuth + OTP links on Android when using deeplinks.
        SupabaseManager.client.handleDeeplinks(intent)

        setContent {
            var isDarkTheme by remember { mutableStateOf(true) }

            SpacerTheme(darkTheme = isDarkTheme) {
                val navController = rememberNavController()

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    SpacerNavHost(
                        navController = navController,
                        isDarkTheme = isDarkTheme,
                        onToggleTheme = { isDarkTheme = !isDarkTheme },
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // Required when OAuth callback returns while activity already exists.
        SupabaseManager.client.handleDeeplinks(intent)
    }

    override fun onResume() {
        super.onResume()
        // Ensures PKCE / OAuth return is processed if the intent was delivered here.
        SupabaseManager.client.handleDeeplinks(intent)
    }
}

@Composable
private fun SpacerNavHost(
    navController: NavHostController,
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
    modifier: Modifier = Modifier
) {
    val authRepository = remember { AuthRepository() }

    NavHost(
        navController = navController,
        startDestination = Routes.Splash,
        modifier = modifier
    ) {
        composable(Routes.Splash) {
            val sessionStatus by SupabaseManager.client.auth.sessionStatus.collectAsState()

            SplashScreen(
                sessionStatus = sessionStatus,
                onRouteReady = { nextRoute ->
                    navController.navigate(nextRoute) {
                        popUpTo(Routes.Splash) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.Login) {
            LoginScreen(
                onCreateAccountClick = { navController.navigate(Routes.CreateAccount) },
                onForgotPasswordClick = { navController.navigate(Routes.ForgotPassword) },
                isDarkTheme = isDarkTheme,
                onToggleTheme = onToggleTheme,
                onLoginSuccess = {
                    navController.navigate(Routes.App) {
                        popUpTo(Routes.Login) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.CreateAccount) {
            CreateAccountScreen(onBackToLoginClick = { navController.popBackStack() })
        }

        composable(Routes.ForgotPassword) {
            ForgotPasswordScreen(onBackToLoginClick = { navController.popBackStack() })
        }

        composable(Routes.App) {
            SpacerAppScaffold(
                onLogout = {
                    CoroutineScope(Dispatchers.IO).launch {
                        authRepository.logout()
                        withContext(Dispatchers.Main) {
                            navController.navigate(Routes.Login) {
                                popUpTo(Routes.App) { inclusive = true }
                            }
                        }
                    }
                },
                isDarkTheme = isDarkTheme,
                onToggleTheme = onToggleTheme
            )
        }
    }
}

@Composable
private fun SplashScreen(
    sessionStatus: SessionStatus,
    onRouteReady: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var startAnimation by remember { mutableStateOf(false) }
    var animationFinished by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0.8f,
        animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing),
        label = "splashScale"
    )

    val alpha by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0f,
        animationSpec = tween(durationMillis = 800),
        label = "splashAlpha"
    )

    LaunchedEffect(Unit) {
        startAnimation = true
        delay(1600)
        animationFinished = true
    }

    LaunchedEffect(animationFinished, sessionStatus) {
        if (!animationFinished) return@LaunchedEffect

        val nextRoute = when (sessionStatus) {
            is SessionStatus.Authenticated -> Routes.App
            is SessionStatus.NotAuthenticated -> Routes.Login
            else -> null
        }

        if (nextRoute != null) onRouteReady(nextRoute)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            painter = painterResource(id = R.drawable.spacer_logo),
            contentDescription = "Spacer logo",
            tint = androidx.compose.ui.graphics.Color.Unspecified,
            modifier = Modifier.scale(scale).alpha(alpha)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Spacer",
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 2.sp
            ),
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.9f),
            modifier = Modifier.alpha(alpha)
        )
    }
}

@Composable
private fun LoginScreen(
    onCreateAccountClick: () -> Unit,
    onForgotPasswordClick: () -> Unit,
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
    onLoginSuccess: () -> Unit,
    modifier: Modifier = Modifier
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var oauthLoading by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val authRepository = remember { AuthRepository() }
    val scope = rememberCoroutineScope()
    val sessionStatus by SupabaseManager.client.auth.sessionStatus.collectAsState()

    LaunchedEffect(sessionStatus) {
        if (sessionStatus is SessionStatus.Authenticated) {
            withContext(Dispatchers.IO) {
                authRepository.ensureProfileAfterOAuthSignIn()
            }
            onLoginSuccess()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 24.dp, vertical = 32.dp)
    ) {
        Row(
            modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Spacer",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.5.sp
                )
            )
            Text(
                text = if (isDarkTheme) "Dark" else "Light",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                modifier = Modifier.clickable { onToggleTheme() }.padding(4.dp)
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .background(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(24.dp)
                )
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 28.dp)
        ) {
            Text(
                text = "Welcome back",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.5.sp
                )
            )
            Text(
                text = "Log in to continue",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                modifier = Modifier.padding(top = 4.dp, bottom = 24.dp)
            )

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )

            Text(
                text = "Forgot password?",
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.End,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 14.dp)
                    .clickable { onForgotPasswordClick() }
            )

            Button(
                onClick = {
                    CoroutineScope(Dispatchers.IO).launch {
                        val result = authRepository.login(
                            LoginRequest(email = email.trim(), password = password)
                        )
                        withContext(Dispatchers.Main) {
                            result
                                .onSuccess {
                                    Toast.makeText(
                                        context,
                                        "Login successful",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                                .onFailure { error ->
                                    Toast.makeText(
                                        context,
                                        "Login failed: ${error.message}",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(16.dp),
                enabled = !oauthLoading
            ) {
                Text("Log In")
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "or continue with",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            )

            // OAuth provider buttons are kept in place for third-party sign-in as this will be
            // handled through supabase.
            OutlinedButton(
                onClick = {
                    scope.launch {
                        oauthLoading = true
                        try {
                            val result = withContext(Dispatchers.IO) {
                                authRepository.signInWithOAuth(Google)
                            }
                            result.onFailure { e ->
                                Toast.makeText(
                                    context,
                                    e.message ?: "Google sign-in failed",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        } finally {
                            oauthLoading = false
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(14.dp),
                enabled = !oauthLoading
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.google__g__logo),
                    contentDescription = "Google",
                    tint = androidx.compose.ui.graphics.Color.Unspecified
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text("Continue with Google")
            }

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedButton(
                onClick = {
                    scope.launch {
                        oauthLoading = true
                        try {
                            val result = withContext(Dispatchers.IO) {
                                authRepository.signInWithOAuth(Discord)
                            }
                            result.onFailure { e ->
                                Toast.makeText(
                                    context,
                                    e.message ?: "Discord sign-in failed",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        } finally {
                            oauthLoading = false
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(14.dp),
                enabled = !oauthLoading
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.discord_black_icon),
                    contentDescription = "Discord",
                    tint = androidx.compose.ui.graphics.Color.Unspecified
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text("Continue with Discord")
            }

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedButton(
                onClick = {
                    scope.launch {
                        oauthLoading = true
                        try {
                            val result = withContext(Dispatchers.IO) {
                                authRepository.signInWithOAuth(Github)
                            }
                            result.onFailure { e ->
                                Toast.makeText(
                                    context,
                                    e.message ?: "GitHub sign-in failed",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        } finally {
                            oauthLoading = false
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(14.dp),
                enabled = !oauthLoading
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.octicons_mark_github),
                    contentDescription = "GitHub",
                    tint = androidx.compose.ui.graphics.Color.Unspecified
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text("Continue with GitHub")
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "New here? Create an account",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().clickable { onCreateAccountClick() }
            )
        }
    }
}

@Composable
private fun CreateAccountScreen(
    onBackToLoginClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var name by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var phoneNumber by remember { mutableStateOf("") }
    var dateOfBirth by remember { mutableStateOf("") }
    var allowUpdates by remember { mutableStateOf(true) }

    val scrollState = rememberScrollState()
    val context = LocalContext.current
    val authRepository = remember { AuthRepository() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
            .padding(horizontal = 24.dp, vertical = 32.dp)
            .verticalScroll(scrollState)
    ) {
        Text(
            text = "Create your space",
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold)
        )
        Text(
            text = "We’ll keep track of your events and invites.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            modifier = Modifier.padding(top = 4.dp, bottom = 24.dp)
        )

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            label = { Text("Username") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = confirmPassword,
            onValueChange = { confirmPassword = it },
            label = { Text("Confirm password") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = phoneNumber,
            onValueChange = { phoneNumber = it },
            label = { Text("Phone number") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = dateOfBirth,
            onValueChange = { dateOfBirth = it },
            label = { Text("Date of birth (MM/DD/YYYY)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = allowUpdates, onCheckedChange = { allowUpdates = it })
            Text(
                text = "I’m okay with Spacer emailing or texting me updates.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                modifier = Modifier.padding(start = 6.dp)
            )
        }

        Spacer(modifier = Modifier.height(10.dp))
        Button(
            onClick = {
                if (password != confirmPassword) {
                    Toast.makeText(context, "Passwords do not match", Toast.LENGTH_SHORT).show()
                    return@Button
                }

                CoroutineScope(Dispatchers.IO).launch {
                    val result = authRepository.signup(
                        SignupRequest(
                            username = username.trim(),
                            email = email.trim(),
                            password = password,
                            name = name.trim().ifBlank { null },
                            phoneNumber = phoneNumber.trim().ifBlank { null },
                            dateOfBirth = dateOfBirth.trim().ifBlank { null },
                            allowUpdates = allowUpdates
                        )
                    )

                    withContext(Dispatchers.Main) {
                        result
                            .onSuccess {
                                Toast.makeText(
                                    context,
                                    "Signup success",
                                    Toast.LENGTH_SHORT
                                ).show()
                                onBackToLoginClick()
                            }
                            .onFailure { error ->
                                Toast.makeText(
                                    context,
                                    "ERROR: ${error.message}",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text("Sign up")
        }

        Spacer(modifier = Modifier.height(12.dp))
        OutlinedButton(
            onClick = onBackToLoginClick,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text("Back to login")
        }
    }
}

@Composable
private fun ForgotPasswordScreen(
    onBackToLoginClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var email by remember { mutableStateOf("") }
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 32.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.Top
    ) {
        Column {
            Text(
                text = "Reset password",
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold)
            )
            Text(
                text = "We’ll email you a link so you can set a new password.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                modifier = Modifier.padding(top = 4.dp, bottom = 24.dp)
            )
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = {
                Toast.makeText(
                    context,
                    "Forgot password endpoint not added yet",
                    Toast.LENGTH_SHORT
                ).show()
            },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text("Send reset link")
        }
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedButton(
            onClick = onBackToLoginClick,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text("Back to login")
        }
    }
}
