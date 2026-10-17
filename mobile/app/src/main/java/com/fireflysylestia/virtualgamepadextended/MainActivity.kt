package com.fireflysylestia.virtualgamepadextended

import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.fireflysylestia.virtualgamepadextended.data.SettingsRepository
import com.fireflysylestia.virtualgamepadextended.data.defaultBaseColor
import com.fireflysylestia.virtualgamepadextended.data.defaultColorScheme
import com.fireflysylestia.virtualgamepadextended.data.defaultFullScreenEnabled
import com.fireflysylestia.virtualgamepadextended.data.defaultGameRumbleEnabled
import com.fireflysylestia.virtualgamepadextended.data.defaultHapticFeedbackEnabled
import com.fireflysylestia.virtualgamepadextended.data.defaultKeepScreenOn
import com.fireflysylestia.virtualgamepadextended.data.defaultSaveConnectionCredentials
import com.fireflysylestia.virtualgamepadextended.network.ConnectionViewModel
import com.fireflysylestia.virtualgamepadextended.network.ConnectionViewModelFactory
import com.fireflysylestia.virtualgamepadextended.ui.screens.AboutScreen
import com.fireflysylestia.virtualgamepadextended.ui.screens.ConnectMenu
import com.fireflysylestia.virtualgamepadextended.ui.screens.ConnectingScreen
import com.fireflysylestia.virtualgamepadextended.ui.screens.GamePad
import com.fireflysylestia.virtualgamepadextended.ui.screens.GamepadCustomizationScreen
import com.fireflysylestia.virtualgamepadextended.ui.screens.MainMenu
import com.fireflysylestia.virtualgamepadextended.ui.screens.SettingsScreen
import com.fireflysylestia.virtualgamepadextended.ui.theme.VirtualGamePadExtendedTheme
import com.fireflysylestia.virtualgamepadextended.ui.utils.HapticUtils
import com.fireflysylestia.virtualgamepadextended.ui.utils.RumblePlayer
import kotlinx.coroutines.flow.first
import kotlin.system.exitProcess


