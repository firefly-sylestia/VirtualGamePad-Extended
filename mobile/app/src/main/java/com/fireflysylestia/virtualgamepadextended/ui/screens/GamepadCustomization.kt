package com.fireflysylestia.virtualgamepadextended.ui.screens

import android.annotation.SuppressLint
import android.content.ClipboardManager
import android.content.Context.CLIPBOARD_SERVICE
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.fireflysylestia.virtualgamepadextended.R
import com.fireflysylestia.virtualgamepadextended.data.ButtonAnchor
import com.fireflysylestia.virtualgamepadextended.data.ButtonComponent
import com.fireflysylestia.virtualgamepadextended.data.ButtonConfig
import com.fireflysylestia.virtualgamepadextended.data.ControllerAction
import com.fireflysylestia.virtualgamepadextended.data.ControllerLayout
import com.fireflysylestia.virtualgamepadextended.data.MacroAction
import com.fireflysylestia.virtualgamepadextended.data.MacroDefinition
import com.fireflysylestia.virtualgamepadextended.data.MacroStep
import com.fireflysylestia.virtualgamepadextended.data.OFFSET_VALUE_RANGE
import com.fireflysylestia.virtualgamepadextended.data.PreviewBase
import com.fireflysylestia.virtualgamepadextended.data.SettingsRepository
import com.fireflysylestia.virtualgamepadextended.data.buttonConfigsForLayout
import com.fireflysylestia.virtualgamepadextended.data.defaultButtonConfigs
import com.fireflysylestia.virtualgamepadextended.data.defaultControllerBindings
import com.fireflysylestia.virtualgamepadextended.ui.composables.BoundedNumericInput
import com.fireflysylestia.virtualgamepadextended.ui.composables.ButtonConfigEditor
import com.fireflysylestia.virtualgamepadextended.ui.composables.DrawGamepad
import com.fireflysylestia.virtualgamepadextended.ui.composables.ListItemPicker
import io.github.kitswas.VGP_Data_Exchange.GamepadReading
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json

private const val logTag = "GamepadCustomization"

/**
 * Sanitizes button configurations to ensure all components have a valid config.
 */
private fun sanitizeButtonConfigs(configs: Map<ButtonComponent, ButtonConfig>): Map<ButtonComponent, ButtonConfig> {
    return ButtonComponent.entries.associateWith { component ->
        configs[component] ?: ButtonConfig.default(component)
    }
}

/**
 * Convert ButtonAnchor to Compose Alignment (shared by the editor overlay).
 */
private fun ButtonAnchor.toAlignment(): Alignment = when (this) {
    ButtonAnchor.TOP_LEFT -> Alignment.TopStart
    ButtonAnchor.TOP_CENTER -> Alignment.TopCenter
    ButtonAnchor.TOP_RIGHT -> Alignment.TopEnd
    ButtonAnchor.CENTER_LEFT -> Alignment.CenterStart
    ButtonAnchor.CENTER -> Alignment.Center
    ButtonAnchor.CENTER_RIGHT -> Alignment.CenterEnd
    ButtonAnchor.BOTTOM_LEFT -> Alignment.BottomStart
    ButtonAnchor.BOTTOM_CENTER -> Alignment.BottomCenter
    ButtonAnchor.BOTTOM_RIGHT -> Alignment.BottomEnd
}

/**
 * Representative on-screen footprint for a component, used to size its editor tile.
 */
private fun tileSizeDp(component: ButtonComponent, baseDp: Float, scale: Float): Float = when (component) {
    ButtonComponent.LEFT_ANALOG_STICK,
    ButtonComponent.RIGHT_ANALOG_STICK -> baseDp / 4f * scale

    ButtonComponent.DPAD,
    ButtonComponent.FACE_BUTTONS -> 0.45f * baseDp * scale

    ButtonComponent.TOUCH_SURFACE -> baseDp / 3f * scale

    ButtonComponent.LEFT_TRIGGER,
    ButtonComponent.RIGHT_TRIGGER,
    ButtonComponent.LEFT_SHOULDER,
    ButtonComponent.RIGHT_SHOULDER -> baseDp / 6f * scale

    else -> baseDp / 6f * scale
}

/**
 * Full-screen live gamepad preview overlay (read-only, drives the shared state).
 */
