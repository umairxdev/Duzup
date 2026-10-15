package com.duzup.app

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import com.duzup.app.core.designsystem.LocalReduceMotion
import com.duzup.app.feature.intro.IntroOverlay
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.duzup.app.core.designsystem.DuzupTheme
import com.duzup.app.core.model.ThemeMode
import com.duzup.app.core.notifications.NotificationHelper
import com.duzup.app.core.preferences.UserPreferences
import com.duzup.app.navigation.DuzupNavHost
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()
    private var pendingItemId by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        // The system splash is only a black hand-over frame; the intro below is drawn on the very first frame
        // (before DataStore has answered) so there is no blank wait.
        enableEdgeToEdge()
        pendingItemId = intent?.getStringExtra(NotificationHelper.EXTRA_ITEM_ID)

        setContent {
            val prefs by viewModel.prefs.collectAsStateWithLifecycle()
            val loaded = prefs
            val current = loaded ?: UserPreferences.defaults()
            val dark = when (current.theme) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            // Keep system bar icon contrast correct even when the app theme differs from the system theme.
            DisposableEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = if (dark) SystemBarStyle.dark(Color.TRANSPARENT)
                    else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
                    navigationBarStyle = if (dark) SystemBarStyle.dark(Color.TRANSPARENT)
                    else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
                )
                onDispose { }
            }
            DuzupTheme(
                themeMode = current.theme,
                reduceMotionPreference = current.reduceMotion,
                hapticsEnabled = current.haptics,
            ) {
                // The start destination is decided once; finishing onboarding navigates explicitly.
                Box(Modifier.fillMaxSize()) {
                    if (loaded != null) {
                        val startOnboarding = remember { !loaded.onboardingCompleted }
                        DuzupNavHost(
                            startOnboarding = startOnboarding,
                            pendingItemId = pendingItemId,
                            onPendingItemConsumed = { pendingItemId = null },
                        )
                    }
                    // The brand moment on launch; skipped with reduced motion.
                    var showIntro by rememberSaveable { mutableStateOf(true) }
                    val reduceMotion = LocalReduceMotion.current
                    if (loaded != null && reduceMotion && showIntro) SideEffect { showIntro = false }
                    if (showIntro && (loaded == null || !reduceMotion)) IntroOverlay(onDone = { showIntro = false })
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingItemId = intent.getStringExtra(NotificationHelper.EXTRA_ITEM_ID)
    }
}
