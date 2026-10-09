package com.llsl.viper4android.ui.screens.main

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.BlurCircular
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.filled.CandlestickChart
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.CrisisAlert
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.SettingsInputComponent
import androidx.compose.material.icons.filled.SpatialAudio
import androidx.compose.material.icons.filled.SpeakerPhone
import androidx.compose.material.icons.filled.SurroundSound
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VerticalAlignCenter
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.llsl.viper4android.R
import com.llsl.viper4android.effect.EffectState
import com.llsl.viper4android.effect.Effects
import com.llsl.viper4android.effect.compressorAdaptAmountToSeconds
import com.llsl.viper4android.effect.compressorDbToRaw
import com.llsl.viper4android.effect.compressorMsToSeconds
import com.llsl.viper4android.effect.compressorRatioToRaw
import com.llsl.viper4android.effect.compressorRawToDb
import com.llsl.viper4android.effect.compressorRawToRatio
import com.llsl.viper4android.effect.compressorSecondsToMs
import com.llsl.viper4android.ui.components.ConfirmDialog
import com.llsl.viper4android.ui.components.EqCurveGraph
import com.llsl.viper4android.ui.components.EqEditDialog
import com.llsl.viper4android.ui.components.InputDialog
import com.llsl.viper4android.ui.components.LabeledDropdown
import com.llsl.viper4android.ui.components.LabeledSlider
import com.llsl.viper4android.ui.components.LabeledSwitch
import com.llsl.viper4android.ui.components.RichText
import com.llsl.viper4android.ui.components.SliderEdit
import com.llsl.viper4android.ui.components.UiDimens
import com.llsl.viper4android.ui.components.resolvePresetName
import java.util.Locale
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt

private val EffectHorizontalPadding = UiDimens.Standard
private val DynamicEqTabHorizontalPadding = EffectHorizontalPadding
private val DynamicEqTabVerticalPadding = UiDimens.Large
private val DynamicEqTabIconSize = UiDimens.IconSmall
private val DynamicSystemButtonIconSize = EffectHorizontalPadding
private val DynamicSystemButtonIconSpacing = UiDimens.XSmall
private val DynamicSystemButtonSpacing = UiDimens.Medium
private val EffectCardHorizontalPadding = EffectHorizontalPadding
private val EffectCardVerticalPadding = UiDimens.XSmall
private val EffectContentPadding = EffectHorizontalPadding
private val EffectHeaderIconSize = UiDimens.IconMedium
private val EffectHeaderIconSpacing = UiDimens.Large
private val EffectHeaderHorizontalPadding = EffectHorizontalPadding
private val EffectHeaderVerticalPadding = UiDimens.Large
private val EffectSwitchPlaceholderHeight = UiDimens.SwitchSlotHeight

private fun scaleToDb(scale: Number): Double = 20.0 * log10(scale.toDouble())

private fun dbToScale(db: Double): Float = 10.0.pow(db / 20.0).toFloat()

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun EffectSection(
    title: String,
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    descriptionRes: Int,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    hasEnableSwitch: Boolean = true,
    toggleOnly: Boolean = false,
    initiallyExpanded: Boolean = false,
    content: @Composable () -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(initiallyExpanded) }
    var showHelpDialog by rememberSaveable { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current

    Card(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(
                    horizontal = EffectCardHorizontalPadding,
                    vertical = EffectCardVerticalPadding,
                ),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
    ) {
        Column {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .then(
                            if (toggleOnly) {
                                Modifier.combinedClickable(
                                    onClick = {},
                                    onLongClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        showHelpDialog = true
                                    },
                                )
                            } else {
                                Modifier.combinedClickable(
                                    onClick = { expanded = !expanded },
                                    onLongClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        showHelpDialog = true
                                    },
                                )
                            },
                        ).padding(
                            horizontal = EffectHeaderHorizontalPadding,
                            vertical = EffectHeaderVerticalPadding,
                        ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(EffectHeaderIconSize),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.width(EffectHeaderIconSpacing))
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                if (hasEnableSwitch) {
                    Switch(
                        checked = enabled,
                        onCheckedChange = onEnabledChange,
                    )
                } else {
                    Spacer(modifier = Modifier.height(EffectSwitchPlaceholderHeight))
                }
            }

            if (!toggleOnly) {
                AnimatedVisibility(
                    visible = expanded,
                    enter = expandVertically(),
                    exit = shrinkVertically(),
                ) {
                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(
                                    start = EffectContentPadding,
                                    end = EffectContentPadding,
                                    bottom = EffectContentPadding,
                                ),
                    ) {
                        content()
                    }
                }
            }
        }
    }

    if (showHelpDialog) {
        AlertDialog(
            modifier = Modifier.fillMaxWidth(0.9f),
            properties = DialogProperties(usePlatformDefaultWidth = false),
            onDismissRequest = { showHelpDialog = false },
            title = { Text(text = title) },
            text = {
                val maxHeight =
                    with(LocalDensity.current) {
                        LocalWindowInfo.current.containerSize.height
                            .toDp() / 2
                    }
                Column(
                    modifier =
                        Modifier
                            .heightIn(max = maxHeight)
                            .verticalScroll(rememberScrollState()),
                ) {
                    RichText(text = stringResource(descriptionRes))
                }
            },
            confirmButton = {
                TextButton(onClick = { showHelpDialog = false }) {
                    Text(text = stringResource(android.R.string.ok))
                }
            },
        )
    }
}

@Composable
fun MasterLimiterRows(
    state: EffectState,
    viewModel: MainViewModel,
) {
    val outputVolume = state.out.volume
    val channelPan = state.out.channelPan
    val limiter = state.out.limiter
    val gainDb = if (outputVolume > 0) scaleToDb(outputVolume) else -99.9
    val limDb = if (limiter > 0) scaleToDb(limiter) else -99.9
    val left = ((1.0f - channelPan) * 50.0f).roundToInt()
    val right = ((1.0f + channelPan) * 50.0f).roundToInt()
    EffectSection(
        title = stringResource(R.string.section_master_limiter),
        enabled = true,
        onEnabledChange = {},
        descriptionRes = R.string.effect_desc_master_limiter,
        icon = Icons.Default.Tune,
        hasEnableSwitch = false,
        initiallyExpanded = true,
    ) {
        LabeledSlider(
            label = stringResource(R.string.label_output_volume),
            value = outputVolume,
            onValueChange = { viewModel.applyPref(Effects.masterLimiter.outputVolume, it) },
            valueRange = 0.01f..2.0f,
            valueLabel = String.format(Locale.US, "%.1f dB", gainDb),
            edit =
                SliderEdit(
                    displayValue = gainDb,
                    displayRange = scaleToDb(0.01)..scaleToDb(2.0),
                    decimals = 1,
                    unit = "dB",
                    onCommit = { viewModel.applyPref(Effects.masterLimiter.outputVolume, dbToScale(it).coerceIn(0.01f, 2.0f)) },
                ),
        )
        LabeledSlider(
            label = stringResource(R.string.label_output_pan),
            value = channelPan,
            onValueChange = { viewModel.applyPref(Effects.masterLimiter.channelPan, it) },
            valueRange = -1.0f..1.0f,
            valueLabel = "$left:$right",
            edit =
                SliderEdit(
                    displayValue = channelPan.toDouble(),
                    displayRange = -1.0..1.0,
                    decimals = 2,
                    onCommit = { viewModel.applyPref(Effects.masterLimiter.channelPan, it.toFloat().coerceIn(-1.0f, 1.0f)) },
                ),
        )
        LabeledSlider(
            label = stringResource(R.string.label_output_limiter),
            value = limiter,
            onValueChange = { viewModel.applyPref(Effects.masterLimiter.threshold, it) },
            valueRange = 0.3f..1.0f,
            valueLabel = String.format(Locale.US, "%.1f dB", limDb),
            edit =
                SliderEdit(
                    displayValue = limDb,
                    displayRange = scaleToDb(0.3)..scaleToDb(1.0),
                    decimals = 1,
                    unit = "dB",
                    onCommit = { viewModel.applyPref(Effects.masterLimiter.threshold, dbToScale(it).coerceIn(0.3f, 1.0f)) },
                ),
        )
    }
}

@Composable
fun PlaybackGainSection(
    state: EffectState,
    viewModel: MainViewModel,
) {
    val vals = state.playbackGainControl
    val enabled = vals.enable
    val strength = vals.strength
    val maxGain = vals.maxGain
    val threshold = vals.outputThreshold

    EffectSection(
        title = stringResource(R.string.section_agc),
        enabled = enabled,
        onEnabledChange = viewModel::setPlaybackGainControlEnabled,
        descriptionRes = R.string.effect_desc_playback_gain_control,
        icon = Icons.AutoMirrored.Filled.TrendingUp,
    ) {
        LabeledSlider(
            label = stringResource(R.string.label_strength),
            value = strength,
            onValueChange = { viewModel.applyPref(Effects.playbackGainControl.strength, it) },
            valueRange = 0.5f..3.0f,
            valueLabel = String.format(Locale.US, "%.1fx", strength),
            edit =
                SliderEdit(
                    displayValue = strength.toDouble(),
                    displayRange = 0.5..3.0,
                    decimals = 1,
                    unit = "x",
                    onCommit = { viewModel.applyPref(Effects.playbackGainControl.strength, it.toFloat().coerceIn(0.5f, 3.0f)) },
                ),
        )
        LabeledSlider(
            label = stringResource(R.string.label_max_gain),
            value = maxGain,
            onValueChange = { viewModel.applyPref(Effects.playbackGainControl.maxGain, it) },
            valueRange = 1.0f..10.0f,
            valueLabel = String.format(Locale.US, "%.1fx", maxGain),
            edit =
                SliderEdit(
                    displayValue = maxGain.toDouble(),
                    displayRange = 1.0..10.0,
                    decimals = 1,
                    unit = "x",
                    onCommit = { viewModel.applyPref(Effects.playbackGainControl.maxGain, it.toFloat().coerceIn(1.0f, 10.0f)) },
                ),
        )
        val threshDb = if (threshold > 0) scaleToDb(threshold) else -99.9
        LabeledSlider(
            label = stringResource(R.string.label_agc_output_threshold),
            value = threshold,
            onValueChange = { viewModel.applyPref(Effects.playbackGainControl.outputThreshold, it) },
            valueRange = 0.3f..1.0f,
            valueLabel = String.format(Locale.US, "%.1f dB", threshDb),
            edit =
                SliderEdit(
                    displayValue = threshDb,
                    displayRange = scaleToDb(0.3)..scaleToDb(1.0),
                    decimals = 1,
                    unit = "dB",
                    onCommit = { viewModel.applyPref(Effects.playbackGainControl.outputThreshold, dbToScale(it).coerceIn(0.3f, 1.0f)) },
                ),
        )
    }
}

