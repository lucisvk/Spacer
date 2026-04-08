package com.example.spacer.Navigation

import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import com.example.spacer.R
import com.example.spacer.home.HomeScreen
import com.example.spacer.profile.ProfileScreen
import androidx.compose.ui.unit.dp

object AppRoutes {
    const val Home = "home"
    const val Events = "events"
    const val Create = "create"
    const val Profile = "profile"
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
            composable(AppRoutes.Events) { PlaceholderPage("Events") }
            composable(AppRoutes.Create) { PlaceholderPage("Create Event") }
            composable(AppRoutes.Profile) {
                ProfileScreen(
                    onLogout = onLogout,
                    isDarkTheme = isDarkTheme,
                    onToggleTheme = onToggleTheme
                )
            }
        }
    }
}

@Composable
private fun SpacerBottomBar(navController: NavHostController) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    NavigationBar(
        modifier = Modifier.height(24.dp),
        containerColor = androidx.compose.material3.MaterialTheme.colorScheme.primaryContainer,
        contentColor = androidx.compose.material3.MaterialTheme.colorScheme.onPrimaryContainer
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
                            androidx.compose.material3.MaterialTheme.colorScheme.primary
                        } else {
                            androidx.compose.material3.MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                        }
                    )
                },
                label = {}
            )
        }
    }
}

@Composable
private fun PlaceholderPage(title: String) {
    Text(text = title)
}

