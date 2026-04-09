package com.example.spacer.Navigation

import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
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
import com.example.spacer.profile.FriendsScreen
import com.example.spacer.profile.HostedEventsScreen
import com.example.spacer.profile.SettingsScreen
import com.example.spacer.location.CreateEventScreen
import com.example.spacer.location.PlaceDetailScreen
import com.example.spacer.location.PlaceUi
import com.example.spacer.location.toPlaceUi
import com.example.spacer.social.FindPeopleScreen
import com.example.spacer.ui.theme.SpacerPurplePrimary
import com.example.spacer.ui.theme.SpacerPurpleSurface
import androidx.compose.ui.unit.dp

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

    Scaffold(
        modifier = modifier,
        bottomBar = { SpacerBottomBar(navController = navController) }
    ) { innerPadding ->
        val layoutDirection = LocalLayoutDirection.current
        NavHost(
            navController = navController,
            startDestination = AppRoutes.Home,
            // Keep bottom content tight so the bottom nav sits closer to the screen edge as i find
            // better ways to make this more convenient as kotlin does no have many options on automatic sizeing
            modifier = Modifier.padding(
                start = innerPadding.calculateStartPadding(layoutDirection),
                top = innerPadding.calculateTopPadding(),
                end = innerPadding.calculateEndPadding(layoutDirection),
                bottom = 0.dp
            )
        ) {
            composable(AppRoutes.Home) { HomeScreen() }
            composable(AppRoutes.Events) { FindPeopleScreen() }
            composable(AppRoutes.Create) {
                val innerNav = rememberNavController()
                var selectedVenue by remember { mutableStateOf<PlaceUi?>(null) }
                NavHost(
                    navController = innerNav,
                    startDestination = "create_root",
                    modifier = Modifier.fillMaxSize()
                ) {
                    composable("create_root") {
                        CreateEventScreen(
                            selectedPlace = selectedVenue,
                            onSelectedPlaceChange = { selectedVenue = it },
                            onOpenPlaceDetail = { place ->
                                innerNav.navigate("place_detail/${Uri.encode(place.id, "UTF-8")}")
                            }
                        )
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
                            }
                        )
                    }
                }
            }
            composable(AppRoutes.Profile) {
                ProfileScreen(
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
}

@Composable
private fun SpacerBottomBar(navController: NavHostController) {
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
                            navController.navigate(item.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
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