@Composable
fun LUFSTargetingSection(
    state: EffectState,
    viewModel: MainViewModel,
) {
    val vals = state.lufs
    val enabled = vals.enable
    val target = vals.target
    val maxGain = vals.maxGain
    val speed = vals.speed

    val speedNames =
        listOf(
            stringResource(R.string.label_lufs_speed_slow),
            stringResource(R.string.label_lufs_speed_medium),
            stringResource(R.string.label_lufs_speed_fast),
        )

    EffectSection(
        title = stringResource(R.string.section_lufs_targeting),
        enabled = enabled,
        onEnabledChange = viewModel::setLufsEnabled,
        descriptionRes = R.string.effect_desc_lufs_targeting,
        icon = Icons.Default.CrisisAlert,
    ) {
        LabeledSlider(
            label = stringResource(R.string.label_lufs_target_lufs),
            value = target,
            onValueChange = { viewModel.applyPref(Effects.lufs.target, it) },
            valueRange = -24f..-8f,
            valueLabel = String.format(Locale.US, "%.1f LUFS", target),
            edit =
                SliderEdit(
                    displayValue = target.toDouble(),
                    displayRange = -24.0..-8.0,
                    decimals = 1,
                    unit = "LUFS",
                    onCommit = { viewModel.applyPref(Effects.lufs.target, it.toFloat().coerceIn(-24.0f, -8.0f)) },
                ),
        )
        LabeledSlider(
            label = stringResource(R.string.label_max_gain),
            value = maxGain,
            onValueChange = { viewModel.applyPref(Effects.lufs.maxGain, it) },
            valueRange = 0f..12f,
            valueLabel = String.format(Locale.US, "%.1f dB", maxGain),
            edit =
                SliderEdit(
                    displayValue = maxGain.toDouble(),
                    displayRange = 0.0..12.0,
                    decimals = 1,
                    unit = "dB",
                    onCommit = { viewModel.applyPref(Effects.lufs.maxGain, it.toFloat().coerceIn(0.0f, 12.0f)) },
                ),
        )
        LabeledSlider(
            label = stringResource(R.string.label_lufs_speed),
            value = speed.toFloat(),
            onValueChange = { viewModel.applyPref(Effects.lufs.speed, it.roundToInt()) },
            valueRange = 0f..2f,
            steps = 1,
            valueLabel = speedNames.getOrElse(speed) { speedNames[1] },
        )
    }
}

@Composable
fun FetCompressorSection(
    state: EffectState,
    viewModel: MainViewModel,
) {
    val vals = state.fetCompressor
    val enabled = vals.enable
    val threshold = vals.threshold
    val ratio = vals.ratio
    val kneeAuto = vals.kneeAuto
    val knee = vals.knee
    val kneeMulti = vals.kneeMulti
    val gainAuto = vals.gainAuto
    val gain = vals.gain
    val attackAuto = vals.attackAuto
    val attack = vals.attack
    val maxAttack = vals.maxAttack
    val releaseAuto = vals.releaseAuto
    val release = vals.release
    val maxRelease = vals.maxRelease
    val crest = vals.crest
    val adapt = vals.adapt
    val noClip = vals.noClip
    val thresholdDb = compressorRawToDb(threshold)
    val ratioDisplay = compressorRawToRatio(ratio)
    val kneeDb = compressorRawToDb(knee)
    val gainDb = compressorRawToDb(gain)
    val attackMs = compressorSecondsToMs(attack)
    val maxAttackMs = compressorSecondsToMs(maxAttack)
    val releaseMs = compressorSecondsToMs(release)
    val maxReleaseMs = compressorSecondsToMs(maxRelease)
    val crestMs = compressorSecondsToMs(crest)

    EffectSection(
        title = stringResource(R.string.section_fet_compressor),
        enabled = enabled,
        onEnabledChange = viewModel::setFetCompressorEnabled,
        descriptionRes = R.string.effect_desc_fet_compressor,
        icon = Icons.Default.VerticalAlignCenter,
    ) {
        LabeledSlider(
            label = stringResource(R.string.label_threshold),
            value = thresholdDb,
            onValueChange = { viewModel.applyPref(Effects.fetCompressor.threshold, compressorDbToRaw(it)) },
            valueRange = -48f..0f,
            valueLabel = "${thresholdDb.roundToInt()} dB",
            edit =
                SliderEdit(
                    displayValue = thresholdDb.toDouble(),
                    displayRange = -48.0..0.0,
                    decimals = 0,
                    unit = "dB",
                    onCommit = {
                        viewModel.applyPref(
                            Effects.fetCompressor.threshold,
                            compressorDbToRaw(it.toFloat().coerceIn(-48.0f, 0.0f)),
                        )
                    },
                ),
        )
        LabeledSlider(
            label = stringResource(R.string.label_fet_ratio),
            value = ratioDisplay,
            onValueChange = { viewModel.applyPref(Effects.fetCompressor.ratio, compressorRatioToRaw(it)) },
            valueRange = 0f..2f,
            valueLabel = String.format(Locale.US, "%.1f", ratioDisplay),
            edit =
                SliderEdit(
                    displayValue = ratioDisplay.toDouble(),
                    displayRange = 0.0..2.0,
                    decimals = 1,
                    onCommit = {
                        viewModel.applyPref(
                            Effects.fetCompressor.ratio,
                            compressorRatioToRaw(it.toFloat().coerceIn(0.0f, 2.0f)),
                        )
                    },
                ),
        )
        LabeledSwitch(
            label = stringResource(R.string.label_fet_auto_knee),
            checked = kneeAuto,
            onCheckedChange = { viewModel.applyPref(Effects.fetCompressor.kneeAuto, it) },
        )
        LabeledSlider(
            label = stringResource(R.string.label_fet_knee),
            value = kneeDb,
            onValueChange = { viewModel.applyPref(Effects.fetCompressor.knee, compressorDbToRaw(it)) },
            valueRange = 0f..12f,
            enabled = !kneeAuto,
            valueLabel = "${kneeDb.roundToInt()} dB",
            edit =
                SliderEdit(
                    displayValue = kneeDb.toDouble(),
                    displayRange = 0.0..12.0,
                    decimals = 0,
                    unit = "dB",
                    onCommit = { viewModel.applyPref(Effects.fetCompressor.knee, compressorDbToRaw(it.toFloat().coerceIn(0.0f, 12.0f))) },
                ),
        )
        LabeledSlider(
            label = stringResource(R.string.label_fet_knee_multi),
            value = kneeMulti,
            onValueChange = { viewModel.applyPref(Effects.fetCompressor.kneeMulti, it) },
            valueRange = 0f..4f,
            valueLabel = String.format(Locale.US, "%.1fx", kneeMulti),
            edit =
                SliderEdit(
                    displayValue = kneeMulti.toDouble(),
                    displayRange = 0.0..4.0,
                    decimals = 1,
                    unit = "x",
                    onCommit = { viewModel.applyPref(Effects.fetCompressor.kneeMulti, it.toFloat().coerceIn(0.0f, 4.0f)) },
                ),
        )
        LabeledSwitch(
            label = stringResource(R.string.label_fet_auto_gain),
            checked = gainAuto,
            onCheckedChange = { viewModel.applyPref(Effects.fetCompressor.gainAuto, it) },
        )
        LabeledSlider(
            label = stringResource(R.string.label_gain),
            value = gainDb,
            onValueChange = { viewModel.applyPref(Effects.fetCompressor.gain, compressorDbToRaw(it)) },
            valueRange = 0f..24f,
            enabled = !gainAuto,
            valueLabel = "${gainDb.roundToInt()} dB",
            edit =
                SliderEdit(
                    displayValue = gainDb.toDouble(),
                    displayRange = 0.0..24.0,
                    decimals = 0,
                    unit = "dB",
                    onCommit = { viewModel.applyPref(Effects.fetCompressor.gain, compressorDbToRaw(it.toFloat().coerceIn(0.0f, 24.0f))) },
                ),
        )
        LabeledSwitch(
            label = stringResource(R.string.label_fet_auto_attack),
            checked = attackAuto,
            onCheckedChange = { viewModel.applyPref(Effects.fetCompressor.attackAuto, it) },
        )
        LabeledSlider(
            label = stringResource(R.string.label_attack),
            value = attackMs.coerceIn(1f, 100f),
            onValueChange = { viewModel.applyPref(Effects.fetCompressor.attack, compressorMsToSeconds(it)) },
            valueRange = 1f..100f,
            enabled = !attackAuto,
            valueLabel = "${attackMs.roundToInt()} ms",
            edit =
                SliderEdit(
                    displayValue = attackMs.toDouble(),
                    displayRange = 1.0..100.0,
                    decimals = 0,
                    unit = "ms",
                    onCommit = {
                        viewModel.applyPref(
                            Effects.fetCompressor.attack,
                            compressorMsToSeconds(it.toFloat().coerceIn(1.0f, 100.0f)),
                        )
                    },
                ),
        )
        LabeledSlider(
            label = stringResource(R.string.label_fet_max_attack),
            value = maxAttackMs.coerceIn(1f, 100f),
            onValueChange = { viewModel.applyPref(Effects.fetCompressor.maxAttack, compressorMsToSeconds(it)) },
            valueRange = 1f..100f,
            valueLabel = "${maxAttackMs.roundToInt()} ms",
            edit =
                SliderEdit(
                    displayValue = maxAttackMs.toDouble(),
                    displayRange = 1.0..100.0,
                    decimals = 0,
                    unit = "ms",
                    onCommit = {
                        viewModel.applyPref(
                            Effects.fetCompressor.maxAttack,
                            compressorMsToSeconds(it.toFloat().coerceIn(1.0f, 100.0f)),
                        )
                    },
                ),
        )
        LabeledSwitch(
            label = stringResource(R.string.label_fet_auto_release),
            checked = releaseAuto,
            onCheckedChange = { viewModel.applyPref(Effects.fetCompressor.releaseAuto, it) },
        )
        LabeledSlider(
            label = stringResource(R.string.label_release),
            value = releaseMs.coerceIn(5f, 500f),
            onValueChange = { viewModel.applyPref(Effects.fetCompressor.release, compressorMsToSeconds(it)) },
            valueRange = 5f..500f,
            enabled = !releaseAuto,
            valueLabel = "${releaseMs.roundToInt()} ms",
            edit =
                SliderEdit(
                    displayValue = releaseMs.toDouble(),
                    displayRange = 5.0..500.0,
                    decimals = 0,
                    unit = "ms",
                    onCommit = {
                        viewModel.applyPref(
                            Effects.fetCompressor.release,
                            compressorMsToSeconds(it.toFloat().coerceIn(5.0f, 500.0f)),
                        )
                    },
                ),
        )
        LabeledSlider(
            label = stringResource(R.string.label_fet_max_release),
            value = maxReleaseMs.coerceIn(5f, 500f),
            onValueChange = { viewModel.applyPref(Effects.fetCompressor.maxRelease, compressorMsToSeconds(it)) },
            valueRange = 5f..500f,
            valueLabel = "${maxReleaseMs.roundToInt()} ms",
            edit =
                SliderEdit(
                    displayValue = maxReleaseMs.toDouble(),
                    displayRange = 5.0..500.0,
                    decimals = 0,
                    unit = "ms",
                    onCommit = {
                        viewModel.applyPref(
                            Effects.fetCompressor.maxRelease,
                            compressorMsToSeconds(it.toFloat().coerceIn(5.0f, 500.0f)),
                        )
                    },
                ),
        )
        LabeledSlider(
            label = stringResource(R.string.label_fet_crest),
            value = crestMs.coerceIn(5f, 300f),
            onValueChange = { viewModel.applyPref(Effects.fetCompressor.crest, compressorMsToSeconds(it)) },
            valueRange = 5f..300f,
            valueLabel = "${crestMs.roundToInt()} ms",
            edit =
                SliderEdit(
                    displayValue = crestMs.toDouble(),
                    displayRange = 5.0..300.0,
                    decimals = 0,
                    unit = "ms",
                    onCommit = {
                        viewModel.applyPref(
                            Effects.fetCompressor.crest,
                            compressorMsToSeconds(it.toFloat().coerceIn(5.0f, 300.0f)),
                        )
                    },
                ),
        )
        LabeledSlider(
            label = stringResource(R.string.label_fet_adapt),
            value = adapt,
            onValueChange = { viewModel.applyPref(Effects.fetCompressor.adapt, it) },
            valueRange = 1f..16f,
            valueLabel = String.format(Locale.US, "%.1fs", adapt),
            edit =
                SliderEdit(
                    displayValue = adapt.toDouble(),
                    displayRange = 1.0..16.0,
                    decimals = 1,
                    unit = "s",
                    onCommit = { viewModel.applyPref(Effects.fetCompressor.adapt, it.toFloat().coerceIn(1.0f, 16.0f)) },
                ),
        )
        LabeledSwitch(
            label = stringResource(R.string.label_fet_no_clip),
            checked = noClip,
            onCheckedChange = { viewModel.applyPref(Effects.fetCompressor.noClip, it) },
        )
    }
}

