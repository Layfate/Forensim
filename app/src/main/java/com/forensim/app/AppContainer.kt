package com.forensim.app

import android.content.Context
import com.forensim.app.core.data.AuthRepository
import com.forensim.app.core.data.AuthState
import com.forensim.app.core.data.ProfileRepository
import com.forensim.app.core.data.JsonCache
import com.forensim.app.core.data.LearningRepository
import com.forensim.app.core.data.SessionStore
import com.forensim.app.core.data.SettingsRepository
import com.forensim.app.core.data.SimulationRepository
import com.forensim.app.core.data.StaffRepository
import com.forensim.app.core.data.TutorRepository
import com.forensim.app.core.network.SupabaseClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

/**
 * Manual dependency container. Explicit constructor injection keeps every dependency visible and
 * swappable in tests; it can be replaced by Hilt (plan section 2.1) once the module graph settles.
 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
        encodeDefaults = true
    }

    val supabase = SupabaseClient(
        baseUrl = BuildConfig.SUPABASE_URL,
        publishableKey = BuildConfig.SUPABASE_PUBLISHABLE_KEY,
        sessionStore = SessionStore(appContext, json),
        json = json,
    )
    val cache = JsonCache(appContext, supabase)
    val settings = SettingsRepository(appContext)
    val auth = AuthRepository(supabase, cache, appScope)
    val learning = LearningRepository(supabase, cache)
    val simulation = SimulationRepository(supabase, cache)
    val tutor = TutorRepository(supabase)
    val reports = com.forensim.app.core.data.ReportRepository(supabase)
    val chat = com.forensim.app.core.data.ChatRepository(supabase, cache, java.io.File(appContext.cacheDir, "chat_photos"))
    val staff = StaffRepository(supabase)
    val profile = ProfileRepository(supabase, appContext)
    val daily = com.forensim.app.core.data.DailyRepository(supabase, cache)
    /** Offline practice work (training parts, practice reports, case progress) waiting to be sent. */
    val practiceSync = com.forensim.app.core.data.PracticeSync(appContext, supabase, cache, simulation).also { sync ->
        simulation.pendingTraining = { sync.pending.value.training.toSet() }
    }

    init {
        // Load the user's profile and photo on sign-in; forget them on sign-out.
        appScope.launch {
            auth.state.collect { s ->
                when (s) {
                    is AuthState.SignedIn -> {
                        // Send practice work saved offline (for this account) before anything else.
                        launch {
                            practiceSync.load()
                            if (!practiceSync.pending.value.isEmpty && practiceSync.flush() == com.forensim.app.core.data.SyncOutcome.RETRY) {
                                practiceSync.schedule()
                            }
                        }
                        runCatching { profile.refresh() }
                    }
                    AuthState.SignedOut -> { profile.clear(); daily.clear(); practiceSync.clear() }
                    AuthState.Loading -> Unit
                }
            }
        }
    }
}
