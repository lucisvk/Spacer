package com.example.spacer.Navigation

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.spacer.R
import com.example.spacer.home.HomeScreen
import com.example.spacer.profile.EditProfileScreen
import com.example.spacer.profile.ProfileScreen
import com.example.spacer.profile.AttendedEventsScreen
import com.example.spacer.network.AuthRepository
import com.example.spacer.network.SessionPrefs
import com.example.spacer.profile.FriendsScreen
import com.example.spacer.profile.HostedEventsScreen
import com.example.spacer.profile.ProfileRepository
import com.example.spacer.profile.SettingsScreen
import com.example.spacer.profile.displayLabelFromProfile
import com.example.spacer.events.EventsHubScreen
import com.example.spacer.events.HostEventDetailScreen
import com.example.spacer.events.InviteEventScreen
import com.example.spacer.location.CreateEventDetailsScreen
import com.example.spacer.location.CreateEventPlaceScreen
import com.example.spacer.location.PlaceDetailScreen
import com.example.spacer.location.PlaceUi
import com.example.spacer.location.toPlaceUi
import com.example.spacer.ui.theme.SpacerPurplePrimary
import com.example.spacer.ui.theme.SpacerPurpleSurface
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object AppRoutes {
    const val Home = "home"
    const val Events = "events"
    const val Create = "create"
    const val Profile = "profile"
    const val EditProfile = "edit_profile"
    const val Settings = "settings"
    const val HostedEvents = "hosted_events"
    const val AttendedEvents = "attended_events"
    const val Friends = "friends"
}

private data class BottomNavItem(
    val label: String,
    val route: String,
    val iconRes: Int
)

private val bottomNavItems = listOf(
    BottomNavItem(label = "Home", route = AppRoutes.Home, iconRes = R.drawable.home_button),
    BottomNavItem(label = "Events", route = AppRoutes.Events, iconRes = R.drawable.calendar_button),
    BottomNavItem(label = "Create", route = AppRoutes.Create, iconRes = R.drawable.create_event_button),
    BottomNavItem(label = "Profile", route = AppRoutes.Profile, iconRes = R.drawable.profile_icon)
)