@Composable
fun MultibandCompressorSection(
    state: EffectState,
    viewModel: MainViewModel,
) {
    val multibandCompressorVals = state.multibandCompressor
    val enabled = multibandCompressorVals.enable

    val crossoverDefaults = listOf(120, 500, 4000, 8000)
    val bandEnables = multibandCompressorVals.bandEnables
    val crossovers = multibandCompressorVals.crossovers

    val thresholds = multibandCompressorVals.thresholds
    val ratios = multibandCompressorVals.ratios
    val gains = multibandCompressorVals.gains
    val knees = multibandCompressorVals.knees
    val kneeMultis = multibandCompressorVals.kneeMultis
    val attacks = multibandCompressorVals.attacks
    val maxAttacks = multibandCompressorVals.maxAttacks
    val releases = multibandCompressorVals.releases
    val maxReleases = multibandCompressorVals.maxReleases
    val crests = multibandCompressorVals.crests
    val adapts = multibandCompressorVals.adapts
    val kneeAutos = multibandCompressorVals.kneeAutos
    val gainAutos = multibandCompressorVals.gainAutos
    val attackAutos = multibandCompressorVals.attackAutos
    val releaseAutos = multibandCompressorVals.releaseAutos
    val noClips = multibandCompressorVals.noClips

    var selectedTab by remember { mutableIntStateOf(0) }
    val tabNames = listOf("Sub", "Low", "Mid", "Pres", "Air")
    val b = selectedTab

    val threshold = thresholds.getOrElse(b) { compressorDbToRaw(-18.0f) }
    val ratio = ratios.getOrElse(b) { compressorRatioToRaw(0.5f) }
    val gain = gains.getOrElse(b) { 0.0f }
    val knee = knees.getOrElse(b) { 0.0f }
    val kneeMulti = kneeMultis.getOrElse(b) { 0.0f }
    val attack = attacks.getOrElse(b) { compressorMsToSeconds(1.0f) }
    val maxAttack = maxAttacks.getOrElse(b) { compressorMsToSeconds(44.0f) }
    val release = releases.getOrElse(b) { compressorMsToSeconds(100.0f) }
    val maxRelease = maxReleases.getOrElse(b) { compressorMsToSeconds(200.0f) }
    val crest = crests.getOrElse(b) { compressorMsToSeconds(100.0f) }
    val adapt = adapts.getOrElse(b) { compressorAdaptAmountToSeconds(0.5f) }
    val bandEnabled = bandEnables.getOrElse(b) { true }
    val kneeAuto = kneeAutos.getOrElse(b) { true }
    val gainAuto = gainAutos.getOrElse(b) { true }
    val attackAuto = attackAutos.getOrElse(b) { true }
    val releaseAuto = releaseAutos.getOrElse(b) { true }
    val noClip = noClips.getOrElse(b) { true }
    val thresholdDb = compressorRawToDb(threshold)
    val ratioDisplay = compressorRawToRatio(ratio)
    val kneeDb = compressorRawToDb(knee)
    val gainDb = compressorRawToDb(gain)
    val attackMs = compressorSecondsToMs(attack)
    val maxAttackMs = compressorSecondsToMs(maxAttack)
    val releaseMs = compressorSecondsToMs(release)
    val maxReleaseMs = compressorSecondsToMs(maxRelease)
    val crestMs = compressorSecondsToMs(crest)

    val onBandEnableChange: (Boolean) -> Unit = { viewModel.applyBandPref(Effects.multibandCompressor.bandEnables, b, it) }
    val onCrossoverChange: (Int) -> Unit = { viewModel.applyBandPref(Effects.multibandCompressor.crossovers, b, it) }
    val onThresholdChange: (Float) -> Unit = { viewModel.applyBandPref(Effects.multibandCompressor.thresholds, b, it) }
    val onRatioChange: (Float) -> Unit = { viewModel.applyBandPref(Effects.multibandCompressor.ratios, b, it) }
    val onAutoKneeChange: (Boolean) -> Unit = { viewModel.applyBandPref(Effects.multibandCompressor.kneeAutos, b, it) }
    val onKneeChange: (Float) -> Unit = { viewModel.applyBandPref(Effects.multibandCompressor.knees, b, it) }
    val onKneeMultiChange: (Float) -> Unit = { viewModel.applyBandPref(Effects.multibandCompressor.kneeMultis, b, it) }
    val onAutoGainChange: (Boolean) -> Unit = { viewModel.applyBandPref(Effects.multibandCompressor.gainAutos, b, it) }
    val onGainChange: (Float) -> Unit = { viewModel.applyBandPref(Effects.multibandCompressor.gains, b, it) }
    val onAutoAttackChange: (Boolean) -> Unit = { viewModel.applyBandPref(Effects.multibandCompressor.attackAutos, b, it) }
    val onAttackChange: (Float) -> Unit = { viewModel.applyBandPref(Effects.multibandCompressor.attacks, b, it) }
    val onMaxAttackChange: (Float) -> Unit = { viewModel.applyBandPref(Effects.multibandCompressor.maxAttacks, b, it) }
    val onAutoReleaseChange: (Boolean) -> Unit = { viewModel.applyBandPref(Effects.multibandCompressor.releaseAutos, b, it) }
    val onReleaseChange: (Float) -> Unit = { viewModel.applyBandPref(Effects.multibandCompressor.releases, b, it) }
    val onMaxReleaseChange: (Float) -> Unit = { viewModel.applyBandPref(Effects.multibandCompressor.maxReleases, b, it) }
    val onCrestChange: (Float) -> Unit = { viewModel.applyBandPref(Effects.multibandCompressor.crests, b, it) }
    val onAdaptChange: (Float) -> Unit = { viewModel.applyBandPref(Effects.multibandCompressor.adapts, b, it) }
    val onNoClipChange: (Boolean) -> Unit = { viewModel.applyBandPref(Effects.multibandCompressor.noClips, b, it) }

    EffectSection(
        title = stringResource(R.string.section_multiband_compressor),
        enabled = enabled,
        onEnabledChange = viewModel::setMultibandCompressorEnabled,
        descriptionRes = R.string.effect_desc_multiband_compressor,
        icon = Icons.Default.Compress,
    ) {
        PrimaryTabRow(selectedTabIndex = selectedTab) {
            tabNames.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = { Text(title) },
                )
            }
        }

        val lowFreq =
            if (b == 0) 20 else crossovers.getOrElse(b - 1) { crossoverDefaults.getOrElse(b - 1) { 20 } }
        val highFreq =
            if (b < 4) crossovers.getOrElse(b) { crossoverDefaults.getOrElse(b) { 20000 } } else 20000
        Text(
            text = "$lowFreq - ${if (b < 4) "$highFreq" else "20000+"} Hz",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = UiDimens.Medium, bottom = UiDimens.XSmall),
        )

        LabeledSwitch(
            label = stringResource(R.string.label_band_enabled),
            checked = bandEnabled,
            onCheckedChange = onBandEnableChange,
        )

        if (b < 4) {
            val crossoverHardMin = intArrayOf(30, 80, 300, 2000)
            val crossoverHardMax = intArrayOf(300, 2000, 8000, 16000)
            val neighborMin =
                if (b > 0) crossovers.getOrElse(b - 1) { crossoverDefaults.getOrElse(b - 1) { 20 } } + 50 else 30
            val neighborMax =
                if (b < 3) crossovers.getOrElse(b + 1) { crossoverDefaults.getOrElse(b + 1) { 20000 } } - 50 else 16000
            val minCrossover = maxOf(crossoverHardMin[b], neighborMin)
            val maxCrossover = minOf(crossoverHardMax[b], neighborMax)
            LabeledSlider(
                label = stringResource(R.string.label_upper_crossover),
                value =
                    crossovers
                        .getOrElse(b) { crossoverDefaults[b] }
                        .toFloat()
                        .coerceIn(minCrossover.toFloat(), maxCrossover.toFloat()),
                onValueChange = { onCrossoverChange(it.roundToInt()) },
                valueRange = minCrossover.toFloat()..maxCrossover.toFloat(),
                steps = ((maxCrossover - minCrossover) / 5).coerceAtLeast(1) - 1,
                valueLabel = "${crossovers.getOrElse(b) { crossoverDefaults[b] }} Hz",
                edit =
                    SliderEdit(
                        displayValue = crossovers.getOrElse(b) { crossoverDefaults[b] }.toDouble(),
                        displayRange = minCrossover.toDouble()..maxCrossover.toDouble(),
                        decimals = 0,
                        unit = "Hz",
                        onCommit = { onCrossoverChange(it.roundToInt().coerceIn(minCrossover, maxCrossover)) },
                    ),
            )
        }

        LabeledSlider(
            label = stringResource(R.string.label_threshold),
            value = thresholdDb,
            onValueChange = { onThresholdChange(compressorDbToRaw(it)) },
            valueRange = -48f..0f,
            valueLabel = "${thresholdDb.roundToInt()} dB",
            edit =
                SliderEdit(
                    displayValue = thresholdDb.toDouble(),
                    displayRange = -48.0..0.0,
                    decimals = 0,
                    unit = "dB",
                    onCommit = { onThresholdChange(compressorDbToRaw(it.toFloat().coerceIn(-48.0f, 0.0f))) },
                ),
        )
        LabeledSlider(
            label = stringResource(R.string.label_fet_ratio),
            value = ratioDisplay,
            onValueChange = { onRatioChange(compressorRatioToRaw(it)) },
            valueRange = 0f..2f,
            valueLabel = String.format(Locale.US, "%.1f", ratioDisplay),
            edit =
                SliderEdit(
                    displayValue = ratioDisplay.toDouble(),
                    displayRange = 0.0..2.0,
                    decimals = 1,
                    onCommit = { onRatioChange(compressorRatioToRaw(it.toFloat().coerceIn(0.0f, 2.0f))) },
                ),
        )
        LabeledSwitch(
            label = stringResource(R.string.label_fet_auto_knee),
            checked = kneeAuto,
            onCheckedChange = onAutoKneeChange,
        )
        LabeledSlider(
            label = stringResource(R.string.label_fet_knee),
            value = kneeDb,
            onValueChange = { onKneeChange(compressorDbToRaw(it)) },
            valueRange = 0f..12f,
            enabled = !kneeAuto,
            valueLabel = "${kneeDb.roundToInt()} dB",
            edit =
                SliderEdit(
                    displayValue = kneeDb.toDouble(),
                    displayRange = 0.0..12.0,
                    decimals = 0,
                    unit = "dB",
                    onCommit = { onKneeChange(compressorDbToRaw(it.toFloat().coerceIn(0.0f, 12.0f))) },
                ),
        )
        LabeledSlider(
            label = stringResource(R.string.label_fet_knee_multi),
            value = kneeMulti,
            onValueChange = { onKneeMultiChange(it) },
            valueRange = 0f..4f,
            valueLabel = String.format(Locale.US, "%.1fx", kneeMulti),
            edit =
                SliderEdit(
                    displayValue = kneeMulti.toDouble(),
                    displayRange = 0.0..4.0,
                    decimals = 1,
                    unit = "x",
                    onCommit = { onKneeMultiChange(it.toFloat().coerceIn(0.0f, 4.0f)) },
                ),
        )
        LabeledSwitch(
            label = stringResource(R.string.label_fet_auto_gain),
            checked = gainAuto,
            onCheckedChange = onAutoGainChange,
        )
        LabeledSlider(
            label = stringResource(R.string.label_gain),
            value = gainDb,
            onValueChange = { onGainChange(compressorDbToRaw(it)) },
            valueRange = 0f..24f,
            enabled = !gainAuto,
            valueLabel = "${gainDb.roundToInt()} dB",
            edit =
                SliderEdit(
                    displayValue = gainDb.toDouble(),
                    displayRange = 0.0..24.0,
                    decimals = 0,
                    unit = "dB",
                    onCommit = { onGainChange(compressorDbToRaw(it.toFloat().coerceIn(0.0f, 24.0f))) },
                ),
        )
        LabeledSwitch(
            label = stringResource(R.string.label_fet_auto_attack),
            checked = attackAuto,
            onCheckedChange = onAutoAttackChange,
        )
        LabeledSlider(
            label = stringResource(R.string.label_attack),
            value = attackMs.coerceIn(1f, 100f),
            onValueChange = { onAttackChange(compressorMsToSeconds(it)) },
            valueRange = 1f..100f,
            enabled = !attackAuto,
            valueLabel = "${attackMs.roundToInt()} ms",
            edit =
                SliderEdit(
                    displayValue = attackMs.toDouble(),
                    displayRange = 1.0..100.0,
                    decimals = 0,
                    unit = "ms",
                    onCommit = { onAttackChange(compressorMsToSeconds(it.toFloat().coerceIn(1.0f, 100.0f))) },
                ),
        )
        LabeledSlider(
            label = stringResource(R.string.label_fet_max_attack),
            value = maxAttackMs.coerceIn(1f, 100f),
            onValueChange = { onMaxAttackChange(compressorMsToSeconds(it)) },
            valueRange = 1f..100f,
            valueLabel = "${maxAttackMs.roundToInt()} ms",
            edit =
                SliderEdit(
                    displayValue = maxAttackMs.toDouble(),
                    displayRange = 1.0..100.0,
                    decimals = 0,
                    unit = "ms",
                    onCommit = { onMaxAttackChange(compressorMsToSeconds(it.toFloat().coerceIn(1.0f, 100.0f))) },
                ),
        )
        LabeledSwitch(
            label = stringResource(R.string.label_fet_auto_release),
            checked = releaseAuto,
            onCheckedChange = onAutoReleaseChange,
        )
        LabeledSlider(
            label = stringResource(R.string.label_release),
            value = releaseMs.coerceIn(5f, 500f),
            onValueChange = { onReleaseChange(compressorMsToSeconds(it)) },
            valueRange = 5f..500f,
            enabled = !releaseAuto,
            valueLabel = "${releaseMs.roundToInt()} ms",
            edit =
                SliderEdit(
                    displayValue = releaseMs.toDouble(),
                    displayRange = 5.0..500.0,
                    decimals = 0,
                    unit = "ms",
                    onCommit = { onReleaseChange(compressorMsToSeconds(it.toFloat().coerceIn(5.0f, 500.0f))) },
                ),
        )
        LabeledSlider(
            label = stringResource(R.string.label_fet_max_release),
            value = maxReleaseMs.coerceIn(5f, 500f),
            onValueChange = { onMaxReleaseChange(compressorMsToSeconds(it)) },
            valueRange = 5f..500f,
            valueLabel = "${maxReleaseMs.roundToInt()} ms",
            edit =
                SliderEdit(
                    displayValue = maxReleaseMs.toDouble(),
                    displayRange = 5.0..500.0,
                    decimals = 0,
                    unit = "ms",
                    onCommit = { onMaxReleaseChange(compressorMsToSeconds(it.toFloat().coerceIn(5.0f, 500.0f))) },
                ),
        )
        LabeledSlider(
            label = stringResource(R.string.label_fet_crest),
            value = crestMs.coerceIn(5f, 300f),
            onValueChange = { onCrestChange(compressorMsToSeconds(it)) },
            valueRange = 5f..300f,
            valueLabel = "${crestMs.roundToInt()} ms",
            edit =
                SliderEdit(
                    displayValue = crestMs.toDouble(),
                    displayRange = 5.0..300.0,
                    decimals = 0,
                    unit = "ms",
                    onCommit = { onCrestChange(compressorMsToSeconds(it.toFloat().coerceIn(5.0f, 300.0f))) },
                ),
        )
        LabeledSlider(
            label = stringResource(R.string.label_fet_adapt),
            value = adapt,
            onValueChange = { onAdaptChange(it) },
            valueRange = 1f..16f,
            valueLabel = String.format(Locale.US, "%.1fs", adapt),
            edit =
                SliderEdit(
                    displayValue = adapt.toDouble(),
                    displayRange = 1.0..16.0,
                    decimals = 1,
                    unit = "s",
                    onCommit = { onAdaptChange(it.toFloat().coerceIn(1.0f, 16.0f)) },
                ),
        )
        LabeledSwitch(
            label = stringResource(R.string.label_fet_no_clip),
            checked = noClip,
            onCheckedChange = onNoClipChange,
        )
    }
}

