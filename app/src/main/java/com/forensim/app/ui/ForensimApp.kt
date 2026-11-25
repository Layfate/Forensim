package com.forensim.app.ui

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.forensim.app.AppContainer
import com.forensim.app.core.data.AppSettings
import com.forensim.app.core.data.AuthState
import com.forensim.app.core.designsystem.ForensimTheme
import com.forensim.app.core.designsystem.LoadingState
import com.forensim.app.core.model.Role
import com.forensim.app.feature.admin.AdminApp
import com.forensim.app.feature.auth.SignInScreen
import com.forensim.app.feature.professor.ProfessorApp
import com.forensim.app.feature.student.StudentApp

val LocalAppContainer = staticCompositionLocalOf<AppContainer> { error("AppContainer not provided") }

/** ViewModel bound to the app container. [key] separates instances for different arguments. */
@Composable
inline fun <reified VM : ViewModel> appViewModel(key: String? = null, crossinline create: (AppContainer) -> VM): VM {
    val container = LocalAppContainer.current
    return viewModel(key = key, factory = viewModelFactory { initializer { create(container) } })
}

/** Light status/navigation bar icons on dark backgrounds and vice versa, following the app theme. */
@Composable
private fun SystemBarAppearance() {
    val activity = LocalActivity.current
    val view = LocalView.current
    val darkBackground = MaterialTheme.colorScheme.background.luminance() < 0.5f
    if (!view.isInEditMode) {
        SideEffect {
            val window = activity?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkBackground
                isAppearanceLightNavigationBars = !darkBackground
            }
        }
    }
}

@Composable
fun ForensimApp(container: AppContainer) {
    val settings by container.settings.settings.collectAsStateWithLifecycle(initialValue = AppSettings())
    val auth by container.auth.state.collectAsStateWithLifecycle()
    val passwordRecovery by container.auth.passwordRecovery.collectAsStateWithLifecycle()
    CompositionLocalProvider(LocalAppContainer provides container) {
        ForensimTheme(preference = settings.theme, reducedMotion = settings.reducedMotion, font = settings.font) {
            SystemBarAppearance()
            // Root surface supplies background and default content colour for every screen.
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background, contentColor = MaterialTheme.colorScheme.onBackground) {
                when (val s = auth) {
                    AuthState.Loading -> LoadingState(label = "Restoring your session")
                    AuthState.SignedOut -> SignInScreen()
                    // Role-based navigation. The role comes from the server; RLS enforces it regardless.
                    // A password-reset link opens the full "Reset your password" page first.
                    is AuthState.SignedIn -> if (passwordRecovery) com.forensim.app.feature.auth.ResetPasswordScreen(s.user) else when (s.user.role) {
                        Role.STUDENT -> StudentApp(s.user)
                        Role.PROFESSOR -> ProfessorApp(s.user)
                        Role.ADMIN -> AdminApp(s.user)
                    }
                }
                // "Set a new password" after a reset link, e-mail change results.
                if (auth is AuthState.SignedIn) com.forensim.app.feature.settings.AccountLinkDialogs()
            }
        }
    }
}