class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val settingsRepository = SettingsRepository(this)

        // Create a ViewModel the first time the system calls an activity's onCreate() method.
        // Re-created activities receive the same ConnectionViewModel instance created by the first activity.
        // Use the 'by viewModels()' Kotlin property delegate with factory
        // from the activity-ktx artifact
        val connectionViewModel: ConnectionViewModel by viewModels {
            ConnectionViewModelFactory { ip, port ->
                if (settingsRepository.saveConnectionCredentials.first()) {
                    settingsRepository.setLastConnectionCredentials(ip, port.toString())
                }
            }
        }
        setContent {
            AppUI(
                connectionViewModel = connectionViewModel,
                settingsRepository = settingsRepository,
            )
        }
    }

    @Composable
    private fun AppUI(
        connectionViewModel: ConnectionViewModel,
        settingsRepository: SettingsRepository,
    ) {
        val hapticEnabled = settingsRepository.hapticFeedbackEnabled.collectAsState(
            initial = defaultHapticFeedbackEnabled
        )

        LaunchedEffect(hapticEnabled.value) {
            HapticUtils.isEnabled = hapticEnabled.value
        }

        // Game rumble is its own switch: it is vibration the game asked for, not
        // feedback from touching a control, so it does not follow the haptics
        // toggle. Switching it off has to silence a rumble already in progress,
        // which is why the running train is stopped rather than just ignored.
        val gameRumbleEnabled = settingsRepository.gameRumbleEnabled.collectAsState(
            initial = defaultGameRumbleEnabled
        )

        LaunchedEffect(gameRumbleEnabled.value) {
            RumblePlayer.isEnabled = gameRumbleEnabled.value
            if (!gameRumbleEnabled.value) RumblePlayer.stop()
        }

        val fullScreenEnabled = settingsRepository.fullScreenEnabled.collectAsState(
            initial = defaultFullScreenEnabled
        )

        // Immersive mode is not a one-shot switch: the bars come back for a swipe,
        // a dialog or the back gesture, and hiding them only when the setting
        // changed left the gamepad sitting with the navigation bar on screen until
        // the app was restarted. So the bars are re-hidden whenever they reappear,
        // and whenever the window regains focus after the notification shade or
        // another app had it. Hiding from inside the visibility callback has to be
        // posted, or the controller would re-enter itself synchronously.
        DisposableEffect(fullScreenEnabled.value, window) {
            val insetsController = WindowCompat.getInsetsController(window, window.decorView)
            if (!fullScreenEnabled.value) {
                WindowCompat.setDecorFitsSystemWindows(window, true)
                insetsController.show(WindowInsetsCompat.Type.systemBars())
                return@DisposableEffect onDispose { }
            }

            WindowCompat.setDecorFitsSystemWindows(window, false)
            insetsController.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

            val decorView = window.decorView
            val hideBars = { insetsController.hide(WindowInsetsCompat.Type.systemBars()) }
            hideBars()

            val focusListener = View.OnFocusChangeListener { _, hasFocus ->
                if (hasFocus) hideBars()
            }
            decorView.setOnFocusChangeListener(focusListener)

            val visibilityListener = View.OnSystemUiVisibilityChangeListener {
                decorView.post { hideBars() }
            }
            decorView.setOnSystemUiVisibilityChangeListener(visibilityListener)

            onDispose {
                decorView.setOnFocusChangeListener(null)
                decorView.setOnSystemUiVisibilityChangeListener(null)
            }
        }

        // A controller session is watched, not read: the screen dimming in the
        // middle of it is the one thing that interrupts a game, so the option to
        // keep it awake lives with the app's other display settings.
        val keepScreenOn = settingsRepository.keepScreenOn.collectAsState(
            initial = defaultKeepScreenOn
        )

        LaunchedEffect(keepScreenOn.value) {
            if (keepScreenOn.value) {
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }

        VirtualGamePadExtendedTheme(
            darkMode = settingsRepository.colorScheme.collectAsState(
                initial = defaultColorScheme
            ).value,
            baseColor = settingsRepository.baseColor.collectAsState(
                initial = defaultBaseColor
            ).value
        ) {
            NavTree(
                connectionViewModel = connectionViewModel,
                settingsRepository = settingsRepository,
            )
        }
    }

    @Composable
    private fun NavTree(
        connectionViewModel: ConnectionViewModel,
        settingsRepository: SettingsRepository,
        navController: NavHostController = rememberNavController(),
    ) {
        NavHost(navController = navController, startDestination = "main_menu") {
            composable("main_menu") {
                MainMenu(
                    onNavigateToConnectScreen = { navController.navigate("connect_screen") },
                    onNavigateToSettingsScreen = { navController.navigate("settings_screen") },
                    onNavigateToAboutScreen = { navController.navigate("about_screen") },
                    onExit = { exitProcess(0) }
                )
            }
            composable("connect_screen") {
                val lastIpAddress by settingsRepository.lastConnectionIpAddress.collectAsState(
                    initial = ""
                )
                val lastPort by settingsRepository.lastConnectionPort.collectAsState(initial = "")
                val saveCredentials by settingsRepository.saveConnectionCredentials.collectAsState(
                    initial = defaultSaveConnectionCredentials
                )

                val initialIp = if (saveCredentials) lastIpAddress else ""
                val initialPort = if (saveCredentials) lastPort else ""

                ConnectMenu(
                    onNavigateToConnectingScreen = { ipAddress, port ->
                        navController.navigate("connecting_screen/$ipAddress/$port")
                    },
                    initialIp = initialIp,
                    initialPort = initialPort
                )
            }
            composable(
                "connecting_screen/{ipAddress}/{port}",
                arguments = listOf(
                    navArgument("ipAddress") { type = NavType.StringType },
                    navArgument("port") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val ipAddress = backStackEntry.arguments?.getString("ipAddress") ?: ""
                val port = backStackEntry.arguments?.getString("port") ?: ""
                ConnectingScreen(
                    onNavigateToGamepad = {
                        navController.navigate("gamepad") {
                            popUpTo("connect_screen") { inclusive = true }
                        }
                    },
                    onNavigateBack = { navController.popBackStack() },
                    connectionViewModel = connectionViewModel,
                    ipAddress = ipAddress,
                    port = port
                )
            }
            composable("gamepad") {
                GamePad(
                    connectionViewModel = connectionViewModel,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable("settings_screen") {
                SettingsScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToGamepadCustomization = { navController.navigate("gamepad_customization") },
                    settingsRepository = settingsRepository
                )
            }
            composable("gamepad_customization") {
                GamepadCustomizationScreen(
                    onNavigateBack = { navController.popBackStack() },
                    settingsRepository = settingsRepository
                )
            }
            composable("about_screen") {
                AboutScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }
    }
}