@Composable
fun DdcSection(
    state: EffectState,
    viewModel: MainViewModel,
) {
    val vals = state.ddc
    val enabled = vals.enable
    val device = vals.device

    val vdcFiles by viewModel.vdcFileList.collectAsStateWithLifecycle()
    val vdcNoneLabel = stringResource(R.string.label_none)
    val cdvOptions = listOf(vdcNoneLabel) + vdcFiles

    EffectSection(
        title = stringResource(R.string.section_ddc),
        enabled = enabled,
        onEnabledChange = viewModel::setDdcEnabled,
        descriptionRes = R.string.effect_desc_ddc,
        icon = Icons.Default.SettingsInputComponent,
    ) {
        LabeledDropdown(
            label = stringResource(R.string.label_ddc_device),
            selectedValue = device.ifEmpty { vdcNoneLabel },
            options = cdvOptions,
            onOptionSelected = { _, value ->
                viewModel.setDdcDevice(if (value == vdcNoneLabel) "" else value)
            },
            onDeleteOption = { _, name -> viewModel.deleteVdcFile(name) },
            isOptionDeletable = { _, name -> name != vdcNoneLabel },
        )
    }
}

@Composable
fun SpectrumExtensionSection(
    state: EffectState,
    viewModel: MainViewModel,
) {
    val vals = state.spectrumExtension
    val enabled = vals.enable
    val strength = vals.strength
    val exciter = vals.exciter

    EffectSection(
        title = stringResource(R.string.section_spectrum_extension),
        enabled = enabled,
        onEnabledChange = viewModel::setSpectrumExtensionEnabled,
        descriptionRes = R.string.effect_desc_spectrum_extension,
        icon = Icons.Default.Waves,
    ) {
        LabeledSlider(
            label = stringResource(R.string.label_strength),
            value = strength.toFloat(),
            onValueChange = { viewModel.applyPref(Effects.spectrumExtension.strength, it.roundToInt()) },
            valueRange = 2200f..8200f,
            steps = 1199,
            valueLabel = "$strength Hz",
            edit =
                SliderEdit(
                    displayValue = strength.toDouble(),
                    displayRange = 2200.0..8200.0,
                    decimals = 0,
                    unit = "Hz",
                    onCommit = { viewModel.applyPref(Effects.spectrumExtension.strength, it.roundToInt().coerceIn(2200, 8200)) },
                ),
        )
        LabeledSlider(
            label = stringResource(R.string.label_vse_exciter),
            value = exciter,
            onValueChange = { viewModel.applyPref(Effects.spectrumExtension.exciter, it) },
            valueRange = 0f..6f,
            valueLabel = String.format(Locale.US, "%.1fx", exciter),
            edit =
                SliderEdit(
                    displayValue = exciter.toDouble(),
                    displayRange = 0.0..6.0,
                    decimals = 1,
                    unit = "x",
                    onCommit = { viewModel.applyPref(Effects.spectrumExtension.exciter, it.toFloat().coerceIn(0.0f, 6.0f)) },
                ),
        )
    }
}

@Composable
fun EqualizerSection(
    state: EffectState,
    viewModel: MainViewModel,
) {
    val eqVals = state.eq
    val enabled = eqVals.enable
    val bandCount = eqVals.bandCount
    val presetId = eqVals.presetId
    val eqPresets = eqVals.presets

    val bands: List<Float> =
        remember(eqVals.bands) {
            eqVals.bands.map { it.toFloat() }
        }

    val onEnabledChange = viewModel::setEqEnabled
    val onBandCountChange = viewModel::setEqBandCount
    val onPresetSelect = viewModel::setEqPreset
    val onBandsChange = viewModel::setEqBands
    val onPresetAdd = viewModel::addEqPreset
    val onPresetDelete = viewModel::deleteEqPreset
    val onReset = viewModel::resetEqBands

    EffectSection(
        title = stringResource(R.string.section_equalizer),
        enabled = enabled,
        onEnabledChange = onEnabledChange,
        descriptionRes = R.string.effect_desc_equalizer,
        icon = Icons.Default.Equalizer,
    ) {
        var showEqDialog by remember { mutableStateOf(false) }

        val bandCounts = listOf(10, 15, 25, 31)
        val bandCountOptions = bandCounts.map { stringResource(R.string.label_eq_n_bands, it) }
        val bandCountIndex =
            when (bandCount) {
                15 -> 1
                25 -> 2
                31 -> 3
                else -> 0
            }
        LabeledDropdown(
            label = stringResource(R.string.label_eq_bands),
            selectedValue = bandCountOptions[bandCountIndex],
            options = bandCountOptions,
            onOptionSelected = { index, _ -> onBandCountChange(bandCounts[index]) },
        )

        if (bands.size >= bandCount) {
            EqCurveGraph(
                bands = bands,
                onClick = { showEqDialog = true },
                bandCount = bandCount,
            )
        }

        if (showEqDialog) {
            EqEditDialog(
                bands = bands,
                onBandsChange = onBandsChange,
                presetId = presetId,
                presets = eqPresets,
                onPresetSelect = onPresetSelect,
                onPresetAdd = onPresetAdd,
                onPresetDelete = onPresetDelete,
                onReset = onReset,
                onDismiss = { showEqDialog = false },
                bandCount = bandCount,
            )
        }
    }
}

