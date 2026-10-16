package com.fireflysylestia.virtualgamepadextended.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(context: Context) {
    private val dataStore = context.settingsDataStore

    val baseColor: Flow<BaseColor> = dataStore.data.map { preferences ->
        BaseColor.fromInt(preferences[BASE_COLOR] ?: defaultBaseColor.ordinal)
    }

    val colorScheme: Flow<ColorScheme> = dataStore.data.map { preferences ->
        ColorScheme.fromInt(preferences[COLOR_SCHEME] ?: defaultColorScheme.ordinal)
    }

    val pollingDelay: Flow<Int> = dataStore.data.map { preferences ->
        preferences[POLLING_DELAY] ?: defaultPollingDelay
    }

    val hapticFeedbackEnabled: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[HAPTIC_FEEDBACK_ENABLED] ?: defaultHapticFeedbackEnabled
    }

    /**
     * Which controls the gamepad shows.
     *
     * With nothing saved this is the active layout's preset, not
     * [defaultButtonConfigs]: those defaults only describe where each control
     * sits, and most of the extras start hidden there. Handing them straight to
     * the gamepad is what used to leave a Switch 2 without its +/− buttons,
     * HOME/Capture or the rail buttons, even though the preset turns them on.
     */
    val buttonConfigs: Flow<Map<ButtonComponent, ButtonConfig>> =
        dataStore.data.map { preferences ->
            storedButtonConfigs(preferences) ?: buttonConfigsForLayout(layoutFrom(preferences))
        }

    val controllerLayout: Flow<ControllerLayout> = dataStore.data.map { preferences ->
        layoutFrom(preferences)
    }

    val macros: Flow<List<MacroDefinition>> = dataStore.data.map { preferences ->
        val jsonString = preferences[MACROS]
        if (jsonString != null) {
            try {
                Json.decodeFromString<List<MacroDefinition>>(jsonString)
            } catch (e: Exception) {
                emptyList()
            }
        } else {
            emptyList()
        }
    }

    val controllerBindings: Flow<Map<String, ControllerAction>> = dataStore.data.map { preferences ->
        val jsonString = preferences[CONTROLLER_BINDINGS]
        if (jsonString != null) {
            try {
                defaultControllerBindings + Json.decodeFromString<Map<String, ControllerAction>>(jsonString)
            } catch (e: Exception) {
                defaultControllerBindings
            }
        } else {
            defaultControllerBindings
        }
    }

    val lastConnectionIpAddress: Flow<String> = dataStore.data.map { preferences ->
        preferences[LAST_CONNECTION_IP_ADDRESS] ?: ""
    }

    val lastConnectionPort: Flow<String> = dataStore.data.map { preferences ->
        preferences[LAST_CONNECTION_PORT] ?: ""
    }

    val saveConnectionCredentials: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[SAVE_CONNECTION_CREDENTIALS] ?: defaultSaveConnectionCredentials
    }

    val fullScreenEnabled: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[FULL_SCREEN_ENABLED] ?: defaultFullScreenEnabled
    }

    /** How far a stick push is amplified before it is shaped and sent. */
    val stickSensitivity: Flow<Float> = dataStore.data.map { preferences ->
        preferences[STICK_SENSITIVITY] ?: defaultStickSensitivity
    }

    /** How far a phone turn is amplified by gyro aiming before it is shaped. */
    val gyroSensitivity: Flow<Float> = dataStore.data.map { preferences ->
        preferences[GYRO_SENSITIVITY] ?: defaultGyroSensitivity
    }

    /** The measured resting bias of the gyroscope, in radians per second. */
    val gyroCalibration: Flow<Pair<Float, Float>> = dataStore.data.map { preferences ->
        (preferences[GYRO_BIAS_X] ?: defaultGyroBias) to
            (preferences[GYRO_BIAS_Y] ?: defaultGyroBias)
    }

    /** Whether the screen stays awake while the app is in front of the player. */
    val keepScreenOn: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[KEEP_SCREEN_ON] ?: defaultKeepScreenOn
    }

    /**
     * Whether the whole gamepad screen aims the right stick, for games whose
     * camera is on it.
     */
    val fullScreenCameraEnabled: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[FULL_SCREEN_CAMERA] ?: defaultFullScreenCameraEnabled
    }

    /** Whether a D-pad or face-button cluster is one sliding surface. */
    val slideBetweenButtons: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[SLIDE_BETWEEN_BUTTONS] ?: defaultSlideBetweenButtons
    }

    /** The background the live gamepad paints itself with. */
    val gamepadBackground: Flow<GamepadBackground> = dataStore.data.map { preferences ->
        GamepadBackground.fromStored(preferences[GAMEPAD_BACKGROUND])
    }

    /** The colour [GamepadBackground.CUSTOM] stands for, as ARGB. */
    val customGamepadBackground: Flow<Int> = dataStore.data.map { preferences ->
        preferences[CUSTOM_GAMEPAD_BACKGROUND] ?: defaultCustomGamepadBackground
    }

    suspend fun setBaseColor(baseColor: BaseColor) {
        dataStore.edit { preferences ->
            preferences[BASE_COLOR] = baseColor.ordinal
        }
    }

    suspend fun setColorScheme(colorScheme: ColorScheme) {
        dataStore.edit { preferences ->
            preferences[COLOR_SCHEME] = colorScheme.ordinal
        }
    }

    suspend fun setPollingDelay(delay: Int) {
        dataStore.edit { preferences ->
            preferences[POLLING_DELAY] = delay
        }
    }

    suspend fun setHapticFeedbackEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[HAPTIC_FEEDBACK_ENABLED] = enabled
        }
    }

    suspend fun setButtonConfig(component: ButtonComponent, config: ButtonConfig) {
        dataStore.edit { preferences ->
            val currentConfigs =
                (storedButtonConfigs(preferences) ?: buttonConfigsForLayout(layoutFrom(preferences)))
                    .toMutableMap()
            currentConfigs[component] = config
            preferences[BUTTON_CONFIGS] = Json.encodeToString(currentConfigs)
        }
    }

    suspend fun setAllButtonConfigs(configs: Map<ButtonComponent, ButtonConfig>) {
        dataStore.edit { preferences ->
            preferences[BUTTON_CONFIGS] = Json.encodeToString(configs)
        }
    }

    suspend fun setControllerLayout(layout: ControllerLayout) {
        dataStore.edit { preferences ->
            preferences[CONTROLLER_LAYOUT] = layout.name
        }
    }

    suspend fun setMacros(macros: List<MacroDefinition>) {
        dataStore.edit { preferences ->
            preferences[MACROS] = Json.encodeToString(macros)
        }
    }

    suspend fun setControllerBindings(bindings: Map<String, ControllerAction>) {
        dataStore.edit { preferences ->
            preferences[CONTROLLER_BINDINGS] = Json.encodeToString(bindings)
        }
    }

    suspend fun setLastConnectionCredentials(ipAddress: String, port: String) {
        dataStore.edit { preferences ->
            preferences[LAST_CONNECTION_IP_ADDRESS] = ipAddress
            preferences[LAST_CONNECTION_PORT] = port
        }
    }

    suspend fun setSaveConnectionCredentials(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[SAVE_CONNECTION_CREDENTIALS] = enabled
        }
    }

    suspend fun setFullScreenEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[FULL_SCREEN_ENABLED] = enabled
        }
    }

    suspend fun setStickSensitivity(sensitivity: Float) {
        dataStore.edit { preferences ->
            preferences[STICK_SENSITIVITY] = sensitivity
        }
    }

    suspend fun setGyroSensitivity(sensitivity: Float) {
        dataStore.edit { preferences ->
            preferences[GYRO_SENSITIVITY] = sensitivity
        }
    }

    /** Saves the gyroscope's resting bias as measured during calibration. */
    suspend fun setGyroCalibration(biasX: Float, biasY: Float) {
        dataStore.edit { preferences ->
            preferences[GYRO_BIAS_X] = biasX
            preferences[GYRO_BIAS_Y] = biasY
        }
    }

    suspend fun setKeepScreenOn(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[KEEP_SCREEN_ON] = enabled
        }
    }

    suspend fun setFullScreenCameraEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[FULL_SCREEN_CAMERA] = enabled
        }
    }

    suspend fun setSlideBetweenButtons(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[SLIDE_BETWEEN_BUTTONS] = enabled
        }
    }

    suspend fun setGamepadBackground(background: GamepadBackground) {
        dataStore.edit { preferences ->
            preferences[GAMEPAD_BACKGROUND] = background.name
        }
    }

    suspend fun setCustomGamepadBackground(color: Int) {
        dataStore.edit { preferences ->
            preferences[CUSTOM_GAMEPAD_BACKGROUND] = color
        }
    }

    suspend fun resetAllSettings() {
        dataStore.edit { preferences ->
            preferences.clear()
        }
    }

    /** The saved per-control configuration, or null when the user has none. */
    private fun storedButtonConfigs(preferences: Preferences): Map<ButtonComponent, ButtonConfig>? =
        preferences[BUTTON_CONFIGS]?.let { jsonString ->
            try {
                Json.decodeFromString<Map<ButtonComponent, ButtonConfig>>(jsonString)
            } catch (e: Exception) {
                null
            }
        }

    /**
     * The layout in the store: the saved one, else a custom layout when only
     * control configurations were saved, else the default family.
     */
    private fun layoutFrom(preferences: Preferences): ControllerLayout {
        val storedLayout = preferences[CONTROLLER_LAYOUT]
        return when {
            storedLayout != null -> ControllerLayout.fromStored(storedLayout)
            preferences[BUTTON_CONFIGS] != null -> ControllerLayout.CUSTOM
            else -> ControllerLayout.SWITCH_2
        }
    }

    companion object {
        private val COLOR_SCHEME = intPreferencesKey("color_scheme")
        private val BASE_COLOR = intPreferencesKey("base_color")
        private val POLLING_DELAY = intPreferencesKey("polling_delay")
        private val HAPTIC_FEEDBACK_ENABLED = booleanPreferencesKey("haptic_feedback_enabled")
        private val BUTTON_CONFIGS = stringPreferencesKey("button_configs")
        private val CONTROLLER_LAYOUT = stringPreferencesKey("controller_layout")
        private val MACROS = stringPreferencesKey("controller_macros")
        private val CONTROLLER_BINDINGS = stringPreferencesKey("controller_bindings")
        private val LAST_CONNECTION_IP_ADDRESS = stringPreferencesKey("last_connection_ip_address")
        private val LAST_CONNECTION_PORT = stringPreferencesKey("last_connection_port")
        private val SAVE_CONNECTION_CREDENTIALS =
            booleanPreferencesKey("save_connection_credentials")
        private val FULL_SCREEN_ENABLED = booleanPreferencesKey("full_screen_enabled")
        private val STICK_SENSITIVITY = floatPreferencesKey("stick_sensitivity")
        private val GYRO_SENSITIVITY = floatPreferencesKey("gyro_sensitivity")
        private val GYRO_BIAS_X = floatPreferencesKey("gyro_bias_x")
        private val GYRO_BIAS_Y = floatPreferencesKey("gyro_bias_y")
        private val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
        private val FULL_SCREEN_CAMERA = booleanPreferencesKey("full_screen_camera")
        private val SLIDE_BETWEEN_BUTTONS = booleanPreferencesKey("slide_between_buttons")
        private val GAMEPAD_BACKGROUND = stringPreferencesKey("gamepad_background")
        private val CUSTOM_GAMEPAD_BACKGROUND = intPreferencesKey("custom_gamepad_background")
    }
}
