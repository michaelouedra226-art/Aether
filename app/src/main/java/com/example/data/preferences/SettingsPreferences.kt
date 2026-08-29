package com.example.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "aether_settings")

data class AetherSettings(
    // 1. Lecture
    val crossfadeEnabled: Boolean = false,
    val crossfadeDuration: Float = 3.0f,
    val crossfadeCurve: String = "EqualPower", // Linear, Exponential, EqualPower
    val gaplessPlayback: Boolean = true,
    val autoResumePosition: Boolean = true,
    val endOfQueueAction: String = "STOP", // STOP, REPEAT

    // 2. Bibliothèque
    val whatsAppExclusion: Boolean = true,
    val rescanFrequency: String = "ON_START", // ON_START, MANUAL

    // 3. Interface & Comportement
    val themePreference: String = "OLED_DARK", // OLED_DARK, SYSTEM
    val animationLevel: String = "MAXIMUM", // MINIMAL, BALANCED, MAXIMUM
    val uiDensity: String = "COMFORTABLE", // COMPACT, COMFORTABLE, SPACIOUS
    val doubleBackExitDelayMs: Long = 2000L,

    // 4. Performance
    val batterySaverMode: Boolean = false,
    val visualizerEnabled: Boolean = true,

    // Playback persistence state
    val lastTrackId: Long = -1L,
    val lastPositionMs: Long = 0L,
    val lastQueueIds: String = "",
    val lastQueueIndex: Int = 0,
    val shuffleEnabled: Boolean = false,
    val repeatMode: String = "OFF" // OFF, ALL, ONE
)

class SettingsManager(private val context: Context) {

    private object Keys {
        val CROSSFADE_ENABLED = booleanPreferencesKey("crossfade_enabled")
        val CROSSFADE_DURATION = floatPreferencesKey("crossfade_duration")
        val CROSSFADE_CURVE = stringPreferencesKey("crossfade_curve")
        val GAPLESS_PLAYBACK = booleanPreferencesKey("gapless_playback")
        val AUTO_RESUME_POS = booleanPreferencesKey("auto_resume_pos")
        val LOUDNESS_NORMALIZED = booleanPreferencesKey("loudness_normalized")
        val LOUDNESS_GAIN = floatPreferencesKey("loudness_gain")
        val AUTO_EQ_SPECTRAL = booleanPreferencesKey("auto_eq_spectral")
        val EQ_PRESET = intPreferencesKey("eq_preset")
        val END_OF_QUEUE_ACTION = stringPreferencesKey("end_of_queue_action")

        val WHATSAPP_EXCLUSION = booleanPreferencesKey("whatsapp_exclusion")
        val RESCAN_FREQUENCY = stringPreferencesKey("rescan_frequency")
        val ALBUM_ART_QUALITY = stringPreferencesKey("album_art_quality")
        val DEFAULT_SORT_ORDER = stringPreferencesKey("default_sort_order")

        val THEME_PREFERENCE = stringPreferencesKey("theme_preference")
        val ANIMATION_LEVEL = stringPreferencesKey("animation_level")
        val SHOW_NOTIF_PROGRESS = booleanPreferencesKey("show_notif_progress")
        val UI_DENSITY = stringPreferencesKey("ui_density")
        val DOUBLE_BACK_DELAY = longPreferencesKey("double_back_delay")

        val BATTERY_SAVER = booleanPreferencesKey("battery_saver")
        val VISUALIZER_ENABLED = booleanPreferencesKey("visualizer_enabled")

        val LAST_TRACK_ID = longPreferencesKey("last_track_id")
        val LAST_POSITION_MS = longPreferencesKey("last_position_ms")
        val LAST_QUEUE_IDS = stringPreferencesKey("last_queue_ids")
        val LAST_QUEUE_INDEX = intPreferencesKey("last_queue_index")
        val SHUFFLE_ENABLED = booleanPreferencesKey("shuffle_enabled")
        val REPEAT_MODE = stringPreferencesKey("repeat_mode")
    }

    val settingsFlow: Flow<AetherSettings> = context.dataStore.data.map { prefs ->
        AetherSettings(
            crossfadeEnabled = prefs[Keys.CROSSFADE_ENABLED] ?: false,
            crossfadeDuration = prefs[Keys.CROSSFADE_DURATION] ?: 3.0f,
            crossfadeCurve = prefs[Keys.CROSSFADE_CURVE] ?: "EqualPower",
            gaplessPlayback = prefs[Keys.GAPLESS_PLAYBACK] ?: true,
            autoResumePosition = prefs[Keys.AUTO_RESUME_POS] ?: true,
            endOfQueueAction = prefs[Keys.END_OF_QUEUE_ACTION] ?: "STOP",

            whatsAppExclusion = prefs[Keys.WHATSAPP_EXCLUSION] ?: true,
            rescanFrequency = prefs[Keys.RESCAN_FREQUENCY] ?: "ON_START",

            themePreference = prefs[Keys.THEME_PREFERENCE] ?: "OLED_DARK",
            animationLevel = prefs[Keys.ANIMATION_LEVEL] ?: "MAXIMUM",
            uiDensity = prefs[Keys.UI_DENSITY] ?: "COMFORTABLE",
            doubleBackExitDelayMs = prefs[Keys.DOUBLE_BACK_DELAY] ?: 2000L,

            batterySaverMode = prefs[Keys.BATTERY_SAVER] ?: false,
            visualizerEnabled = prefs[Keys.VISUALIZER_ENABLED] ?: true,

            lastTrackId = prefs[Keys.LAST_TRACK_ID] ?: -1L,
            lastPositionMs = prefs[Keys.LAST_POSITION_MS] ?: 0L,
            lastQueueIds = prefs[Keys.LAST_QUEUE_IDS] ?: "",
            lastQueueIndex = prefs[Keys.LAST_QUEUE_INDEX] ?: 0,
            shuffleEnabled = prefs[Keys.SHUFFLE_ENABLED] ?: false,
            repeatMode = prefs[Keys.REPEAT_MODE] ?: "OFF"
        )
    }