@Composable
fun DynamicEqSection(
    state: EffectState,
    viewModel: MainViewModel,
) {
    val dynVals = state.dynamicEq
    val enabled = dynVals.enable
    val bandCount = dynVals.bandCount

    val freqs = dynVals.freqs.take(bandCount)
    val qs = dynVals.qs.take(bandCount)
    val gains = dynVals.gains.take(bandCount)
    val thresholds = dynVals.thresholds.take(bandCount)
    val attacks = dynVals.attacks.take(bandCount)
    val releases = dynVals.releases.take(bandCount)
    val filterTypes = dynVals.filterTypes.take(bandCount)

    var selectedTab by remember { mutableIntStateOf(0) }
    val safeTab = if (bandCount > 0) selectedTab.coerceIn(0, bandCount - 1) else 0
    var deleteBandIndex by remember { mutableIntStateOf(-1) }

    fun formatFreq(hz: Int): String =
        when {
            hz >= 1000 -> "${hz / 1000}kHz"
            else -> "${hz}Hz"
        }

    if (deleteBandIndex >= 0) {
        ConfirmDialog(
            title = stringResource(R.string.dialog_delete_band),
            body =
                stringResource(
                    R.string.dialog_delete_band_confirm,
                    formatFreq(freqs.getOrElse(deleteBandIndex) { 1000 }),
                ),
            confirmLabel = stringResource(R.string.action_delete),
            destructive = true,
            onConfirm = {
                val i = deleteBandIndex
                deleteBandIndex = -1
                viewModel.removeDynamicEqBand(i)
                if (selectedTab >= bandCount - 1) selectedTab = maxOf(0, bandCount - 2)
            },
            onDismiss = { deleteBandIndex = -1 },
        )
    }

    EffectSection(
        title = stringResource(R.string.section_dynamic_eq),
        enabled = enabled,
        onEnabledChange = viewModel::setDynamicEqEnabled,
        descriptionRes = R.string.effect_desc_dynamic_eq,
        icon = Icons.Default.Insights,
    ) {
        PrimaryScrollableTabRow(
            selectedTabIndex = safeTab,
            edgePadding = UiDimens.None,
        ) {
            for (i in 0 until bandCount) {
                val isSelected = safeTab == i
                val color =
                    if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                Box(
                    contentAlignment = Alignment.Center,
                    modifier =
                        Modifier
                            .combinedClickable(
                                onClick = { selectedTab = i },
                                onLongClick = { if (bandCount > 1) deleteBandIndex = i },
                            ).padding(
                                horizontal = DynamicEqTabHorizontalPadding,
                                vertical = DynamicEqTabVerticalPadding,
                            ),
                ) {
                    Text(
                        text = formatFreq(freqs.getOrElse(i) { 1000 }),
                        color = color,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
            val lastFreq = if (bandCount > 0) freqs.getOrElse(bandCount - 1) { 0 } else 0
            if (bandCount < 10 && lastFreq < 20000) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier =
                        Modifier
                            .clickable {
                                viewModel.addDynamicEqBand()
                                selectedTab = bandCount
                            }.padding(
                                horizontal = DynamicEqTabHorizontalPadding,
                                vertical = DynamicEqTabVerticalPadding,
                            ),
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(DynamicEqTabIconSize),
                    )
                }
            }
        }

        if (bandCount > 0) {
            val freq = freqs.getOrElse(safeTab) { 1000 }
            val q = qs.getOrElse(safeTab) { 1.5f }
            val gain = gains.getOrElse(safeTab) { 0.0f }
            val threshold = thresholds.getOrElse(safeTab) { -30.0f }
            val attack = attacks.getOrElse(safeTab) { 10.0f }
            val release = releases.getOrElse(safeTab) { 100.0f }
            val filterType = filterTypes.getOrElse(safeTab) { 0 }.coerceIn(0, 2)

            val minFreq =
                if (safeTab > 0) (freqs.getOrElse(safeTab - 1) { 20 } + 5).toFloat() else 20f
            val maxFreq =
                if (safeTab < bandCount - 1) (freqs.getOrElse(safeTab + 1) { 20000 } - 5).toFloat() else 20000f

            val onFreqChange: (Int) -> Unit = { viewModel.applyBandPref(Effects.dynamicEq.freqs, safeTab, it, bandCount) }
            val onQChange: (Float) -> Unit = { viewModel.applyBandPref(Effects.dynamicEq.qs, safeTab, it, bandCount) }
            val onGainChange: (Float) -> Unit = { viewModel.applyBandPref(Effects.dynamicEq.gains, safeTab, it, bandCount) }
            val onThresholdChange: (Float) -> Unit =
                { viewModel.applyBandPref(Effects.dynamicEq.thresholds, safeTab, it, bandCount) }
            val onAttackChange: (Float) -> Unit = { viewModel.applyBandPref(Effects.dynamicEq.attacks, safeTab, it, bandCount) }
            val onReleaseChange: (Float) -> Unit = { viewModel.applyBandPref(Effects.dynamicEq.releases, safeTab, it, bandCount) }
            val onFilterTypeChange: (Int) -> Unit =
                { viewModel.applyBandPref(Effects.dynamicEq.filterTypes, safeTab, it, bandCount) }

            LabeledSlider(
                label = stringResource(R.string.label_frequency),
                value = freq.toFloat(),
                onValueChange = { onFreqChange(it.roundToInt()) },
                valueRange = minFreq..maxFreq,
                steps = ((maxFreq - minFreq) / 5f).toInt().coerceAtLeast(1) - 1,
                valueLabel = "$freq Hz",
                edit =
                    SliderEdit(
                        displayValue = freq.toDouble(),
                        displayRange = minFreq.toDouble()..maxFreq.toDouble(),
                        decimals = 0,
                        unit = "Hz",
                        onCommit = { onFreqChange(it.roundToInt().coerceIn(minFreq.roundToInt(), maxFreq.roundToInt())) },
                    ),
            )
            LabeledSlider(
                label = stringResource(R.string.label_dynamic_eq_q_factor),
                value = q,
                onValueChange = { onQChange(it) },
                valueRange = 0.5f..8.0f,
                valueLabel = String.format(Locale.US, "%.1f", q),
                edit =
                    SliderEdit(
                        displayValue = q.toDouble(),
                        displayRange = 0.5..8.0,
                        decimals = 1,
                        onCommit = { onQChange(it.toFloat().coerceIn(0.5f, 8.0f)) },
                    ),
            )
            LabeledSlider(
                label = stringResource(R.string.label_dynamic_eq_target_gain),
                value = gain,
                onValueChange = { onGainChange(it) },
                valueRange = -12f..12f,
                valueLabel = String.format(Locale.US, "%.1f dB", gain),
                edit =
                    SliderEdit(
                        displayValue = gain.toDouble(),
                        displayRange = -12.0..12.0,
                        decimals = 1,
                        unit = "dB",
                        onCommit = { onGainChange(it.toFloat().coerceIn(-12.0f, 12.0f)) },
                    ),
            )
            LabeledSlider(
                label = stringResource(R.string.label_threshold),
                value = threshold,
                onValueChange = { onThresholdChange(it) },
                valueRange = -80f..0f,
                valueLabel = String.format(Locale.US, "%.1f dB", threshold),
                edit =
                    SliderEdit(
                        displayValue = threshold.toDouble(),
                        displayRange = -80.0..0.0,
                        decimals = 1,
                        unit = "dB",
                        onCommit = { onThresholdChange(it.toFloat().coerceIn(-80.0f, 0.0f)) },
                    ),
            )
            LabeledSlider(
                label = stringResource(R.string.label_attack),
                value = attack,
                onValueChange = { onAttackChange(it) },
                valueRange = 1f..100f,
                valueLabel = "${attack.roundToInt()} ms",
                edit =
                    SliderEdit(
                        displayValue = attack.toDouble(),
                        displayRange = 1.0..100.0,
                        decimals = 0,
                        unit = "ms",
                        onCommit = { onAttackChange(it.toFloat().coerceIn(1.0f, 100.0f)) },
                    ),
            )
            LabeledSlider(
                label = stringResource(R.string.label_release),
                value = release,
                onValueChange = { onReleaseChange(it) },
                valueRange = 10f..500f,
                valueLabel = "${release.roundToInt()} ms",
                edit =
                    SliderEdit(
                        displayValue = release.toDouble(),
                        displayRange = 10.0..500.0,
                        decimals = 0,
                        unit = "ms",
                        onCommit = { onReleaseChange(it.toFloat().coerceIn(10.0f, 500.0f)) },
                    ),
            )
            val filterTypeNames =
                listOf(
                    stringResource(R.string.filter_peak),
                    stringResource(R.string.filter_low_shelf),
                    stringResource(R.string.filter_high_shelf),
                )
            LabeledDropdown(
                label = stringResource(R.string.label_dynamic_eq_filter_type),
                selectedValue = filterTypeNames[filterType],
                options = filterTypeNames,
                onOptionSelected = { index, _ -> onFilterTypeChange(index) },
            )
        }
    }
}

@Composable
fun ConvolverSection(
    state: EffectState,
    viewModel: MainViewModel,
) {
    val vals = state.convolver
    val enabled = vals.enable
    val kernel = vals.kernelFile
    val crossChannel = vals.crossChannel

    val kernelFiles by viewModel.kernelFileList.collectAsStateWithLifecycle()
    val kernelNoneLabel = stringResource(R.string.label_none)
    val kernelOptions = listOf(kernelNoneLabel) + kernelFiles

    EffectSection(
        title = stringResource(R.string.section_convolver),
        enabled = enabled,
        onEnabledChange = viewModel::setConvolverEnabled,
        descriptionRes = R.string.effect_desc_convolver,
        icon = Icons.Default.BlurCircular,
    ) {
        LabeledDropdown(
            label = stringResource(R.string.label_convolver_kernel),
            selectedValue = kernel.ifEmpty { kernelNoneLabel },
            options = kernelOptions,
            onOptionSelected = { _, value ->
                viewModel.setConvolverKernel(if (value == kernelNoneLabel) "" else value)
            },
            onDeleteOption = { _, name -> viewModel.deleteKernelFile(name) },
            isOptionDeletable = { _, name -> name != kernelNoneLabel },
        )
        LabeledSlider(
            label = stringResource(R.string.label_convolver_cross_channel),
            value = crossChannel,
            onValueChange = { viewModel.applyPref(Effects.convolver.crossChannel, it) },
            valueRange = 0f..1f,
            valueLabel = "${(crossChannel * 100.0f).roundToInt()}%",
            edit =
                SliderEdit(
                    displayValue = (crossChannel * 100.0f).toDouble(),
                    displayRange = 0.0..100.0,
                    decimals = 0,
                    onCommit = { viewModel.applyPref(Effects.convolver.crossChannel, (it / 100.0).toFloat().coerceIn(0.0f, 1.0f)) },
                ),
        )
    }
}

@Composable
fun FieldSurroundSection(
    state: EffectState,
    viewModel: MainViewModel,
) {
    val vals = state.fieldSurround
    val enabled = vals.enable
    val widening = vals.widening
    val midImage = vals.midImage
    val depth = vals.depth

    EffectSection(
        title = stringResource(R.string.section_field_surround),
        enabled = enabled,
        onEnabledChange = viewModel::setFieldSurroundEnabled,
        descriptionRes = R.string.effect_desc_field_surround,
        icon = Icons.Default.SurroundSound,
    ) {
        LabeledSlider(
            label = stringResource(R.string.label_field_surround_widening),
            value = widening,
            onValueChange = { viewModel.applyPref(Effects.fieldSurround.widening, it) },
            valueRange = 0f..8f,
            steps = 7,
            valueLabel = widening.roundToInt().toString(),
            edit =
                SliderEdit(
                    displayValue = widening.toDouble(),
                    displayRange = 0.0..8.0,
                    decimals = 0,
                    onCommit = { viewModel.applyPref(Effects.fieldSurround.widening, it.toFloat().coerceIn(0.0f, 8.0f)) },
                ),
        )
        LabeledSlider(
            label = stringResource(R.string.label_field_surround_mid_image),
            value = midImage,
            onValueChange = { viewModel.applyPref(Effects.fieldSurround.midImage, it) },
            valueRange = 1f..2f,
            valueLabel = String.format(Locale.US, "%.1f", midImage),
            edit =
                SliderEdit(
                    displayValue = midImage.toDouble(),
                    displayRange = 1.0..2.0,
                    decimals = 1,
                    onCommit = { viewModel.applyPref(Effects.fieldSurround.midImage, it.toFloat().coerceIn(1.0f, 2.0f)) },
                ),
        )
        LabeledSlider(
            label = stringResource(R.string.label_depth),
            value = depth.toFloat(),
            onValueChange = { viewModel.applyPref(Effects.fieldSurround.depth, it.roundToInt()) },
            valueRange = 200f..950f,
            valueLabel = "${((depth - 200) / 750.0 * 100.0).roundToInt()}%",
            edit =
                SliderEdit(
                    displayValue = ((depth - 200) / 750.0 * 100.0),
                    displayRange = 0.0..100.0,
                    decimals = 0,
                    unit = "%",
                    onCommit = {
                        viewModel.applyPref(
                            Effects.fieldSurround.depth,
                            (it / 100.0 * 750.0 + 200.0).roundToInt().coerceIn(200, 950),
                        )
                    },
                ),
        )
    }
}