@SuppressLint("ConfigurationScreenWidthHeight")
@Composable
fun GamepadPreview(
    buttonConfigs: Map<ButtonComponent, ButtonConfig>,
    controllerLayout: ControllerLayout,
    macros: List<MacroDefinition>,
    bindings: Map<String, ControllerAction>,
) {
    val configuration = LocalConfiguration.current
    val screenHeight = configuration.screenHeightDp
    val screenWidth = configuration.screenWidthDp

    val gamepadState = remember { GamepadReading() }

    DrawGamepad(
        widthDp = screenWidth,
        heightDp = screenHeight,
        gamepadState = gamepadState,
        buttonConfigs = buttonConfigs,
        controllerLayout = controllerLayout,
        macros = macros,
        bindings = bindings
    )
}

/**
 * Interactive layout editor: every control is a tile you can tap to open its menu
 * and drag to move. Hidden controls are dimmed.
 */
@Composable
private fun LayoutPreviewEditor(
    buttonConfigs: Map<ButtonComponent, ButtonConfig>,
    onSelect: (ButtonComponent) -> Unit,
    onConfigChange: (ButtonComponent, ButtonConfig) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    BoxWithConstraints(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(4.dp)
    ) {
        val baseDp = maxHeight.value
        Box(modifier = Modifier.fillMaxSize()) {
            ButtonComponent.entries.forEach { component ->
                val config = buttonConfigs[component] ?: ButtonConfig.default(component)
                val tile = tileSizeDp(component, baseDp, config.scale)
                val basePx = with(density) { baseDp.dp.toPx() }
                Box(
                    modifier = Modifier
                        .align(config.anchor.toAlignment())
                        .offset(
                            x = (config.offsetX * baseDp).dp,
                            y = (config.offsetY * baseDp).dp
                        )
                        .size(tile.dp, (tile * 1.15f).dp)
                        .alpha(if (config.visible) 1f else 0.4f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (config.visible) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            }
                        )
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                        .clickable { onSelect(component) }
                        .pointerInput(component, config) {
                            detectDragGestures { change, drag ->
                                change.consume()
                                val nx = (config.offsetX + drag.x / basePx)
                                    .coerceIn(OFFSET_VALUE_RANGE)
                                val ny = (config.offsetY + drag.y / basePx)
                                    .coerceIn(OFFSET_VALUE_RANGE)
                                onConfigChange(component, config.copy(offsetX = nx, offsetY = ny))
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(component.nameRes).take(7),
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
fun GamepadCustomizationScreen(
    onNavigateBack: () -> Unit,
    settingsRepository: SettingsRepository
) {
    val buttonConfigs by settingsRepository.buttonConfigs.collectAsState(initial = defaultButtonConfigs)
    val controllerLayout by settingsRepository.controllerLayout.collectAsState(initial = ControllerLayout.SWITCH_2)
    val macros by settingsRepository.macros.collectAsState(initial = emptyList())
    val bindings by settingsRepository.controllerBindings.collectAsState(initial = defaultControllerBindings)

    var showBindingEditor by rememberSaveable { mutableStateOf(false) }
    var showMacroEditor by rememberSaveable { mutableStateOf(false) }
    var modifiedLayout by rememberSaveable { mutableStateOf<ControllerLayout?>(null) }
    var modifiedConfigs by rememberSaveable {
        mutableStateOf<Map<ButtonComponent, ButtonConfig>?>(null)
    }
    var showPlayPreview by rememberSaveable { mutableStateOf(false) }
    var showExportDialog by rememberSaveable { mutableStateOf(false) }
    var showImportDialog by rememberSaveable { mutableStateOf(false) }
    var importJsonText by rememberSaveable { mutableStateOf("") }
    var showMenu by rememberSaveable { mutableStateOf(false) }
    var editingComponent by rememberSaveable { mutableStateOf<ButtonComponent?>(null) }

    val currentConfigs = modifiedConfigs ?: buttonConfigs
    val currentLayout = modifiedLayout ?: controllerLayout

    fun updateConfig(component: ButtonComponent, config: ButtonConfig) {
        val base = modifiedConfigs ?: buttonConfigs
        modifiedConfigs = base + (component to config)
    }

    fun applyPreset(layout: ControllerLayout) {
        modifiedLayout = layout
        if (layout != ControllerLayout.CUSTOM) {
            modifiedConfigs = buttonConfigsForLayout(layout)
        }
    }

    fun enableAllControls() {
        val base = currentConfigs.toMutableMap()
        ButtonComponent.entries.forEach { component ->
            val config = base[component] ?: ButtonConfig.default(component)
            base[component] = config.copy(visible = true)
        }
        modifiedConfigs = base
    }

    BackHandler(enabled = showPlayPreview) { showPlayPreview = false }

    if (showPlayPreview) {
        GamepadPreview(
            buttonConfigs = currentConfigs,
            controllerLayout = currentLayout,
            macros = macros,
            bindings = bindings
        )
        return
    }

    if (showExportDialog) {
        ExportConfigDialog(buttonConfigs = currentConfigs, onDismiss = { showExportDialog = false })
    }

    if (showBindingEditor) {
        ControllerBindingsDialog(
            bindings = bindings,
            onSave = { updated ->
                runBlocking { settingsRepository.setControllerBindings(updated) }
                showBindingEditor = false
            },
            onDismiss = { showBindingEditor = false }
        )
    }

    if (showMacroEditor) {
        MacroEditorDialog(
            macros = macros,
            onSave = { updated ->
                runBlocking { settingsRepository.setMacros(updated) }
                showMacroEditor = false
            },
            onDismiss = { showMacroEditor = false }
        )
    }

    if (showImportDialog) {
        ImportConfigDialog(
            importedJsonText = importJsonText,
            onJsonTextChange = { importJsonText = it },
            onImport = { configs ->
                modifiedConfigs = configs
                showImportDialog = false
                importJsonText = ""
                Log.i(logTag, "Button configs imported")
            },
            onDismiss = {
                showImportDialog = false
                importJsonText = ""
            }
        )
    }

    editingComponent?.let { component ->
        LayoutEditorDialog(
            component = component,
            config = currentConfigs[component] ?: ButtonConfig.default(component),
            onConfigChange = { updateConfig(component, it) },
            onDismiss = { editingComponent = null }
        )
    }

    Scaffold { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                stringResource(R.string.customization_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(top = 6.dp)
            )

            ListItemPicker(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                list = ControllerLayout.entries.asIterable(),
                selectedItem = currentLayout,
                label = "Controller preset",
                isHorizontal = true,
                formattedDisplay = { layout -> Text(layout.displayName) },
                onItemSelected = { layout -> applyPreset(layout) }
            )

            LayoutPreviewEditor(
                buttonConfigs = currentConfigs,
                onSelect = { editingComponent = it },
                onConfigChange = { component, config -> updateConfig(component, config) },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp)
            )

            Text(
                stringResource(R.string.customization_desc),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            // Library of every control. Hidden controls are dimmed; tap to enable.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ButtonComponent.entries.forEach { component ->
                    val config = currentConfigs[component] ?: ButtonConfig.default(component)
                    FilterChip(
                        selected = config.visible,
                        onClick = {
                            updateConfig(component, config.copy(visible = !config.visible))
                        },
                        label = {
                            Text(
                                text = component.displayName,
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        modifier = Modifier.alpha(if (config.visible) 1f else 0.5f)
                    )
                }
            }

            // Tools
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Button(onClick = { showExportDialog = true }) {
                    Text(stringResource(R.string.customization_export))
                }
                Button(onClick = { showImportDialog = true }) {
                    Text(stringResource(R.string.customization_import))
                }
                Button(onClick = { showMacroEditor = true }) { Text("Macros") }
                Button(onClick = { showBindingEditor = true }) { Text("Remap") }
                Button(onClick = { showPlayPreview = true }) {
                    Text(stringResource(R.string.customization_preview))
                }
            }

            // Actions
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(onClick = { applyPreset(ControllerLayout.SWITCH_2) }) {
                    Text(stringResource(R.string.reset))
                }
                Button(onClick = {
                    runBlocking {
                        try {
                            modifiedConfigs?.let { configs ->
                                settingsRepository.setAllButtonConfigs(configs)
                            }
                            settingsRepository.setControllerLayout(currentLayout)
                            Log.i(logTag, "Button configs and controller preset saved")
                        } catch (e: Exception) {
                            Log.e(logTag, "Error saving button configs", e)
                        }
                    }
                    onNavigateBack()
                }) {
                    Text(stringResource(R.string.save))
                }

                Box {
                    Button(onClick = { showMenu = true }) { Text("Menu") }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("Custom layout") },
                            onClick = {
                                modifiedLayout = ControllerLayout.CUSTOM
                                showMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Pokémon Legends Z-A layout") },
                            onClick = {
                                applyPreset(ControllerLayout.POKEMON_ZA)
                                showMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Enable all controls") },
                            onClick = {
                                enableAllControls()
                                showMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Save as custom layout") },
                            onClick = {
                                modifiedLayout = ControllerLayout.CUSTOM
                                showMenu = false
                                runBlocking {
                                    modifiedConfigs?.let {
                                        settingsRepository.setAllButtonConfigs(it)
                                    }
                                    settingsRepository.setControllerLayout(ControllerLayout.CUSTOM)
                                }
                                onNavigateBack()
                            }
                        )
                    }
                }

                Button(onClick = onNavigateBack) {
                    Text(stringResource(R.string.cancel))
                }
            }
        }
    }
}

/**
 * Dialog for editing a single control, opened from the preview or the library.
 */
@Composable
private fun LayoutEditorDialog(
    component: ButtonComponent,
    config: ButtonConfig,
    onConfigChange: (ButtonConfig) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(component.nameRes)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                ButtonConfigEditor(
                    component = component,
                    config = config,
                    onConfigChange = onConfigChange,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) { Text(stringResource(R.string.customization_done)) }
        }
    )
}

/**
 * Dialog for exporting button configurations as JSON
 */
@Composable
fun ExportConfigDialog(
    buttonConfigs: Map<ButtonComponent, ButtonConfig>,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val clipboard = context.getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
    val jsonString = remember(buttonConfigs) {
        Json.encodeToString(buttonConfigs)
    }
    val exportLabel = stringResource(R.string.customization_export_label)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.customization_export_title)) },
        text = {
            Log.i(logTag, "Exporting button configs: $jsonString")
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    stringResource(R.string.customization_export_desc),
                    style = MaterialTheme.typography.bodySmall
                )
                TextField(
                    value = jsonString,
                    onValueChange = {},
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    label = { Text(stringResource(R.string.customization_export_label)) },
                    readOnly = true,
                    singleLine = false,
                    maxLines = 5,
                    textStyle = MaterialTheme.typography.bodySmall,
                    trailingIcon = {
                        Button(onClick = {
                            val clip = android.content.ClipData.newPlainText(
                                exportLabel, jsonString
                            )
                            clipboard.setPrimaryClip(clip)
                            Log.i(logTag, "Exported JSON copied to clipboard")
                        }) {
                            Text(stringResource(R.string.customization_copy))
                        }
                    }
                )
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text(stringResource(R.string.customization_done))
            }
        }
    )
}

