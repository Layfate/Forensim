package com.forensim.app.core.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.forensim.app.core.designsystem.ThemePreference
import com.forensim.app.core.network.Session
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

private val Context.sessionDataStore: DataStore<Preferences> by preferencesDataStore(name = "session")
private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * Persists the refresh session in app-private storage (excluded from backup, see
 * res/xml/data_extraction_rules.xml). Cleared on sign-out.
 */
class SessionStore(private val context: Context, private val json: Json) {
    private val key = stringPreferencesKey("session")

    suspend fun load(): Session? = context.sessionDataStore.data.first()[key]
        ?.let { runCatching { json.decodeFromString(Session.serializer(), it) }.getOrNull() }

    suspend fun save(session: Session) {
        context.sessionDataStore.edit { it[key] = json.encodeToString(Session.serializer(), session) }
    }

    suspend fun clear() {
        context.sessionDataStore.edit { it.remove(key) }
    }

    private val pkceKey = stringPreferencesKey("pkce_verifier")

    suspend fun savePkceVerifier(v: String) {
        context.sessionDataStore.edit { it[pkceKey] = v }
    }

    private val flowKey = stringPreferencesKey("pkce_flow")

    /** Remembers why the pending PKCE code was requested ("oauth" or "recovery"). */
    suspend fun savePendingFlow(flow: String) {
        context.sessionDataStore.edit { it[flowKey] = flow }
    }

    suspend fun takePendingFlow(): String? {
        var value: String? = null
        context.sessionDataStore.edit { value = it[flowKey]; it.remove(flowKey) }
        return value
    }

    /** One-time read: the verifier is removed as soon as it is used. */
    suspend fun takePkceVerifier(): String? {
        var value: String? = null
        context.sessionDataStore.edit { value = it[pkceKey]; it.remove(pkceKey) }
        return value
    }
}

enum class GraphicsQuality(val renderScale: Float, val label: String) {
    // Fraction of the screen's native resolution the 3D scene is drawn at (HIGH = full HD+).
    LOW(0.75f, "Low"), MEDIUM(0.9f, "Medium"), HIGH(1.0f, "High")
}

/** Gameplay control and accessibility settings (plan sections 5.2 and 10.2). */
data class ControlSettings(
    val lookSensitivity: Float = 1.0f,
    val gyroEnabled: Boolean = false,
    val gyroGain: Float = 1.0f,
    val invertY: Boolean = false,
    val leftHanded: Boolean = false,
    val graphics: GraphicsQuality = GraphicsQuality.HIGH,
    /** Horizontal field of view of the 3D lab camera, in degrees. */
    val fieldOfView: Float = 90f,
)

/** Sound effects and vibration in the 3D lab. */
data class SoundSettings(
    val effects: Boolean = true,
    val volume: Float = 0.7f,
    val vibration: Boolean = true,
)

/** Settings > Notifications: which kinds of alerts the user wants. */
data class NotificationSettings(
    val coursework: Boolean = true,
    val grades: Boolean = true,
    val announcements: Boolean = true,
    val reminders: Boolean = true,
)

data class AppSettings(
    val font: com.forensim.app.core.designsystem.AppFont = com.forensim.app.core.designsystem.AppFont.DEFAULT,
    val theme: ThemePreference = ThemePreference.DARK,
    val reducedMotion: Boolean = false,
    val controls: ControlSettings = ControlSettings(),
    val sound: SoundSettings = SoundSettings(),
    /** Advanced > Ask before playing lesson videos on mobile data. */
    val askBeforeMobileVideo: Boolean = true,
    val notifications: NotificationSettings = NotificationSettings(),
)