@Composable
fun DiffSurroundSection(
    state: EffectState,
    viewModel: MainViewModel,
) {
    val vals = state.diffSurround
    val enabled = vals.enable
    val delay = vals.delay
    val reverse = vals.reverse
    val wetDryMix = vals.wetDryMix
    val lpCutoff = vals.lpCutoff

    EffectSection(
        title = stringResource(R.string.section_diff_surround),
        enabled = enabled,
        onEnabledChange = viewModel::setDiffSurroundEnabled,
        descriptionRes = R.string.effect_desc_diff_surround,
        icon = Icons.Default.SpatialAudio,
    ) {
        LabeledSlider(
            label = stringResource(R.string.label_delay),
            value = delay,
            onValueChange = { viewModel.applyPref(Effects.diffSurround.delay, it) },
            valueRange = 1f..20f,
            steps = 18,
            valueLabel = "${delay.roundToInt()} ms",
            edit =
                SliderEdit(
                    displayValue = delay.toDouble(),
                    displayRange = 1.0..20.0,
                    decimals = 0,
                    unit = "ms",
                    onCommit = { viewModel.applyPref(Effects.diffSurround.delay, it.toFloat().coerceIn(1.0f, 20.0f)) },
                ),
        )
        LabeledSwitch(
            label = stringResource(R.string.label_diff_surround_reverse),
            checked = reverse,
            onCheckedChange = { viewModel.applyPref(Effects.diffSurround.reverse, it) },
        )
        LabeledSlider(
            label = stringResource(R.string.label_diff_surround_wet_dry_mix),
            value = wetDryMix,
            onValueChange = { viewModel.applyPref(Effects.diffSurround.wetDryMix, it) },
            valueRange = 0f..1f,
            valueLabel = "${(wetDryMix * 100.0f).roundToInt()}%",
            edit =
                SliderEdit(
                    displayValue = (wetDryMix * 100.0f).toDouble(),
                    displayRange = 0.0..100.0,
                    decimals = 0,
                    unit = "%",
                    onCommit = { viewModel.applyPref(Effects.diffSurround.wetDryMix, (it / 100.0).toFloat().coerceIn(0.0f, 1.0f)) },
                ),
        )
        LabeledSlider(
            label = stringResource(R.string.label_diff_surround_lp_cutoff),
            value = lpCutoff.toFloat(),
            onValueChange = { viewModel.applyPref(Effects.diffSurround.lpCutoff, it.roundToInt()) },
            valueRange = 0f..20000f,
            steps = 3999,
            valueLabel = if (lpCutoff == 0) stringResource(R.string.label_off) else "$lpCutoff Hz",
            edit =
                SliderEdit(
                    displayValue = lpCutoff.toDouble(),
                    displayRange = 0.0..20000.0,
                    decimals = 0,
                    unit = "Hz",
                    onCommit = { viewModel.applyPref(Effects.diffSurround.lpCutoff, it.roundToInt().coerceIn(0, 20000)) },
                ),
        )
    }
}

@Composable
fun StereoImagerSection(
    state: EffectState,
    viewModel: MainViewModel,
) {
    val vals = state.stereoImager
    val enabled = vals.enable
    val lowWidth = vals.lowWidth
    val midWidth = vals.midWidth
    val highWidth = vals.highWidth
    val lowCrossover = vals.lowCrossover
    val highCrossover = vals.highCrossover

    EffectSection(
        title = stringResource(R.string.section_stereo_imager),
        enabled = enabled,
        onEnabledChange = viewModel::setStereoImagerEnabled,
        descriptionRes = R.string.effect_desc_stereo_imager,
        icon = Icons.Default.AspectRatio,
    ) {
        LabeledSlider(
            label = stringResource(R.string.label_stereo_imager_low_width),
            value = lowWidth,
            onValueChange = { viewModel.applyPref(Effects.stereoImager.lowWidth, it) },
            valueRange = 0f..2f,
            valueLabel = "${(lowWidth * 100.0f).roundToInt()}%",
            edit =
                SliderEdit(
                    displayValue = (lowWidth * 100.0f).toDouble(),
                    displayRange = 0.0..200.0,
                    decimals = 0,
                    unit = "%",
                    onCommit = { viewModel.applyPref(Effects.stereoImager.lowWidth, (it / 100.0).toFloat().coerceIn(0.0f, 2.0f)) },
                ),
        )
        LabeledSlider(
            label = stringResource(R.string.label_stereo_imager_mid_width),
            value = midWidth,
            onValueChange = { viewModel.applyPref(Effects.stereoImager.midWidth, it) },
            valueRange = 0f..2f,
            valueLabel = "${(midWidth * 100.0f).roundToInt()}%",
            edit =
                SliderEdit(
                    displayValue = (midWidth * 100.0f).toDouble(),
                    displayRange = 0.0..200.0,
                    decimals = 0,
                    unit = "%",
                    onCommit = { viewModel.applyPref(Effects.stereoImager.midWidth, (it / 100.0).toFloat().coerceIn(0.0f, 2.0f)) },
                ),
        )
        LabeledSlider(
            label = stringResource(R.string.label_stereo_imager_high_width),
            value = highWidth,
            onValueChange = { viewModel.applyPref(Effects.stereoImager.highWidth, it) },
            valueRange = 0f..2f,
            valueLabel = "${(highWidth * 100.0f).roundToInt()}%",
            edit =
                SliderEdit(
                    displayValue = (highWidth * 100.0f).toDouble(),
                    displayRange = 0.0..200.0,
                    decimals = 0,
                    unit = "%",
                    onCommit = { viewModel.applyPref(Effects.stereoImager.highWidth, (it / 100.0).toFloat().coerceIn(0.0f, 2.0f)) },
                ),
        )
        LabeledSlider(
            label = stringResource(R.string.label_stereo_imager_low_crossover),
            value = lowCrossover.toFloat(),
            onValueChange = { viewModel.applyPref(Effects.stereoImager.lowCrossover, it.roundToInt()) },
            valueRange = 80f..400f,
            steps = 63,
            valueLabel = "$lowCrossover Hz",
            edit =
                SliderEdit(
                    displayValue = lowCrossover.toDouble(),
                    displayRange = 80.0..400.0,
                    decimals = 0,
                    unit = "Hz",
                    onCommit = { viewModel.applyPref(Effects.stereoImager.lowCrossover, it.roundToInt().coerceIn(80, 400)) },
                ),
        )
        LabeledSlider(
            label = stringResource(R.string.label_stereo_imager_high_crossover),
            value = highCrossover.toFloat(),
            onValueChange = { viewModel.applyPref(Effects.stereoImager.highCrossover, it.roundToInt()) },
            valueRange = 2000f..8000f,
            steps = 1199,
            valueLabel = "$highCrossover Hz",
            edit =
                SliderEdit(
                    displayValue = highCrossover.toDouble(),
                    displayRange = 2000.0..8000.0,
                    decimals = 0,
                    unit = "Hz",
                    onCommit = { viewModel.applyPref(Effects.stereoImager.highCrossover, it.roundToInt().coerceIn(2000, 8000)) },
                ),
        )
    }
}

@Composable
fun HeadphoneSurroundSection(
    state: EffectState,
    viewModel: MainViewModel,
) {
    val vals = state.headphoneSurround
    val enabled = vals.enable
    val quality = vals.quality

    EffectSection(
        title = stringResource(R.string.section_headphone_surround),
        enabled = enabled,
        onEnabledChange = viewModel::setHeadphoneSurroundEnabled,
        descriptionRes = R.string.effect_desc_headphone_surround,
        icon = Icons.Default.Headphones,
    ) {
        LabeledSlider(
            label = stringResource(R.string.label_vhe_quality),
            value = quality.toFloat(),
            onValueChange = { viewModel.applyPref(Effects.headphoneSurround.quality, it.roundToInt()) },
            valueRange = 0f..4f,
            steps = 3,
            edit =
                SliderEdit(
                    displayValue = quality.toDouble(),
                    displayRange = 0.0..4.0,
                    decimals = 0,
                    onCommit = { viewModel.applyPref(Effects.headphoneSurround.quality, it.roundToInt().coerceIn(0, 4)) },
                ),
        )
    }
}

@Composable
fun ReverberationSection(
    state: EffectState,
    viewModel: MainViewModel,
) {
    val vals = state.reverb
    val enabled = vals.enable
    val roomSize = vals.roomSize
    val width = vals.width
    val damp = vals.damp
    val wet = vals.wet
    val dry = vals.dry

    EffectSection(
        title = stringResource(R.string.section_reverb),
        enabled = enabled,
        onEnabledChange = viewModel::setReverbEnabled,
        descriptionRes = R.string.effect_desc_reverb,
        icon = Icons.Default.BlurOn,
    ) {
        LabeledSlider(
            label = stringResource(R.string.label_reverb_room_size),
            value = roomSize,
            onValueChange = { viewModel.applyPref(Effects.reverb.roomSize, it) },
            valueRange = 0f..1f,
            valueLabel = (roomSize * 10.0f).roundToInt().toString(),
            edit =
                SliderEdit(
                    displayValue = (roomSize * 10.0f).toDouble(),
                    displayRange = 0.0..10.0,
                    decimals = 0,
                    onCommit = { viewModel.applyPref(Effects.reverb.roomSize, (it / 10.0).toFloat().coerceIn(0.0f, 1.0f)) },
                ),
        )
        LabeledSlider(
            label = stringResource(R.string.label_width),
            value = width,
            onValueChange = { viewModel.applyPref(Effects.reverb.width, it) },
            valueRange = 0f..1f,
            valueLabel = (width * 10.0f).roundToInt().toString(),
            edit =
                SliderEdit(
                    displayValue = (width * 10.0f).toDouble(),
                    displayRange = 0.0..10.0,
                    decimals = 0,
                    onCommit = { viewModel.applyPref(Effects.reverb.width, (it / 10.0).toFloat().coerceIn(0.0f, 1.0f)) },
                ),
        )
        LabeledSlider(
            label = stringResource(R.string.label_reverb_dampening),
            value = damp,
            onValueChange = { viewModel.applyPref(Effects.reverb.damp, it) },
            valueRange = 0f..1f,
            valueLabel = (damp * 10.0f).roundToInt().toString(),
            edit =
                SliderEdit(
                    displayValue = (damp * 10.0f).toDouble(),
                    displayRange = 0.0..10.0,
                    decimals = 0,
                    onCommit = { viewModel.applyPref(Effects.reverb.damp, (it / 10.0).toFloat().coerceIn(0.0f, 1.0f)) },
                ),
        )
        LabeledSlider(
            label = stringResource(R.string.label_reverb_wet),
            value = wet,
            onValueChange = { viewModel.applyPref(Effects.reverb.wet, it) },
            valueRange = 0f..1f,
            valueLabel = "${(wet * 100.0f).roundToInt()}%",
            edit =
                SliderEdit(
                    displayValue = (wet * 100.0f).toDouble(),
                    displayRange = 0.0..100.0,
                    decimals = 0,
                    unit = "%",
                    onCommit = { viewModel.applyPref(Effects.reverb.wet, (it / 100.0).toFloat().coerceIn(0.0f, 1.0f)) },
                ),
        )
        LabeledSlider(
            label = stringResource(R.string.label_reverb_dry),
            value = dry,
            onValueChange = { viewModel.applyPref(Effects.reverb.dry, it) },
            valueRange = 0f..1f,
            valueLabel = "${(dry * 100.0f).roundToInt()}%",
            edit =
                SliderEdit(
                    displayValue = (dry * 100.0f).toDouble(),
                    displayRange = 0.0..100.0,
                    decimals = 0,
                    unit = "%",
                    onCommit = { viewModel.applyPref(Effects.reverb.dry, (it / 100.0).toFloat().coerceIn(0.0f, 1.0f)) },
                ),
        )
    }
}

