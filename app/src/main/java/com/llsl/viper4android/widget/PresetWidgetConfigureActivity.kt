package com.llsl.viper4android.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.size
import com.llsl.viper4android.data.repository.ViperRepository
import com.llsl.viper4android.ui.components.DialogCard
import com.llsl.viper4android.ui.theme.ViperTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlin.math.roundToInt
import javax.inject.Inject

@AndroidEntryPoint
class PresetWidgetConfigureActivity : ComponentActivity() {
    @Inject
    lateinit var repository: ViperRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(Activity.RESULT_CANCELED)
        val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1)
        if (widgetId == -1) {
            finish()
            return
        }
        val previous = PresetWidgetSettings.load(this, widgetId)
        setContent {
            ViperTheme {
                val source = remember { repository.getAllPresets() }
                val presets by source.collectAsState(initial = emptyList())
                val chosen =
                    remember(widgetId) {
                        mutableStateListOf<Long>().apply {
                            addAll(previous?.presets?.map { it.id }.orEmpty())
                        }
                    }
                var orientation by remember(widgetId) {
                    mutableStateOf(previous?.orientation ?: "horizontal")
                }
                var spacing by remember(widgetId) {
                    mutableFloatStateOf((previous?.gapDp ?: 4).toFloat())
                }
                var outerInset by remember(widgetId) {
                    mutableFloatStateOf((previous?.outerInsetDp ?: 0).toFloat())
                }
                var cornerRadius by remember(widgetId) {
                    mutableFloatStateOf((previous?.cornerRadiusDp ?: 10).toFloat())
                }
                var borderWidth by remember(widgetId) {
                    mutableFloatStateOf((previous?.borderWidthDp ?: 1).toFloat())
                }
                var textColor by remember(widgetId) {
                    mutableIntStateOf(previous?.textColor ?: DEFAULT_TEXT_COLOR)
                }
                var presetTextSize by remember(widgetId) {
                    mutableFloatStateOf((previous?.presetTextSizeSp ?: 13).toFloat())
                }
                var presetTextWeight by remember(widgetId) {
                    mutableStateOf(previous?.presetTextWeight ?: "normal")
                }
                var activeTextColor by remember(widgetId) {
                    mutableIntStateOf(previous?.activeTextColor ?: DEFAULT_TEXT_COLOR)
                }
                var identityMode by remember(widgetId) {
                    mutableStateOf(previous?.identityMode ?: "viper")
                }
                var identityText by remember(widgetId) {
                    mutableStateOf(previous?.identityText ?: "")
                }
                var widgetColor by remember(widgetId) {
                    mutableIntStateOf(previous?.widgetColor ?: DEFAULT_WIDGET_COLOR)
                }
                var activeColor by remember(widgetId) {
                    mutableIntStateOf(previous?.activeColor ?: DEFAULT_ACTIVE_COLOR)
                }
                var borderColor by remember(widgetId) {
                    mutableIntStateOf(previous?.borderColor ?: DEFAULT_BORDER_COLOR)
                }
                var activeBorderColor by remember(widgetId) {
                    mutableIntStateOf(previous?.activeBorderColor ?: DEFAULT_ACTIVE_BORDER_COLOR)
                }
                var editingColor by remember { mutableStateOf<String?>(null) }
                var previewVisible by remember(widgetId) { mutableStateOf(true) }

                Scaffold(
                    bottomBar = {
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surface)
                                    .navigationBarsPadding()
                                    .imePadding()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                        ) {
                            Button(
                                modifier = Modifier.fillMaxWidth(),
                                enabled = chosen.isNotEmpty(),
                                onClick = {
                                    PresetWidgetSettings.save(
                                        this@PresetWidgetConfigureActivity,
                                        widgetId,
                                        WidgetConfiguration(
                                            presets = chosen.map { WidgetPreset(it) },
                                            orientation = orientation,
                                            gapDp = spacing.roundToInt(),
                                            outerInsetDp = outerInset.roundToInt(),
                                            cornerRadiusDp = cornerRadius.roundToInt(),
                                            borderWidthDp = borderWidth.roundToInt(),
                                            textColor = textColor,
                                            presetTextSizeSp = presetTextSize.roundToInt(),
                                            presetTextWeight = presetTextWeight,
                                            activeTextColor = activeTextColor,
                                            identityMode = identityMode,
                                            identityText = identityText.trim().take(18),
                                            widgetColor = widgetColor,
                                            activeColor = activeColor,
                                            borderColor = borderColor,
                                            activeBorderColor = activeBorderColor,
                                        ),
                                    )
                                    setResult(
                                        Activity.RESULT_OK,
                                        Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId),
                                    )
                                    PresetWidgetProvider.refreshAll(this@PresetWidgetConfigureActivity)
                                    finish()
                                },
                            ) { Text("Save widget") }
                        }
                    },
                ) { contentPadding ->
                    BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(contentPadding)) {
                        // Reserve room for the settings in landscape and split-screen windows.
                        val previewMaxHeight = minOf(192.dp, maxHeight * 0.25f)
                        Column(modifier = Modifier.fillMaxSize()) {
                        Column(
                            modifier = Modifier.fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surface)
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text("Preset widget", style = MaterialTheme.typography.titleLarge)
                                if (chosen.isNotEmpty()) {
                                    TextButton(onClick = { previewVisible = !previewVisible }) {
                                        Text(if (previewVisible) "Hide preview" else "Show preview")
                                    }
                                }
                            }
                            if (chosen.isNotEmpty() && previewVisible) {
                                Text(
                                    "Live preview — long-press and drag to reorder",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Box(
                                    modifier = Modifier.fillMaxWidth()
                                        .heightIn(max = previewMaxHeight)
                                        .verticalScroll(rememberScrollState()),
                                ) {
                                    PresetWidgetPreview(
                                        chosen = chosen,
                                        names = presets.associate { it.id to it.name },
                                        vertical = orientation == "vertical",
                                        gapDp = spacing.roundToInt(),
                                        outerInsetDp = outerInset.roundToInt(),
                                        cornerRadiusDp = cornerRadius.roundToInt(),
                                        borderWidthDp = borderWidth.roundToInt(),
                                        textColor = textColor,
                                        textSizeSp = presetTextSize.roundToInt(),
                                        textWeight = presetTextWeight,
                                        activeTextColor = activeTextColor,
                                        widgetColor = widgetColor,
                                        identityMode = identityMode,
                                        identityText = identityText,
                                        activeColor = activeColor,
                                        borderColor = borderColor,
                                        activeBorderColor = activeBorderColor,
                                    )
                                }
                            } else if (chosen.isEmpty()) {
                                Text(
                                    "Select presets below to preview.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        Column(
                            modifier = Modifier.weight(1f).fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            WidgetSettingsGroup(title = "Presets", icon = Icons.Default.LibraryMusic) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Text("Select which saved presets appear on this widget.")
                                presets.forEach { preset ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Checkbox(
                                            checked = preset.id in chosen,
                                            onCheckedChange = { selected ->
                                                if (selected && preset.id !in chosen) chosen.add(preset.id)
                                                if (!selected) chosen.remove(preset.id)
                                            },
                                        )
                                        Text(preset.name)
                                    }
                                }
                            }
                        }

                        WidgetSettingsGroup(title = "Layout", icon = Icons.Default.AspectRatio) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    listOf("horizontal", "vertical", "auto").forEach { option ->
                                        FilterChip(
                                            selected = orientation == option,
                                            onClick = { orientation = option },
                                            label = { Text(option.replaceFirstChar { it.uppercase() }) },
                                        )
                                    }
                                }
                                if (orientation == "auto") {
                                    Text("Auto uses a horizontal layout when wide, vertical when tall.")
                                }
                            }
                        }

                        WidgetSettingsGroup(title = "Widget label", icon = Icons.Default.Badge) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    listOf(
                                        "viper" to "ViPER",
                                        "custom" to "Custom",
                                    ).forEach { (mode, label) ->
                                        FilterChip(
                                            selected = identityMode == mode,
                                            onClick = { identityMode = mode },
                                            label = { Text(label) },
                                            leadingIcon = if (mode == "viper") {
                                                {
                                                    Icon(
                                                        imageVector = Icons.Default.GraphicEq,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(18.dp),
                                                    )
                                                }
                                            } else null,
                                        )
                                    }
                                }
                                if (identityMode == "custom") {
                                    OutlinedTextField(
                                        value = identityText,
                                        onValueChange = { identityText = it.replace("\n", " ").take(18) },
                                        modifier = Modifier.fillMaxWidth(),
                                        label = { Text("Button identity") },
                                        placeholder = { Text("e.g. Headphones") },
                                        singleLine = true,
                                        supportingText = { Text("Leave empty to hide the identity label") },
                                    )
                                }
                            }
                        }

                        WidgetSettingsGroup(title = "Spacing & shape", icon = Icons.Default.Tune) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Text("Between buttons: ${spacing.roundToInt()} dp")
                                Slider(value = spacing, onValueChange = { spacing = it }, valueRange = 0f..96f)
                                Text("Outside inset: ${outerInset.roundToInt()} dp")
                                Slider(value = outerInset, onValueChange = { outerInset = it }, valueRange = 0f..64f)
                                Text("Corner radius: ${cornerRadius.roundToInt()} dp")
                                Slider(
                                    value = cornerRadius,
                                    onValueChange = { cornerRadius = it },
                                    valueRange = 0f..32f, steps = 31,
                                )
                                Text("Border width: ${borderWidth.roundToInt()} dp")
                                Slider(
                                    value = borderWidth,
                                    onValueChange = { borderWidth = it },
                                    valueRange = 0f..4f, steps = 3,
                                )
                            }
                        }

                        WidgetSettingsGroup(title = "Preset label", icon = Icons.Default.TextFields) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Text("Size: ${presetTextSize.roundToInt()} sp")
                                Slider(
                                    value = presetTextSize,
                                    onValueChange = { presetTextSize = it },
                                    valueRange = 10f..30f,
                                    steps = 19,
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    listOf("normal" to "Regular", "bold" to "Bold")
                                        .forEach { (weight, label) ->
                                            FilterChip(
                                                selected = presetTextWeight == weight,
                                                onClick = { presetTextWeight = weight },
                                                label = { Text(label) },
                                            )
                                        }
                                }
                            }
                        }

                        WidgetSettingsGroup(title = "Colours", icon = Icons.Default.Palette) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                WidgetColorMatrix(
                                    inactiveBackground = widgetColor,
                                    activeBackground = activeColor,
                                    inactiveText = textColor,
                                    activeText = activeTextColor,
                                    inactiveBorder = borderColor,
                                    activeBorder = activeBorderColor,
                                    onPick = { editingColor = it },
                                )
                            }
                        }
                        }
                        }
                    }
                }

                val title = editingColor
                if (title != null) {
                    key(title) {
                        // Freeze the original colour while preview updates the underlying state.
                        val initial = remember {
                            when (title) {
                                "Text" -> textColor
                                "Active text" -> activeTextColor
                                "Widget" -> widgetColor
                                "Active" -> activeColor
                                "Border" -> borderColor
                                else -> activeBorderColor
                            }
                        }
                        val updateColour: (Int) -> Unit = { colour ->
                            when (title) {
                                "Text" -> textColor = colour
                                "Active text" -> activeTextColor = colour
                                "Widget" -> widgetColor = colour
                                "Active" -> activeColor = colour
                                "Border" -> borderColor = colour
                                else -> activeBorderColor = colour
                            }
                        }
                        PresetWidgetColorPicker(
                            title = title,
                            initialColor = initial,
                            onPreview = updateColour,
                            onDismiss = {
                                updateColour(initial)
                                editingColor = null
                            },
                            onSelect = { colour ->
                                updateColour(colour)
                                editingColor = null
                            },
                            preview = { picked ->
                                WidgetPickerLivePreview(
                                    presetLabels = chosen.map { id ->
                                        presets.firstOrNull { it.id == id }?.name ?: "Missing preset"
                                    },
                                    orientation = orientation,
                                    gapDp = spacing.roundToInt(),
                                    outerInsetDp = outerInset.roundToInt(),
                                    cornerRadiusDp = cornerRadius.roundToInt(),
                                    borderWidthDp = borderWidth.roundToInt(),
                                    textSizeSp = presetTextSize.roundToInt(),
                                    textWeight = presetTextWeight,
                                    identity = if (identityMode == "custom") identityText.trim() else "ViPER",
                                    showViperIcon = identityMode != "custom",
                                    inactiveText = if (title == "Text") picked else textColor,
                                    activeText = if (title == "Active text") picked else activeTextColor,
                                    inactiveBackground = if (title == "Widget") picked else widgetColor,
                                    activeBackground = if (title == "Active") picked else activeColor,
                                    inactiveBorder = if (title == "Border") picked else borderColor,
                                    activeBorder = if (title == "Active border") picked else activeBorderColor,
                                )
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WidgetSettingsGroup(
    title: String,
    icon: ImageVector,
    content: @Composable () -> Unit,
) {
    DialogCard {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        content()
    }
}
