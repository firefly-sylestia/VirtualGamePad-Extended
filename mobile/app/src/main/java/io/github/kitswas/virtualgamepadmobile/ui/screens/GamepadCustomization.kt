package io.github.kitswas.virtualgamepadmobile.ui.screens

import android.annotation.SuppressLint
import android.content.ClipboardManager
import android.content.Context.CLIPBOARD_SERVICE
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.kitswas.VGP_Data_Exchange.GamepadReading
import io.github.kitswas.virtualgamepadmobile.R
import io.github.kitswas.virtualgamepadmobile.data.ButtonComponent
import io.github.kitswas.virtualgamepadmobile.data.ButtonConfig
import io.github.kitswas.virtualgamepadmobile.data.ControllerLayout
import io.github.kitswas.virtualgamepadmobile.data.MacroAction
import io.github.kitswas.virtualgamepadmobile.data.MacroDefinition
import io.github.kitswas.virtualgamepadmobile.data.MacroStep
import io.github.kitswas.virtualgamepadmobile.data.ControllerAction
import io.github.kitswas.virtualgamepadmobile.data.defaultControllerBindings
import io.github.kitswas.virtualgamepadmobile.data.buttonConfigsForLayout
import io.github.kitswas.virtualgamepadmobile.data.PreviewBase
import io.github.kitswas.virtualgamepadmobile.data.SettingsRepository
import io.github.kitswas.virtualgamepadmobile.data.defaultButtonConfigs
import io.github.kitswas.virtualgamepadmobile.ui.composables.BoundedNumericInput
import io.github.kitswas.virtualgamepadmobile.ui.composables.ButtonConfigEditor
import io.github.kitswas.virtualgamepadmobile.ui.composables.ListItemPicker
import io.github.kitswas.virtualgamepadmobile.ui.composables.DrawGamepad
import io.github.kitswas.virtualgamepadmobile.ui.composables.ResponsiveGrid
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
 * Full-screen gamepad preview overlay
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

    // Draw the gamepad with current configuration
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
        mutableStateOf<Map<ButtonComponent, ButtonConfig>?>(
            null
        )
    }
    var showPreview by rememberSaveable { mutableStateOf(false) }
    var showExportDialog by rememberSaveable { mutableStateOf(false) }
    var showImportDialog by rememberSaveable { mutableStateOf(false) }
    var importJsonText by rememberSaveable { mutableStateOf("") }

    // Get the current configs to preview (modified or saved)
    val currentConfigs = modifiedConfigs ?: buttonConfigs
    val currentLayout = modifiedLayout ?: controllerLayout

    // Handle back button to close preview
    BackHandler(enabled = showPreview) {
        showPreview = false
    }

    if (showPreview) {
        // Full-screen preview overlay
        GamepadPreview(buttonConfigs = currentConfigs, controllerLayout = currentLayout, macros = macros, bindings = bindings)
        return
    }

    if (showExportDialog) {
        ExportConfigDialog(
            buttonConfigs = currentConfigs,
            onDismiss = { showExportDialog = false }
        )
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

    Scaffold { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Fixed title at the top
            Text(
                stringResource(R.string.customization_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            // Scrollable button configuration content with responsive grid
            val scrollState = rememberScrollState()
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(scrollState)
                    .padding(horizontal = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    stringResource(R.string.customization_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 4.dp)
                )

                ListItemPicker(
                    modifier = Modifier.fillMaxWidth(),
                    list = ControllerLayout.entries.asIterable(),
                    selectedItem = currentLayout,
                    label = "Controller preset",
                    isHorizontal = false,
                    formattedDisplay = { layout -> Text(layout.displayName) },
                    onItemSelected = { layout ->
                        modifiedLayout = layout
                        if (layout != ControllerLayout.CUSTOM) {
                            modifiedConfigs = buttonConfigsForLayout(layout)
                        }
                    }
                )

                val minCardWidth = 420.dp
                // Responsive grid that dynamically calculates columns and batches items
                ResponsiveGrid(
                    items = ButtonComponent.entries.toList(),
                    minItemWidth = minCardWidth,
                    horizontalSpacing = 8.dp,
                    verticalSpacing = 8.dp,
                ) { component ->
                    val config = if (modifiedConfigs != null) {
                        modifiedConfigs!![component] ?: ButtonConfig.default(component)
                    } else {
                        buttonConfigs[component] ?: ButtonConfig.default(component)
                    }

                    val onConfigChange = remember(component) {
                        { newConfig: ButtonConfig ->
                            val currentConfigs = modifiedConfigs ?: buttonConfigs
                            modifiedConfigs = currentConfigs + (component to newConfig)
                        }
                    }

                    ButtonConfigEditor(
                        component = component,
                        config = config,
                        onConfigChange = onConfigChange,
                        modifier = Modifier.widthIn(min = minCardWidth)
                    )
                }
            }

            // Fixed buttons at the bottom
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp, horizontal = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Import/Export, macro editing and Preview buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Button(onClick = { showExportDialog = true }) {
                        Text(stringResource(R.string.customization_export))
                    }
                    Button(onClick = { showPreview = true }) {
                        Text(stringResource(R.string.customization_preview))
                    }
                    Button(onClick = { showImportDialog = true }) {
                        Text(stringResource(R.string.customization_import))
                    }
                    Button(onClick = { showMacroEditor = true }) {
                        Text("Macros")
                    }
                    Button(onClick = { showBindingEditor = true }) {
                        Text("Remap")
                    }
                }

                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Button(onClick = {
                        modifiedLayout = ControllerLayout.SWITCH_2
                        modifiedConfigs = buttonConfigsForLayout(ControllerLayout.SWITCH_2)
                        runBlocking {
                            settingsRepository.setAllButtonConfigs(buttonConfigsForLayout(ControllerLayout.SWITCH_2))
                            settingsRepository.setControllerLayout(ControllerLayout.SWITCH_2)
                        }
                        Log.i(logTag, "Button configs reset to Switch 2 defaults")
                    }) {
                        Text(stringResource(R.string.reset))
                    }

                    Button(onClick = {
                        modifiedConfigs?.let { configs ->
                            runBlocking {
                                try {
                                    settingsRepository.setAllButtonConfigs(configs)
                                    settingsRepository.setControllerLayout(currentLayout)
                                    Log.i(logTag, "Button configs and controller preset saved")
                                } catch (e: Exception) {
                                    Log.e(
                                        logTag, "Error saving button configs", e
                                    )
                                }
                            }
                        }
                        onNavigateBack()
                    }) {
                        Text(stringResource(R.string.save))
                    }

                    Button(onClick = onNavigateBack) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            }
        }
    }
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
                            // Copy to clipboard
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

                        // Sanitize the imported configs
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