@Composable
fun DynamicSystemSection(
    state: EffectState,
    viewModel: MainViewModel,
) {
    val vals = state.dynamicSystem
    val enabled = vals.enable
    val strength = vals.strength
    val dsPresetId = vals.presetId
    val dsPresets = vals.presets
    val xLow = vals.xLow
    val xHigh = vals.xHigh
    val yLow = vals.yLow
    val yHigh = vals.yHigh
    val sideGainLow = vals.sideGainLow
    val sideGainHigh = vals.sideGainHigh

    var showSaveDialog by remember { mutableStateOf(false) }
    var deletePresetId by remember { mutableStateOf<Long?>(null) }

    val onPresetSelect = viewModel::setDynamicSystemPreset
    val onXLowChange = viewModel::setDynamicSystemXLow
    val onXHighChange = viewModel::setDynamicSystemXHigh
    val onYLowChange = viewModel::setDynamicSystemYLow
    val onYHighChange = viewModel::setDynamicSystemYHigh
    val onSideGainLowChange = viewModel::setDynamicSystemSideGainLow
    val onSideGainHighChange = viewModel::setDynamicSystemSideGainHigh
    val onPresetAdd = viewModel::addDynamicSystemPreset
    val onPresetDelete = viewModel::deleteDynamicSystemPreset
    val onReset = viewModel::resetDynamicSystemCoefficients

    EffectSection(
        title = stringResource(R.string.section_dynamic_system),
        enabled = enabled,
        onEnabledChange = viewModel::setDynamicSystemEnabled,
        descriptionRes = R.string.effect_desc_dynamic_system,
        icon = Icons.Default.CandlestickChart,
    ) {
        val presetName =
            dsPresets.find { it.id == dsPresetId }?.let { resolvePresetName(it) }
                ?: stringResource(R.string.label_custom)
        LabeledDropdown(
            label = stringResource(R.string.label_preset),
            selectedValue = presetName,
            options = dsPresets.map { resolvePresetName(it) },
            onOptionSelected = { index, _ -> onPresetSelect(dsPresets[index].id) },
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(DynamicSystemButtonSpacing),
        ) {
            TextButton(onClick = { showSaveDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(DynamicSystemButtonIconSize))
                Spacer(modifier = Modifier.width(DynamicSystemButtonIconSpacing))
                Text(stringResource(R.string.action_save))
            }
            TextButton(
                onClick = { deletePresetId = dsPresetId },
                enabled = dsPresetId != null,
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = null,
                    modifier = Modifier.size(DynamicSystemButtonIconSize),
                )
                Spacer(modifier = Modifier.width(DynamicSystemButtonIconSpacing))
                Text(stringResource(R.string.action_delete))
            }
            TextButton(onClick = onReset) {
                Icon(
                    Icons.Default.RestartAlt,
                    contentDescription = null,
                    modifier = Modifier.size(DynamicSystemButtonIconSize),
                )
                Spacer(modifier = Modifier.width(DynamicSystemButtonIconSpacing))
                Text(stringResource(R.string.action_reset))
            }
        }

        LabeledSlider(
            label = stringResource(R.string.label_dynamic_system_strength),
            value = strength,
            onValueChange = { viewModel.applyPref(Effects.dynamicSystem.strength, it) },
            valueRange = 0f..1f,
            valueLabel = "${(strength * 100.0f).roundToInt()}%",
            edit =
                SliderEdit(
                    displayValue = (strength * 100.0f).toDouble(),
                    displayRange = 0.0..100.0,
                    decimals = 0,
                    unit = "%",
                    onCommit = { viewModel.applyPref(Effects.dynamicSystem.strength, (it / 100.0).toFloat().coerceIn(0.0f, 1.0f)) },
                ),
        )

        LabeledSlider(
            label = stringResource(R.string.label_dynamic_system_x_low_freq),
            value = xLow.toFloat(),
            onValueChange = { onXLowChange(it.roundToInt()) },
            valueRange = 0f..2400f,
            steps = (2400 / 5) - 1,
            valueLabel = "$xLow Hz",
            edit =
                SliderEdit(
                    displayValue = xLow.toDouble(),
                    displayRange = 0.0..2400.0,
                    decimals = 0,
                    unit = "Hz",
                    onCommit = { onXLowChange(it.roundToInt().coerceIn(0, 2400)) },
                ),
        )

        LabeledSlider(
            label = stringResource(R.string.label_dynamic_system_x_high_freq),
            value = xHigh.toFloat(),
            onValueChange = { onXHighChange(it.roundToInt()) },
            valueRange = 0f..12000f,
            steps = (12000 / 5) - 1,
            valueLabel = "$xHigh Hz",
            edit =
                SliderEdit(
                    displayValue = xHigh.toDouble(),
                    displayRange = 0.0..12000.0,
                    decimals = 0,
                    unit = "Hz",
                    onCommit = { onXHighChange(it.roundToInt().coerceIn(0, 12000)) },
                ),
        )

        LabeledSlider(
            label = stringResource(R.string.label_dynamic_system_y_low_freq),
            value = yLow.toFloat(),
            onValueChange = { onYLowChange(it.roundToInt()) },
            valueRange = 0f..200f,
            steps = 199,
            valueLabel = "$yLow Hz",
            edit =
                SliderEdit(
                    displayValue = yLow.toDouble(),
                    displayRange = 0.0..200.0,
                    decimals = 0,
                    unit = "Hz",
                    onCommit = { onYLowChange(it.roundToInt().coerceIn(0, 200)) },
                ),
        )

        LabeledSlider(
            label = stringResource(R.string.label_dynamic_system_y_high_freq),
            value = yHigh.toFloat(),
            onValueChange = { onYHighChange(it.roundToInt()) },
            valueRange = 0f..300f,
            steps = (300 / 5) - 1,
            valueLabel = "$yHigh Hz",
            edit =
                SliderEdit(
                    displayValue = yHigh.toDouble(),
                    displayRange = 0.0..300.0,
                    decimals = 0,
                    unit = "Hz",
                    onCommit = { onYHighChange(it.roundToInt().coerceIn(0, 300)) },
                ),
        )

        LabeledSlider(
            label = stringResource(R.string.label_dynamic_system_side_gain_low),
            value = sideGainLow,
            onValueChange = { onSideGainLowChange(it) },
            valueRange = 0f..1f,
            valueLabel = "${(sideGainLow * 100.0f).roundToInt()}%",
            edit =
                SliderEdit(
                    displayValue = (sideGainLow * 100.0f).toDouble(),
                    displayRange = 0.0..100.0,
                    decimals = 0,
                    unit = "%",
                    onCommit = { onSideGainLowChange((it / 100.0).toFloat().coerceIn(0.0f, 1.0f)) },
                ),
        )

        LabeledSlider(
            label = stringResource(R.string.label_dynamic_system_side_gain_high),
            value = sideGainHigh,
            onValueChange = { onSideGainHighChange(it) },
            valueRange = 0f..1f,
            valueLabel = "${(sideGainHigh * 100.0f).roundToInt()}%",
            edit =
                SliderEdit(
                    displayValue = (sideGainHigh * 100.0f).toDouble(),
                    displayRange = 0.0..100.0,
                    decimals = 0,
                    unit = "%",
                    onCommit = { onSideGainHighChange((it / 100.0).toFloat().coerceIn(0.0f, 1.0f)) },
                ),
        )
    }

    if (showSaveDialog) {
        InputDialog(
            title = stringResource(R.string.preset_save_title),
            initialValue = "",
            confirmLabel = stringResource(android.R.string.ok),
            onConfirm = { name ->
                onPresetAdd(name)
                showSaveDialog = false
            },
            onDismiss = { showSaveDialog = false },
            placeholder = stringResource(R.string.preset_name_hint),
        )
    }

    deletePresetId?.let { targetId ->
        val targetName =
            dsPresets.find { it.id == targetId }?.let { resolvePresetName(it) }
                ?: stringResource(R.string.label_custom)
        ConfirmDialog(
            title = stringResource(R.string.preset_delete_title),
            body = stringResource(R.string.preset_delete_confirm, targetName),
            confirmLabel = stringResource(R.string.action_delete),
            destructive = true,
            onConfirm = {
                onPresetDelete(targetId)
                deletePresetId = null
            },
            onDismiss = { deletePresetId = null },
        )
    }
}

@Composable
fun TubeSimulatorSection(
    state: EffectState,
    viewModel: MainViewModel,
) {
    val vals = state.tubeSimulator
    val enabled = vals.enable
    val model = vals.model
    val drive = vals.drive
    val mix = vals.mix
    val modelOptions =
        listOf(
            stringResource(R.string.label_tube_model_12ax7),
            stringResource(R.string.label_tube_model_12au7),
            stringResource(R.string.label_tube_model_6n1p),
        )

    EffectSection(
        title = stringResource(R.string.section_tube_simulator),
        enabled = enabled,
        onEnabledChange = viewModel::setTubeSimulatorEnabled,
        descriptionRes = R.string.effect_desc_tube_simulator,
        icon = Icons.Default.MusicNote,
    ) {
        LabeledDropdown(
            label = stringResource(R.string.label_tube_model),
            selectedValue = modelOptions.getOrElse(model) { modelOptions.first() },
            options = modelOptions,
            onOptionSelected = { index, _ -> viewModel.applyPref(Effects.tubeSimulator.model, index) },
        )
        LabeledSlider(
            label = stringResource(R.string.label_tube_drive),
            value = drive,
            onValueChange = { viewModel.applyPref(Effects.tubeSimulator.drive, it) },
            valueRange = 0.0f..1.0f,
            valueLabel = String.format(Locale.US, "%.0f%%", drive * 100.0f),
            edit =
                SliderEdit(
                    displayValue = (drive * 100.0f).toDouble(),
                    displayRange = 0.0..100.0,
                    decimals = 0,
                    unit = "%",
                    onCommit = { viewModel.applyPref(Effects.tubeSimulator.drive, (it / 100.0).toFloat().coerceIn(0.0f, 1.0f)) },
                ),
        )
        LabeledSlider(
            label = stringResource(R.string.label_tube_mix),
            value = mix,
            onValueChange = { viewModel.applyPref(Effects.tubeSimulator.mix, it) },
            valueRange = 0.0f..1.0f,
            valueLabel = String.format(Locale.US, "%.0f%%", mix * 100.0f),
            edit =
                SliderEdit(
                    displayValue = (mix * 100.0f).toDouble(),
                    displayRange = 0.0..100.0,
                    decimals = 0,
                    unit = "%",
                    onCommit = { viewModel.applyPref(Effects.tubeSimulator.mix, (it / 100.0).toFloat().coerceIn(0.0f, 1.0f)) },
                ),
        )
    }
}