@Composable
fun SpacerAppScaffold(
    onLogout: () -> Unit,
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
    modifier: Modifier = Modifier
) {
    val navController = androidx.navigation.compose.rememberNavController()
    var navBarVisible by remember { mutableStateOf(false) }
    val appContext = LocalContext.current
    val sessionPrefs = remember { SessionPrefs(appContext) }
    val authRepository = remember { AuthRepository() }
    val profileRepository = remember { ProfileRepository() }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            authRepository.ensureProfileAfterOAuthSignIn()
            profileRepository.load().onSuccess { snap ->
                val label = displayLabelFromProfile(snap.profile)
                if (label.isNotBlank()) {
                    sessionPrefs.saveProfileName(label)
                }
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            bottomBar = {}
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = AppRoutes.Home,
                modifier = Modifier.padding(innerPadding)
            ) {
            composable(AppRoutes.Home) {
                HomeScreen(
                    onViewAllEvents = {
                        navController.navigate(AppRoutes.Events) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
            composable(AppRoutes.Events) {
                val innerNav = rememberNavController()
                NavHost(
                    navController = innerNav,
                    startDestination = "events_hub",
                    modifier = Modifier.fillMaxSize()
                ) {
                    composable("events_hub") {
                        EventsHubScreen(
                            innerEventsNav = innerNav,
                            outerNav = navController,
                            onOpenInvite = { innerNav.navigate("invite/${it}") },
                            onOpenHostEvent = { innerNav.navigate("host/${it}") }
                        )
                    }
                    composable(
                        route = "invite/{eventId}",
                        arguments = listOf(
                            navArgument("eventId") { type = NavType.StringType }
                        )
                    ) { entry ->
                        val id = entry.arguments?.getString("eventId").orEmpty()
                        InviteEventScreen(
                            eventId = id,
                            onBack = { innerNav.popBackStack() }
                        )
                    }
                    composable(
                        route = "host/{eventId}",
                        arguments = listOf(
                            navArgument("eventId") { type = NavType.StringType }
                        )
                    ) { entry ->
                        val id = entry.arguments?.getString("eventId").orEmpty()
                        HostEventDetailScreen(
                            eventId = id,
                            onBack = { innerNav.popBackStack() }
                        )
                    }
                }
            }
            composable(AppRoutes.Create) {
                val innerNav = rememberNavController()
                var selectedVenue by remember { mutableStateOf<PlaceUi?>(null) }
                var draftEventTitle by remember { mutableStateOf("") }
                NavHost(
                    navController = innerNav,
                    startDestination = "create_place",
                    modifier = Modifier.fillMaxSize()
                ) {
                    composable("create_place") {
                        CreateEventPlaceScreen(
                            selectedPlace = selectedVenue,
                            onSelectedPlaceChange = { selectedVenue = it },
                            eventTitle = draftEventTitle,
                            onEventTitleChange = { draftEventTitle = it },
                            onOpenPlaceDetail = { place ->
                                innerNav.navigate("place_detail/${Uri.encode(place.id, "UTF-8")}")
                            },
                            onContinue = {
                                if (selectedVenue != null && draftEventTitle.isNotBlank()) {
                                    innerNav.navigate("create_details") { launchSingleTop = true }
                                }
                            },
                            onUsePlaceForEvent = {
                                innerNav.navigate("create_details") { launchSingleTop = true }
                            }
                        )
                    }
                    composable("create_details") {
                        val place = selectedVenue
                        if (place == null) {
                            LaunchedEffect(Unit) {
                                innerNav.popBackStack()
                            }
                            Box(modifier = Modifier.fillMaxSize())
                        } else {
                            CreateEventDetailsScreen(
                                place = place,
                                eventTitle = draftEventTitle,
                                onEventTitleChange = { draftEventTitle = it },
                                onBack = { innerNav.popBackStack() },
                                onPublished = {
                                    draftEventTitle = ""
                                    selectedVenue = null
                                    innerNav.popBackStack("create_place", inclusive = false)
                                    navController.navigate(AppRoutes.Events) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            )
                        }
                    }
                    composable(
                        route = "place_detail/{placeId}",
                        arguments = listOf(
                            navArgument("placeId") { type = NavType.StringType }
                        )
                    ) { entry ->
                        val enc = entry.arguments?.getString("placeId").orEmpty()
                        val placeId = Uri.decode(enc)
                        PlaceDetailScreen(
                            placeId = placeId,
                            onBack = { innerNav.popBackStack() },
                            onUseForEvent = { detail ->
                                selectedVenue = detail.toPlaceUi()
                                innerNav.popBackStack()
                                innerNav.navigate("create_details") { launchSingleTop = true }
                            }
                        )
                    }
                }
            }
            composable(AppRoutes.Profile) {
                ProfileScreen(
                    navController = navController,
                    onOpenEditProfile = { navController.navigate(AppRoutes.EditProfile) },
                    onOpenSettings = { navController.navigate(AppRoutes.Settings) },
                    onOpenHostedEvents = { navController.navigate(AppRoutes.HostedEvents) },
                    onOpenAttendedEvents = { navController.navigate(AppRoutes.AttendedEvents) },
                    onOpenFriends = { navController.navigate(AppRoutes.Friends) }
                )
            }
            composable(AppRoutes.EditProfile) {
                EditProfileScreen(
                    onBack = { navController.popBackStack() }
                )
            }
            composable(AppRoutes.Settings) {
                SettingsScreen(
                    isDarkTheme = isDarkTheme,
                    onToggleTheme = onToggleTheme,
                    onLogout = onLogout,
                    onBack = { navController.popBackStack() }
                )
            }
            composable(AppRoutes.HostedEvents) {
                HostedEventsScreen(onBack = { navController.popBackStack() })
            }
            composable(AppRoutes.AttendedEvents) {
                AttendedEventsScreen(onBack = { navController.popBackStack() })
            }
            composable(AppRoutes.Friends) {
                FriendsScreen(onBack = { navController.popBackStack() })
            }
            }
        }

        FloatingActionButton(
            onClick = { navBarVisible = !navBarVisible },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(
                    end = 18.dp,
                    bottom = if (navBarVisible) 80.dp else 16.dp
                ),
            containerColor = SpacerPurpleSurface.copy(alpha = 0.92f),
            contentColor = SpacerPurplePrimary
        ) {
            Icon(
                imageVector = if (navBarVisible) Icons.Filled.Close else Icons.Filled.Menu,
                contentDescription = if (navBarVisible) "Hide navigation" else "Show navigation"
            )
        }

        AnimatedVisibility(
            visible = navBarVisible,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
        ) {
            SpacerBottomBar(
                navController = navController,
                onNavigate = { navBarVisible = false }
            )
        }
    }
}

@Composable
private fun SpacerBottomBar(
    navController: NavHostController,
    onNavigate: () -> Unit
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    // Floating “glass” bar: translucent fill + light edge + rounded pill (Apple-style, no backdrop blur on all APIs).
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 18.dp, vertical = 10.dp)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp),
            shape = RoundedCornerShape(30.dp),
            color = SpacerPurpleSurface.copy(alpha = 0.52f),
            tonalElevation = 0.dp,
            shadowElevation = 10.dp,
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f))
        ) {
            NavigationBar(
                modifier = Modifier.fillMaxWidth(),
                containerColor = Color.Transparent,
                contentColor = Color.White.copy(alpha = 0.92f),
                tonalElevation = 0.dp
            ) {
                bottomNavItems.forEach { item ->
                    val selected = currentDestination
                        ?.hierarchy
                        ?.any { it.route == item.route } == true

                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            onNavigate()
                            navController.navigate(item.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = item.route != AppRoutes.Create
                            }
                        },
                        icon = {
                            Icon(
                                painter = painterResource(id = item.iconRes),
                                contentDescription = item.label,
                                tint = if (selected) {
                                    SpacerPurplePrimary
                                } else {
                                    Color.White.copy(alpha = 0.55f)
                                }
                            )
                        },
                        label = {},
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = SpacerPurplePrimary,
                            unselectedIconColor = Color.White.copy(alpha = 0.55f),
                            selectedTextColor = SpacerPurplePrimary,
                            unselectedTextColor = Color.White.copy(alpha = 0.55f),
                            indicatorColor = SpacerPurplePrimary.copy(alpha = 0.2f)
                        )
                    )
                }
            }
        }
    }
}