class SettingsRepository(private val context: Context) {
    private object Keys {
        val theme = stringPreferencesKey("theme")
        // "font_v2": the default moved from Inter to Poppins (2026-11); earlier choices reset once.
        val font = stringPreferencesKey("font_v2")
        val reducedMotion = booleanPreferencesKey("reduced_motion")
        val sensitivity = floatPreferencesKey("look_sensitivity")
        val gyro = booleanPreferencesKey("gyro_enabled")
        val gyroGain = floatPreferencesKey("gyro_gain")
        val invertY = booleanPreferencesKey("invert_y")
        val leftHanded = booleanPreferencesKey("left_handed")
        val graphics = stringPreferencesKey("graphics")
        val fieldOfView = floatPreferencesKey("field_of_view")
        val soundEffects = booleanPreferencesKey("sound_effects")
        val soundVolume = floatPreferencesKey("sound_volume")
        val vibration = booleanPreferencesKey("vibration")
        val askMobileVideo = booleanPreferencesKey("ask_mobile_video")
        val notifyCoursework = booleanPreferencesKey("notify_coursework")
        val notifyGrades = booleanPreferencesKey("notify_grades")
        val notifyAnnouncements = booleanPreferencesKey("notify_announcements")
        val notifyReminders = booleanPreferencesKey("notify_reminders")
    }

    val settings: Flow<AppSettings> = context.settingsDataStore.data.map { p ->
        AppSettings(
            font = com.forensim.app.core.designsystem.AppFont.from(p[Keys.font]),
            theme = p[Keys.theme]?.let { runCatching { ThemePreference.valueOf(it) }.getOrNull() } ?: ThemePreference.DARK,
            reducedMotion = p[Keys.reducedMotion] ?: false,
            controls = ControlSettings(
                lookSensitivity = p[Keys.sensitivity] ?: 1.0f,
                gyroEnabled = p[Keys.gyro] ?: false,
                gyroGain = p[Keys.gyroGain] ?: 1.0f,
                invertY = p[Keys.invertY] ?: false,
                leftHanded = p[Keys.leftHanded] ?: false,
                graphics = p[Keys.graphics]?.let { runCatching { GraphicsQuality.valueOf(it) }.getOrNull() } ?: GraphicsQuality.HIGH,
                fieldOfView = p[Keys.fieldOfView] ?: 90f,
            ),
            sound = SoundSettings(
                effects = p[Keys.soundEffects] ?: true,
                volume = p[Keys.soundVolume] ?: 0.7f,
                vibration = p[Keys.vibration] ?: true,
            ),
            askBeforeMobileVideo = p[Keys.askMobileVideo] ?: true,
            notifications = NotificationSettings(
                coursework = p[Keys.notifyCoursework] ?: true,
                grades = p[Keys.notifyGrades] ?: true,
                announcements = p[Keys.notifyAnnouncements] ?: true,
                reminders = p[Keys.notifyReminders] ?: true,
            ),
        )
    }

    suspend fun updateNotifications(n: NotificationSettings) = context.settingsDataStore.edit {
        it[Keys.notifyCoursework] = n.coursework
        it[Keys.notifyGrades] = n.grades
        it[Keys.notifyAnnouncements] = n.announcements
        it[Keys.notifyReminders] = n.reminders
    }

    suspend fun setAskBeforeMobileVideo(on: Boolean) = context.settingsDataStore.edit { it[Keys.askMobileVideo] = on }

    /** Advanced > Reset settings: every preference back to its default (language is stored separately). */
    suspend fun resetToDefaults() = context.settingsDataStore.edit { it.clear() }

    suspend fun setFont(font: com.forensim.app.core.designsystem.AppFont) = context.settingsDataStore.edit { it[Keys.font] = font.name }
    suspend fun setTheme(theme: ThemePreference) = context.settingsDataStore.edit { it[Keys.theme] = theme.name }
    suspend fun setReducedMotion(on: Boolean) = context.settingsDataStore.edit { it[Keys.reducedMotion] = on }

    suspend fun updateSound(s: SoundSettings) = context.settingsDataStore.edit {
        it[Keys.soundEffects] = s.effects
        it[Keys.soundVolume] = s.volume.coerceIn(0f, 1f)
        it[Keys.vibration] = s.vibration
    }

    suspend fun updateControls(c: ControlSettings) = context.settingsDataStore.edit {
        it[Keys.sensitivity] = c.lookSensitivity.coerceIn(0.2f, 3f)
        it[Keys.gyro] = c.gyroEnabled
        it[Keys.gyroGain] = c.gyroGain.coerceIn(0.2f, 3f)
        it[Keys.invertY] = c.invertY
        it[Keys.leftHanded] = c.leftHanded
        it[Keys.graphics] = c.graphics.name
        it[Keys.fieldOfView] = c.fieldOfView.coerceIn(70f, 110f)
    }
}
