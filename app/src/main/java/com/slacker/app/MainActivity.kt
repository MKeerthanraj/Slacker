package com.slacker.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.slacker.app.notifications.NotificationScheduler
import com.slacker.app.ui.board.CaseBoardScreen
import com.slacker.app.ui.board.TaskBoardScreen
import com.slacker.app.ui.quickadd.QuickAddScreen
import com.slacker.app.ui.settings.SettingsScreen
import com.slacker.app.viewmodel.AppViewModel

class MainActivity : ComponentActivity() {

    private val requestNotifPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* no-op: user's choice either way */ }

    // Compose-ui hover bug (issuetracker b/341828232): stylus/hover pointers can
    // leave a stale ACTION_HOVER_EXIT and crash mid-scroll. Fixed in compose-ui
    // 1.7, kept as a guard so a regression can never take the whole app down.
    override fun dispatchGenericMotionEvent(ev: MotionEvent?): Boolean =
        try {
            super.dispatchGenericMotionEvent(ev)
        } catch (e: IllegalStateException) {
            true
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestNotifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        NotificationScheduler.schedule(this)

        setContent {
            val prefs = remember { getSharedPreferences("slacker_settings", MODE_PRIVATE) }
            var darkMode by remember { mutableStateOf(prefs.getBoolean("dark_mode", false)) }
            SlackerTheme(darkTheme = darkMode) {
                AppRoot(
                    darkMode = darkMode,
                    onToggleTheme = {
                        darkMode = !darkMode
                        prefs.edit().putBoolean("dark_mode", darkMode).apply()
                    }
                )
            }
        }
    }
}

private sealed class Screen(val route: String, val label: String) {
    object Tasks : Screen("tasks", "Tasks")
    object Cases : Screen("cases", "Cases")
    object QuickAdd : Screen("quickadd", "Add")
    object Settings : Screen("settings", "Settings")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppRoot(darkMode: Boolean, onToggleTheme: () -> Unit) {
    val navController = rememberNavController()
    val viewModel: AppViewModel = androidx.lifecycle.viewmodel.compose.viewModel()

    val items = listOf(Screen.Tasks, Screen.Cases, Screen.QuickAdd, Screen.Settings)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Slacker") },
                actions = {
                    IconButton(onClick = onToggleTheme) {
                        Icon(
                            if (darkMode) Icons.Filled.LightMode else Icons.Filled.DarkMode,
                            contentDescription = if (darkMode) "Switch to light mode" else "Switch to dark mode"
                        )
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination

                items.forEach { screen ->
                    val icon = when (screen) {
                        Screen.Tasks -> Icons.AutoMirrored.Filled.List
                        Screen.Cases -> Icons.Filled.Warning
                        Screen.QuickAdd -> Icons.Filled.Add
                        Screen.Settings -> Icons.Filled.Settings
                    }
                    NavigationBarItem(
                        icon = { Icon(icon, contentDescription = screen.label) },
                        label = { Text(screen.label) },
                        selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                        onClick = {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Tasks.route,
            modifier = Modifier.padding(padding)
        ) {
            composable(Screen.Tasks.route) { TaskBoardScreen(viewModel) }
            composable(Screen.Cases.route) { CaseBoardScreen(viewModel) }
            composable(Screen.QuickAdd.route) { QuickAddScreen(viewModel) }
            composable(Screen.Settings.route) { SettingsScreen(viewModel) }
        }
    }
}

@Composable
private fun SlackerTheme(darkTheme: Boolean, content: @Composable () -> Unit) {
    val lightColors = lightColorScheme(
        primary = androidx.compose.ui.graphics.Color(0xFF1F5FBF),
        onPrimary = androidx.compose.ui.graphics.Color.White,
        primaryContainer = androidx.compose.ui.graphics.Color(0xFFE7F0FF),
        onPrimaryContainer = androidx.compose.ui.graphics.Color(0xFF173B75),
        secondary = androidx.compose.ui.graphics.Color(0xFF52627A),
        background = androidx.compose.ui.graphics.Color(0xFFF7F8FA),
        surface = androidx.compose.ui.graphics.Color.White,
        surfaceVariant = androidx.compose.ui.graphics.Color(0xFFEDEFF3),
        error = androidx.compose.ui.graphics.Color(0xFFDE350B),
        errorContainer = androidx.compose.ui.graphics.Color(0xFFFFEBE6),
        outline = androidx.compose.ui.graphics.Color(0xFF687385)
    )
    val darkColors = darkColorScheme(
        primary = androidx.compose.ui.graphics.Color(0xFF579DFF),
        onPrimary = androidx.compose.ui.graphics.Color(0xFF092957),
        primaryContainer = androidx.compose.ui.graphics.Color(0xFF0747A6),
        onPrimaryContainer = androidx.compose.ui.graphics.Color(0xFFDEEBFF),
        secondary = androidx.compose.ui.graphics.Color(0xFF9F8FEF),
        background = androidx.compose.ui.graphics.Color(0xFF101214),
        surface = androidx.compose.ui.graphics.Color(0xFF1D2125),
        surfaceVariant = androidx.compose.ui.graphics.Color(0xFF2C333A),
        error = androidx.compose.ui.graphics.Color(0xFFFF7452),
        errorContainer = androidx.compose.ui.graphics.Color(0xFF5D1F1A),
        outline = androidx.compose.ui.graphics.Color(0xFF9FADBC)
    )

    MaterialTheme(colorScheme = if (darkTheme) darkColors else lightColors, content = content)
}
