package fr.terrapilot.assistant.domain

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "terrapilot_settings")

data class AppSettings(
    val darkTheme: Boolean = false,
    val soundAlertsEnabled: Boolean = true,
    val soundVolumePercent: Int = 80,
    val orientationMode: String = "AUTO", // AUTO, LANDSCAPE, PORTRAIT
    val displayHalfCm: Boolean = true,
    val defaultSwellFactor: Double = 1.25,
    val activeChantierId: Long = -1L
)

class SettingsRepository(private val context: Context) {
    private object Keys {
        val DARK_THEME = booleanPreferencesKey("dark_theme")
        val SOUND_ALERTS = booleanPreferencesKey("sound_alerts")
        val SOUND_VOLUME = intPreferencesKey("sound_volume")
        val ORIENTATION = stringPreferencesKey("orientation")
        val HALF_CM = booleanPreferencesKey("half_cm")
        val SWELL = doublePreferencesKey("swell")
        val ACTIVE_CHANTIER = longPreferencesKey("active_chantier")
    }

    val settingsFlow: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            darkTheme = p[Keys.DARK_THEME] ?: false,
            soundAlertsEnabled = p[Keys.SOUND_ALERTS] ?: true,
            soundVolumePercent = p[Keys.SOUND_VOLUME] ?: 80,
            orientationMode = p[Keys.ORIENTATION] ?: "AUTO",
            displayHalfCm = p[Keys.HALF_CM] ?: true,
            defaultSwellFactor = p[Keys.SWELL] ?: 1.25,
            activeChantierId = p[Keys.ACTIVE_CHANTIER] ?: -1L
        )
    }

    suspend fun setActiveChantier(id: Long) {
        context.dataStore.edit { it[Keys.ACTIVE_CHANTIER] = id }
    }

    suspend fun update(transform: (MutablePreferences) -> Unit) {
        context.dataStore.edit(transform)
    }

    suspend fun setDarkTheme(v: Boolean) = update { it[Keys.DARK_THEME] = v }
    suspend fun setSoundAlerts(v: Boolean) = update { it[Keys.SOUND_ALERTS] = v }
    suspend fun setSoundVolume(v: Int) = update { it[Keys.SOUND_VOLUME] = v }
    suspend fun setOrientation(v: String) = update { it[Keys.ORIENTATION] = v }
    suspend fun setHalfCm(v: Boolean) = update { it[Keys.HALF_CM] = v }
    suspend fun setSwell(v: Double) = update { it[Keys.SWELL] = v }
}
