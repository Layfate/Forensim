package com.forensim.app

import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.forensim.app.ui.ForensimApp
import kotlinx.coroutines.launch

/** AppCompatActivity for per-app language switching on every Android version (AppCompatDelegate). */
class MainActivity : AppCompatActivity() {
    private val container get() = (application as ForensimApplication).container

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestHighestRefreshRate()
        if (savedInstanceState == null) handleAuthRedirect(intent)
        setContent { ForensimApp(container) }
    }

    override fun onResume() {
        super.onResume()
        // The active display can change while paused (foldables, external displays).
        requestHighestRefreshRate()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        requestHighestRefreshRate()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleAuthRedirect(intent)
    }

    /**
     * Asks for the panel's fastest mode (90/120/144 Hz) at the current resolution. Many OEM
     * skins otherwise keep third-party apps at 60 Hz, which makes scrolling and animations feel
     * heavy. The system still overrides this in battery saver or when the device is hot.
     */
    private fun requestHighestRefreshRate() {
        val display = (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) display
            else @Suppress("DEPRECATION") windowManager.defaultDisplay) ?: return
        val current = display.mode
        val best = display.supportedModes
            .filter { it.physicalWidth == current.physicalWidth && it.physicalHeight == current.physicalHeight }
            .maxByOrNull { it.refreshRate } ?: return
        if (window.attributes.preferredDisplayModeId != best.modeId) {
            window.attributes = window.attributes.apply { preferredDisplayModeId = best.modeId }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            // Adaptive-refresh panels: jump to the top rate as soon as a finger touches the screen.
            window.setFrameRateBoostOnTouchEnabled(true)
        }
    }

    /** Google / Facebook sign-in returns here via com.forensim.app://login-callback. */
    private fun handleAuthRedirect(intent: Intent?) {
        val uri = intent?.data ?: return
        if (uri.scheme == "com.forensim.app" && uri.host == "login-callback") {
            container.appScope.launch { container.auth.completeOAuth(uri) }
        }
    }
}