@Composable
fun PsychoacousticBassSection(
    state: EffectState,
    viewModel: MainViewModel,
) {
    val vals = state.psychoacousticBass
    val enabled = vals.enable
    val cutoff = vals.cutoff
    val intensity = vals.intensity
    val harmonicOrder = vals.harmonicOrder
    val originalLevel = vals.originalLevel

    val harmonicNames =
        listOf(
            stringResource(R.string.harmonic_2nd),
            stringResource(R.string.harmonic_3rd),
            stringResource(R.string.harmonic_4th),
            stringResource(R.string.harmonic_5th),
        )
    val harmonicValues = listOf(2, 3, 4, 5)
    val harmonicIndex = harmonicValues.indexOf(harmonicOrder).coerceAtLeast(0)

    EffectSection(
        title = stringResource(R.string.section_psycho_bass),
        enabled = enabled,
        onEnabledChange = viewModel::setPsychoacousticBassEnabled,
        descriptionRes = R.string.effect_desc_psychoacoustic_bass,
        icon = Icons.Default.Psychology,
    ) {
        LabeledSlider(
            label = stringResource(R.string.label_psycho_bass_cutoff),
            value = cutoff.toFloat(),
            onValueChange = { viewModel.applyPref(Effects.psychoacousticBass.cutoff, it.roundToInt()) },
            valueRange = 60f..150f,
            valueLabel = "$cutoff Hz",
            edit =
                SliderEdit(
                    displayValue = cutoff.toDouble(),
                    displayRange = 60.0..150.0,
                    decimals = 0,
                    unit = "Hz",
                    onCommit = { viewModel.applyPref(Effects.psychoacousticBass.cutoff, it.roundToInt().coerceIn(60, 150)) },
                ),
        )
        LabeledSlider(
            label = stringResource(R.string.label_psycho_bass_intensity),
            value = intensity,
            onValueChange = { viewModel.applyPref(Effects.psychoacousticBass.intensity, it) },
            valueRange = 0f..1f,
            valueLabel = "${(intensity * 100.0f).roundToInt()}%",
            edit =
                SliderEdit(
                    displayValue = (intensity * 100.0f).toDouble(),
                    displayRange = 0.0..100.0,
                    decimals = 0,
                    unit = "%",
                    onCommit = { viewModel.applyPref(Effects.psychoacousticBass.intensity, (it / 100.0).toFloat().coerceIn(0.0f, 1.0f)) },
                ),
        )
        LabeledSlider(
            label = stringResource(R.string.label_psycho_bass_harmonic_order),
            value = harmonicOrder.toFloat(),
            onValueChange = { viewModel.applyPref(Effects.psychoacousticBass.harmonicOrder, it.roundToInt()) },
            valueRange = 2f..5f,
            steps = 2,
            valueLabel = harmonicNames[harmonicIndex],
        )
        LabeledSlider(
            label = stringResource(R.string.label_psycho_bass_ori_bass_level),
            value = originalLevel,
            onValueChange = { viewModel.applyPref(Effects.psychoacousticBass.originalLevel, it) },
            valueRange = 0f..1f,
            valueLabel = "${(originalLevel * 100.0f).roundToInt()}%",
            edit =
                SliderEdit(
                    displayValue = (originalLevel * 100.0f).toDouble(),
                    displayRange = 0.0..100.0,
                    decimals = 0,
                    unit = "%",
                    onCommit = {
                        viewModel.applyPref(
                            Effects.psychoacousticBass.originalLevel,
                            (it / 100.0).toFloat().coerceIn(0.0f, 1.0f),
                        )
                    },
                ),
        )
    }
}

@Composable
fun ViperBassSection(
    state: EffectState,
    viewModel: MainViewModel,
) {
    val vals = state.bass
    val enabled = vals.enable
    val mode = vals.mode
    val frequency = vals.frequency
    val gain = vals.gain
    val antiPop = vals.antiPop

    val modeNames =
        listOf(
            stringResource(R.string.bass_mode_natural),
            stringResource(R.string.bass_mode_pure),
            stringResource(R.string.bass_mode_subwoofer),
        )

    EffectSection(
        title = stringResource(R.string.section_viper_bass),
        enabled = enabled,
        onEnabledChange = viewModel::setBassEnabled,
        descriptionRes = R.string.effect_desc_viper_bass,
        icon = Icons.Default.GraphicEq,
    ) {
        LabeledDropdown(
            label = stringResource(R.string.label_mode),
            selectedValue = modeNames.getOrElse(mode) { modeNames[0] },
            options = modeNames,
            onOptionSelected = { index, _ -> viewModel.applyPref(Effects.bass.mode, index) },
        )
        if (mode != 2) {
            LabeledSlider(
                label = stringResource(R.string.label_frequency),
                value = frequency.toFloat(),
                onValueChange = { viewModel.applyPref(Effects.bass.frequency, it.roundToInt()) },
                valueRange = 15f..150f,
                steps = 134,
                valueLabel = "$frequency Hz",
                edit =
                    SliderEdit(
                        displayValue = frequency.toDouble(),
                        displayRange = 15.0..150.0,
                        decimals = 0,
                        unit = "Hz",
                        onCommit = { viewModel.applyPref(Effects.bass.frequency, it.roundToInt().coerceIn(15, 150)) },
                    ),
            )
        }
        LabeledSlider(
            label = stringResource(R.string.label_gain),
            value = gain,
            onValueChange = { viewModel.applyPref(Effects.bass.gain, it) },
            valueRange = 0.5f..10.0f,
            valueLabel = String.format(Locale.US, "%.1fx", gain),
            edit =
                SliderEdit(
                    displayValue = gain.toDouble(),
                    displayRange = 0.5..10.0,
                    decimals = 1,
                    unit = "x",
                    onCommit = { viewModel.applyPref(Effects.bass.gain, it.toFloat().coerceIn(0.5f, 10.0f)) },
                ),
        )
        LabeledSwitch(
            label = stringResource(R.string.label_bass_anti_pop),
            checked = antiPop,
            onCheckedChange = { viewModel.applyPref(Effects.bass.antiPop, it) },
        )
    }
}

@Composable
fun ViperBassMonoSection(
    state: EffectState,
    viewModel: MainViewModel,
) {
    val vals = state.bassMono
    val enabled = vals.enable
    val mode = vals.mode
    val frequency = vals.frequency
    val gain = vals.gain
    val antiPop = vals.antiPop

    val modeNames =
        listOf(
            stringResource(R.string.bass_mode_natural),
            stringResource(R.string.bass_mode_pure),
            stringResource(R.string.bass_mode_subwoofer),
        )

    EffectSection(
        title = stringResource(R.string.section_viper_bass_mono),
        enabled = enabled,
        onEnabledChange = viewModel::setBassMonoEnabled,
        descriptionRes = R.string.effect_desc_viper_bass_mono,
        icon = Icons.Default.GraphicEq,
    ) {
        LabeledDropdown(
            label = stringResource(R.string.label_mode),
            selectedValue = modeNames.getOrElse(mode) { modeNames[0] },
            options = modeNames,
            onOptionSelected = { index, _ -> viewModel.applyPref(Effects.bassMono.mode, index) },
        )
        if (mode != 2) {
            LabeledSlider(
                label = stringResource(R.string.label_frequency),
                value = frequency.toFloat(),
                onValueChange = { viewModel.applyPref(Effects.bassMono.frequency, it.roundToInt()) },
                valueRange = 15f..150f,
                steps = 134,
                valueLabel = "$frequency Hz",
                edit =
                    SliderEdit(
                        displayValue = frequency.toDouble(),
                        displayRange = 15.0..150.0,
                        decimals = 0,
                        unit = "Hz",
                        onCommit = { viewModel.applyPref(Effects.bassMono.frequency, it.roundToInt().coerceIn(15, 150)) },
                    ),
            )
        }
        LabeledSlider(
            label = stringResource(R.string.label_gain),
            value = gain,
            onValueChange = { viewModel.applyPref(Effects.bassMono.gain, it) },
            valueRange = 0.5f..10.0f,
            valueLabel = String.format(Locale.US, "%.1fx", gain),
            edit =
                SliderEdit(
                    displayValue = gain.toDouble(),
                    displayRange = 0.5..10.0,
                    decimals = 1,
                    unit = "x",
                    onCommit = { viewModel.applyPref(Effects.bassMono.gain, it.toFloat().coerceIn(0.5f, 10.0f)) },
                ),
        )
        LabeledSwitch(
            label = stringResource(R.string.label_bass_anti_pop),
            checked = antiPop,
            onCheckedChange = { viewModel.applyPref(Effects.bassMono.antiPop, it) },
        )
    }
}

@Composable
fun ViperClaritySection(
    state: EffectState,
    viewModel: MainViewModel,
) {
    val vals = state.clarity
    val enabled = vals.enable
    val mode = vals.mode
    val gain = vals.gain

    val modeNames =
        listOf(
            stringResource(R.string.clarity_mode_natural),
            stringResource(R.string.clarity_mode_ozone),
            stringResource(R.string.clarity_mode_xhifi),
        )

    EffectSection(
        title = stringResource(R.string.section_viper_clarity),
        enabled = enabled,
        onEnabledChange = viewModel::setClarityEnabled,
        descriptionRes = R.string.effect_desc_viper_clarity,
        icon = Icons.Default.Hearing,
    ) {
        LabeledDropdown(
            label = stringResource(R.string.label_mode),
            selectedValue = modeNames.getOrElse(mode) { modeNames[0] },
            options = modeNames,
            onOptionSelected = { index, _ -> viewModel.applyPref(Effects.clarity.mode, index) },
        )
        LabeledSlider(
            label = stringResource(R.string.label_gain),
            value = gain,
            onValueChange = { viewModel.applyPref(Effects.clarity.gain, it) },
            valueRange = 0f..4.5f,
            valueLabel = String.format(Locale.US, "%.1fx", gain),
            edit =
                SliderEdit(
                    displayValue = gain.toDouble(),
                    displayRange = 0.0..4.5,
                    decimals = 1,
                    unit = "x",
                    onCommit = { viewModel.applyPref(Effects.clarity.gain, it.toFloat().coerceIn(0.0f, 4.5f)) },
                ),
        )
    }
}

@Composable
fun AuditoryProtectionSection(
    state: EffectState,
    viewModel: MainViewModel,
) {
    val vals = state.cure
    val enabled = vals.enable
    val crossfeedPreset = vals.crossfeedPreset

    val strengthNames =
        listOf(
            stringResource(R.string.label_mild),
            stringResource(R.string.label_medium),
            stringResource(R.string.label_strong),
        )

    EffectSection(
        title = stringResource(R.string.section_cure),
        enabled = enabled,
        onEnabledChange = viewModel::setCureEnabled,
        descriptionRes = R.string.effect_desc_cure,
        icon = Icons.Default.HealthAndSafety,
    ) {
        LabeledDropdown(
            label = stringResource(R.string.label_cure_strength),
            selectedValue = strengthNames.getOrElse(crossfeedPreset) { strengthNames[0] },
            options = strengthNames,
            onOptionSelected = { index, _ ->
                viewModel.applyPref(Effects.cure.crossfeedPreset, index)
            },
        )
    }
}

@Composable
fun AnalogXSection(
    state: EffectState,
    viewModel: MainViewModel,
) {
    val vals = state.analogX
    val enabled = vals.enable
    val mode = vals.mode

    val modeNames =
        listOf(
            stringResource(R.string.label_mild),
            stringResource(R.string.label_medium),
            stringResource(R.string.label_strong),
        )

    EffectSection(
        title = stringResource(R.string.section_analogx),
        enabled = enabled,
        onEnabledChange = viewModel::setAnalogXEnabled,
        descriptionRes = R.string.effect_desc_analogx,
        icon = Icons.Default.Memory,
    ) {
        LabeledDropdown(
            label = stringResource(R.string.label_mode),
            selectedValue = modeNames.getOrElse(mode) { modeNames[0] },
            options = modeNames,
            onOptionSelected = { index, _ -> viewModel.applyPref(Effects.analogX.mode, index) },
        )
    }
}

@Composable
fun SpeakerOptSection(
    state: EffectState,
    viewModel: MainViewModel,
) {
    EffectSection(
        title = stringResource(R.string.section_speaker_optimization),
        enabled = state.speakerCorrection.enable,
        onEnabledChange = viewModel::setSpeakerCorrectionEnabled,
        descriptionRes = R.string.effect_desc_speaker_correction,
        icon = Icons.Default.SpeakerPhone,
        toggleOnly = true,
    ) {}
}