/**
 * Dialog for importing button configurations from JSON
 */
@Composable
fun ImportConfigDialog(
    importedJsonText: String,
    onJsonTextChange: (String) -> Unit,
    onImport: (Map<ButtonComponent, ButtonConfig>) -> Unit,
    onDismiss: () -> Unit
) {
    var hasError by rememberSaveable { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.customization_import_title)) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    stringResource(R.string.customization_import_desc),
                    style = MaterialTheme.typography.bodySmall
                )
                val errorMessage = if (hasError) {
                    stringResource(R.string.customization_import_error)
                } else {
                    ""
                }
                Text(
                    errorMessage,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(top = 4.dp)
                )
                TextField(
                    value = importedJsonText,
                    onValueChange = onJsonTextChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    placeholder = { Text(stringResource(R.string.customization_import_placeholder)) },
                    singleLine = false,
                    maxLines = 5
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    try {
                        val configs: Map<ButtonComponent, ButtonConfig> =
                            Json.decodeFromString(importedJsonText)

                        val sanitizedConfigs = sanitizeButtonConfigs(configs)

                        hasError = false
                        Log.i(logTag, "Imported and sanitized button configs: $sanitizedConfigs")
                        onImport(sanitizedConfigs)
                    } catch (e: Exception) {
                        hasError = true
                        Log.e(logTag, "Error parsing JSON", e)
                    }
                }
            ) {
                Text(stringResource(R.string.customization_import))
            }
        },
        dismissButton = {
            Button(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@SuppressLint("ConfigurationScreenWidthHeight")
@Preview(
    showBackground = true,
    widthDp = 800,
    heightDp = 400
)
@Composable
fun GamepadCustomizationScreenPreview() {
    PreviewBase {
        GamepadCustomizationScreen(
            onNavigateBack = {},
            settingsRepository = SettingsRepository(LocalContext.current)
        )
    }
}


@Composable
fun MacroEditorDialog(
    macros: List<MacroDefinition>,
    onSave: (List<MacroDefinition>) -> Unit,
    onDismiss: () -> Unit,
) {
    val initial = remember(macros) {
        (0..3).map { slot ->
            macros.getOrNull(slot) ?: MacroDefinition(name = "Macro ${slot + 1}")
        }
    }
    var definitions by remember(macros) { mutableStateOf(initial) }
    var slot by rememberSaveable { mutableIntStateOf(0) }
    var name by rememberSaveable { mutableStateOf(initial.first().name) }
    var steps by remember { mutableStateOf(initial.first().steps) }
    var action by rememberSaveable { mutableStateOf(MacroAction.A) }
    var value by rememberSaveable { mutableStateOf(1f) }
    var holdMillis by rememberSaveable { mutableIntStateOf(120) }
    var gapMillis by rememberSaveable { mutableIntStateOf(40) }

    fun commitCurrent(): List<MacroDefinition> {
        val updated = definitions.toMutableList()
        updated[slot] = MacroDefinition(
            name = name.trim().ifEmpty { "Macro ${slot + 1}" },
            steps = steps.take(32),
        )
        definitions = updated
        return updated
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Macro editor") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Create up to four macros. Button and D-pad steps use press/release; trigger and axis steps use the value below.")
                ListItemPicker(
                    modifier = Modifier.fillMaxWidth(),
                    list = (0..3).asIterable(),
                    selectedItem = slot,
                    label = "Macro slot",
                    formattedDisplay = { Text("M${it + 1}") },
                    onItemSelected = { nextSlot ->
                        commitCurrent()
                        slot = nextSlot
                        name = definitions[nextSlot].name
                        steps = definitions[nextSlot].steps
                    }
                )
                TextField(
                    value = name,
                    onValueChange = { name = it.take(48) },
                    label = { Text("Macro name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                ListItemPicker(
                    modifier = Modifier.fillMaxWidth(),
                    list = MacroAction.entries.asIterable(),
                    selectedItem = action,
                    label = "Action",
                    formattedDisplay = { Text(it.name.replace('_', ' ')) },
                    onItemSelected = { action = it }
                )
                BoundedNumericInput(
                    value = value,
                    onValueChange = { value = it },
                    label = "Axis / trigger value",
                    minValue = -1f,
                    maxValue = 1f,
                    parseValue = { it.toFloatOrNull() },
                    formatValue = { "%.2f".format(it) },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BoundedNumericInput(
                        value = holdMillis,
                        onValueChange = { holdMillis = it },
                        label = "Hold (ms)",
                        minValue = 10,
                        maxValue = 5000,
                        parseValue = { it.toIntOrNull() },
                        modifier = Modifier.weight(1f)
                    )
                    BoundedNumericInput(
                        value = gapMillis,
                        onValueChange = { gapMillis = it },
                        label = "Gap (ms)",
                        minValue = 0,
                        maxValue = 5000,
                        parseValue = { it.toIntOrNull() },
                        modifier = Modifier.weight(1f)
                    )
                }
                Button(
                    onClick = {
                        if (steps.size < 32) {
                            steps = steps + MacroStep(
                                action = action,
                                value = value,
                                holdMillis = holdMillis,
                                gapMillis = gapMillis,
                            )
                        }
                    },
                    enabled = steps.size < 32,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Add step (${steps.size}/32)")
                }
                steps.forEachIndexed { index, step ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "${index + 1}. ${step.action.name.replace('_', ' ')} · ${step.holdMillis} ms",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall
                        )
                        Button(onClick = { steps = steps.filterIndexed { i, _ -> i != index } }) {
                            Text("Remove")
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSave(commitCurrent()) }) {
                Text("Save macros")
            }
        },
        dismissButton = {
            Button(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}


@Composable
fun ControllerBindingsDialog(
    bindings: Map<String, ControllerAction>,
    onSave: (Map<String, ControllerAction>) -> Unit,
    onDismiss: () -> Unit,
) {
    var edited by remember(bindings) {
        mutableStateOf(defaultControllerBindings + bindings)
    }
    val bindingKeys = defaultControllerBindings.keys.sorted()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Remap controller buttons") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Choose which gamepad action each visible digital control sends. Analog sticks and triggers keep their axis/pressure behavior.")
                bindingKeys.forEach { key ->
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            key.replace('_', ' ').uppercase(),
                            style = MaterialTheme.typography.labelMedium
                        )
                        ListItemPicker(
                            modifier = Modifier.fillMaxWidth(),
                            list = ControllerAction.entries.asIterable(),
                            selectedItem = edited[key] ?: defaultControllerBindings.getValue(key),
                            label = "Output action",
                            formattedDisplay = { Text(it.name.replace('_', ' ')) },
                            onItemSelected = { action ->
                                edited = edited + (key to action)
                            }
                        )
                    }
                }
                Button(
                    onClick = { edited = defaultControllerBindings },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Reset mappings")
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSave(edited) }) {
                Text("Save mappings")
            }
        },
        dismissButton = {
            Button(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