    suspend fun updateVisualizerEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.VISUALIZER_ENABLED] = enabled }
    }

    suspend fun updateCrossfade(enabled: Boolean, duration: Float, curve: String) {
        context.dataStore.edit {
            it[Keys.CROSSFADE_ENABLED] = enabled
            it[Keys.CROSSFADE_DURATION] = duration
            it[Keys.CROSSFADE_CURVE] = curve
        }
    }

    suspend fun updateGapless(enabled: Boolean) {
        context.dataStore.edit { it[Keys.GAPLESS_PLAYBACK] = enabled }
    }

    suspend fun updateAutoResume(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AUTO_RESUME_POS] = enabled }
    }

    suspend fun updateLoudness(normalized: Boolean, gain: Float) {
        context.dataStore.edit {
            it[Keys.LOUDNESS_NORMALIZED] = normalized
            it[Keys.LOUDNESS_GAIN] = gain
        }
    }

    suspend fun updateEqualizer(autoSpectral: Boolean, preset: Int) {
        context.dataStore.edit {
            it[Keys.AUTO_EQ_SPECTRAL] = autoSpectral
            it[Keys.EQ_PRESET] = preset
        }
    }

    suspend fun updateEndOfQueueAction(action: String) {
        context.dataStore.edit { it[Keys.END_OF_QUEUE_ACTION] = action }
    }

    suspend fun updateWhatsAppExclusion(enabled: Boolean) {
        context.dataStore.edit { it[Keys.WHATSAPP_EXCLUSION] = enabled }
    }

    suspend fun updateRescanFrequency(frequency: String) {
        context.dataStore.edit { it[Keys.RESCAN_FREQUENCY] = frequency }
    }

    suspend fun updateAlbumArtQuality(quality: String) {
        context.dataStore.edit { it[Keys.ALBUM_ART_QUALITY] = quality }
    }

    suspend fun updateDefaultSortOrder(sortOrder: String) {
        context.dataStore.edit { it[Keys.DEFAULT_SORT_ORDER] = sortOrder }
    }

    suspend fun updateTheme(theme: String) {
        context.dataStore.edit { it[Keys.THEME_PREFERENCE] = theme }
    }

    suspend fun updateAnimationLevel(level: String) {
        context.dataStore.edit { it[Keys.ANIMATION_LEVEL] = level }
    }

    suspend fun updateShowNotificationProgress(show: Boolean) {
        context.dataStore.edit { it[Keys.SHOW_NOTIF_PROGRESS] = show }
    }

    suspend fun updateUiDensity(density: String) {
        context.dataStore.edit { it[Keys.UI_DENSITY] = density }
    }

    suspend fun updateDoubleBackDelay(delayMs: Long) {
        context.dataStore.edit { it[Keys.DOUBLE_BACK_DELAY] = delayMs }
    }

    suspend fun updateBatterySaver(enabled: Boolean) {
        context.dataStore.edit { it[Keys.BATTERY_SAVER] = enabled }
    }

    suspend fun updatePlaybackSession(
        trackId: Long,
        positionMs: Long,
        queueIds: String,
        queueIndex: Int,
        shuffle: Boolean,
        repeat: String
    ) {
        context.dataStore.edit {
            it[Keys.LAST_TRACK_ID] = trackId
            it[Keys.LAST_POSITION_MS] = positionMs
            it[Keys.LAST_QUEUE_IDS] = queueIds
            it[Keys.LAST_QUEUE_INDEX] = queueIndex
            it[Keys.SHUFFLE_ENABLED] = shuffle
            it[Keys.REPEAT_MODE] = repeat
        }
    }

    suspend fun savePlaybackState(
        trackId: Long,
        positionMs: Long,
        queueIds: List<Long>,
        queueIndex: Int,
        shuffle: Boolean,
        repeat: String
    ) {
        context.dataStore.edit {
            it[Keys.LAST_TRACK_ID] = trackId
            it[Keys.LAST_POSITION_MS] = positionMs
            it[Keys.LAST_QUEUE_IDS] = queueIds.joinToString(",")
            it[Keys.LAST_QUEUE_INDEX] = queueIndex
            it[Keys.SHUFFLE_ENABLED] = shuffle
            it[Keys.REPEAT_MODE] = repeat
        }
    }

    suspend fun resetAllPlaybackData() {
        context.dataStore.edit {
            it.remove(Keys.LAST_TRACK_ID)
            it.remove(Keys.LAST_POSITION_MS)
            it.remove(Keys.LAST_QUEUE_IDS)
            it.remove(Keys.LAST_QUEUE_INDEX)
            it.remove(Keys.SHUFFLE_ENABLED)
            it.remove(Keys.REPEAT_MODE)
        }
    }
}
