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
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.llsl.viper4android.R
import com.llsl.viper4android.effect.EffectState
import com.llsl.viper4android.effect.Effects
import com.llsl.viper4android.ui.components.EqCurveGraph
import com.llsl.viper4android.ui.components.EqEditDialog
import com.llsl.viper4android.ui.components.LabeledDropdown
import com.llsl.viper4android.ui.components.LabeledSlider
import com.llsl.viper4android.ui.components.LabeledSwitch
import com.llsl.viper4android.ui.components.RichText
import com.llsl.viper4android.ui.components.SliderEdit
import com.llsl.viper4android.ui.components.resolvePresetName
import java.util.Locale
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt

private fun rawToDb(raw: Number): Double = 20.0 * log10(raw.toDouble() / 100.0)

private fun dbToRaw(db: Double): Int = (10.0.pow(db / 20.0) * 100.0).roundToInt()

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
                .padding(horizontal = 16.dp, vertical = 4.dp),
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
                        ).padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.width(12.dp))
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
                    Spacer(modifier = Modifier.height(48.dp))
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
                                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    ) {
                        content()
                    }
                }
            }
        }
    }

    if (showHelpDialog) {
        AlertDialog(
            onDismissRequest = { showHelpDialog = false },
            title = { Text(text = title) },
            text = {
                val maxHeight =
                    with(LocalDensity.current) {
                        LocalWindowInfo.current.containerSize.height
                            .toDp() / 2
                    }
                Column(
                    modifier = Modifier.heightIn(max = maxHeight).verticalScroll(rememberScrollState()),
                ) { RichText(text = stringResource(descriptionRes)) }
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
    val gainDb = if (outputVolume > 0) rawToDb(outputVolume) else -99.9
    val limDb = if (limiter > 0) rawToDb(limiter) else -99.9
    val left = 50 - channelPan / 2
    val right = 50 + channelPan / 2
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
            value = outputVolume.toFloat(),
            onValueChange = { viewModel.applyPref(Effects.masterLimiter.outputVolume, it.roundToInt()) },
            valueRange = 1f..200f,
            valueLabel = "${"%.1f".format(gainDb)}dB",
            edit =
                SliderEdit(
                    displayValue = gainDb,
                    displayRange = rawToDb(1)..rawToDb(200),
                    decimals = 1,
                    unit = "dB",
                    onCommit = { viewModel.applyPref(Effects.masterLimiter.outputVolume, dbToRaw(it).coerceIn(1, 200)) },
                ),
        )
        LabeledSlider(
            label = stringResource(R.string.label_output_pan),
            value = channelPan.toFloat(),
            onValueChange = { viewModel.applyPref(Effects.masterLimiter.channelPan, it.roundToInt()) },
            valueRange = -100f..100f,
            valueLabel = "$left:$right",
            edit =
                SliderEdit(
                    displayValue = channelPan.toDouble(),
                    displayRange = -100.0..100.0,
                    decimals = 0,
                    onCommit = { viewModel.applyPref(Effects.masterLimiter.channelPan, it.roundToInt()) },
                ),
        )
        LabeledSlider(
            label = stringResource(R.string.label_output_limiter),
            value = limiter.toFloat(),
            onValueChange = { viewModel.applyPref(Effects.masterLimiter.threshold, it.roundToInt()) },
            valueRange = 30f..100f,
            valueLabel = "${"%.1f".format(limDb)}dB",
            edit =
                SliderEdit(
                    displayValue = limDb,
                    displayRange = rawToDb(30)..rawToDb(100),
                    decimals = 1,
                    unit = "dB",
                    onCommit = { viewModel.applyPref(Effects.masterLimiter.threshold, dbToRaw(it).coerceIn(30, 100)) },
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
            value = strength.toFloat(),
            onValueChange = { viewModel.applyPref(Effects.playbackGainControl.strength, it.roundToInt()) },
            valueRange = 50f..300f,
            valueLabel = "${"%.1f".format(strength / 100.0)}x",
            edit =
                SliderEdit(
                    displayValue = strength / 100.0,
                    displayRange = 0.5..3.0,
                    decimals = 1,
                    unit = "x",
                    onCommit = { viewModel.applyPref(Effects.playbackGainControl.strength, (it * 100).roundToInt().coerceIn(50, 300)) },
                ),
        )
        LabeledSlider(
            label = stringResource(R.string.label_max_gain),
            value = maxGain.toFloat(),
            onValueChange = { viewModel.applyPref(Effects.playbackGainControl.maxGain, it.roundToInt()) },
            valueRange = 100f..1000f,
            valueLabel = "${"%.1f".format(maxGain / 100.0)}x",
            edit =
                SliderEdit(
                    displayValue = maxGain / 100.0,
                    displayRange = 1.0..10.0,
                    decimals = 1,
                    unit = "x",
                    onCommit = { viewModel.applyPref(Effects.playbackGainControl.maxGain, (it * 100).roundToInt().coerceIn(100, 1000)) },
                ),
        )
        val threshDb = if (threshold > 0) rawToDb(threshold) else -99.9
        LabeledSlider(
            label = stringResource(R.string.label_agc_output_threshold),
            value = threshold.toFloat(),
            onValueChange = { viewModel.applyPref(Effects.playbackGainControl.outputThreshold, it.roundToInt()) },
            valueRange = 30f..100f,
            valueLabel = "${"%.1f".format(threshDb)}dB",
            edit =
                SliderEdit(
                    displayValue = threshDb,
                    displayRange = rawToDb(30)..rawToDb(100),
                    decimals = 1,
                    unit = "dB",
                    onCommit = { viewModel.applyPref(Effects.playbackGainControl.outputThreshold, dbToRaw(it).coerceIn(30, 100)) },
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
            value = target.toFloat(),
            onValueChange = { viewModel.applyPref(Effects.lufs.target, it.roundToInt()) },
            valueRange = 80f..240f,
            valueLabel = String.format(Locale.US, "%.1f LUFS", target / -10f),
            edit =
                SliderEdit(
                    displayValue = target / -10.0,
                    displayRange = -24.0..-8.0,
                    decimals = 1,
                    unit = "LUFS",
                    onCommit = { viewModel.applyPref(Effects.lufs.target, (it * -10).roundToInt().coerceIn(80, 240)) },
                ),
        )
        LabeledSlider(
            label = stringResource(R.string.label_max_gain),
            value = maxGain.toFloat(),
            onValueChange = { viewModel.applyPref(Effects.lufs.maxGain, it.roundToInt()) },
            valueRange = 0f..120f,
            valueLabel = String.format(Locale.US, "%.1f dB", maxGain / 10f),
            edit =
                SliderEdit(
                    displayValue = maxGain / 10.0,
                    displayRange = 0.0..12.0,
                    decimals = 1,
                    unit = "dB",
                    onCommit = { viewModel.applyPref(Effects.lufs.maxGain, (it * 10).roundToInt().coerceIn(0, 120)) },
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

    EffectSection(
        title = stringResource(R.string.section_fet_compressor),
        enabled = enabled,
        onEnabledChange = viewModel::setFetCompressorEnabled,
        descriptionRes = R.string.effect_desc_fet_compressor,
        icon = Icons.Default.VerticalAlignCenter,
    ) {
        LabeledSlider(
            label = stringResource(R.string.label_threshold),
            value = threshold.toFloat(),
            onValueChange = { viewModel.applyPref(Effects.fetCompressor.threshold, it.roundToInt()) },
            valueRange = -48f..0f,
            valueLabel = "$threshold dB",
            edit =
                SliderEdit(
                    displayValue = threshold.toDouble(),
                    displayRange = -48.0..0.0,
                    decimals = 0,
                    unit = "dB",
                    onCommit = { viewModel.applyPref(Effects.fetCompressor.threshold, it.roundToInt().coerceIn(-48, 0)) },
                ),
        )
        LabeledSlider(
            label = stringResource(R.string.label_fet_ratio),
            value = ratio / 100f,
            onValueChange = { viewModel.applyPref(Effects.fetCompressor.ratio, (it * 100f).roundToInt()) },
            valueRange = 0f..2f,
            valueLabel = String.format(Locale.US, "%.1f", ratio / 100.0),
            edit =
                SliderEdit(
                    displayValue = ratio / 100.0,
                    displayRange = 0.0..2.0,
                    decimals = 1,
                    onCommit = { viewModel.applyPref(Effects.fetCompressor.ratio, (it * 100).roundToInt().coerceIn(0, 200)) },
                ),
        )
        LabeledSwitch(
            label = stringResource(R.string.label_fet_auto_knee),
            checked = kneeAuto,
            onCheckedChange = { viewModel.applyPref(Effects.fetCompressor.kneeAuto, it) },
        )
        LabeledSlider(
            label = stringResource(R.string.label_fet_knee),
            value = knee.toFloat(),
            onValueChange = { viewModel.applyPref(Effects.fetCompressor.knee, it.roundToInt()) },
            valueRange = 0f..12f,
            enabled = !kneeAuto,
            valueLabel = "$knee dB",
            edit =
                SliderEdit(
                    displayValue = knee.toDouble(),
                    displayRange = 0.0..12.0,
                    decimals = 0,
                    unit = "dB",
                    onCommit = { viewModel.applyPref(Effects.fetCompressor.knee, it.roundToInt().coerceIn(0, 12)) },
                ),
        )
        LabeledSlider(
            label = stringResource(R.string.label_fet_knee_multi),
            value = (kneeMulti / 100f * 4f),
            onValueChange = { viewModel.applyPref(Effects.fetCompressor.kneeMulti, (it / 4f * 100f).roundToInt()) },
            valueRange = 0f..4f,
            valueLabel = String.format(Locale.US, "%.1fx", kneeMulti / 100.0 * 4.0),
            edit =
                SliderEdit(
                    displayValue = kneeMulti / 100.0 * 4.0,
                    displayRange = 0.0..4.0,
                    decimals = 1,
                    unit = "x",
                    onCommit = { viewModel.applyPref(Effects.fetCompressor.kneeMulti, (it / 4 * 100).roundToInt().coerceIn(0, 100)) },
                ),
        )
        LabeledSwitch(
            label = stringResource(R.string.label_fet_auto_gain),
            checked = gainAuto,
            onCheckedChange = { viewModel.applyPref(Effects.fetCompressor.gainAuto, it) },
        )
        LabeledSlider(
            label = stringResource(R.string.label_gain),
            value = gain.toFloat(),
            onValueChange = { viewModel.applyPref(Effects.fetCompressor.gain, it.roundToInt()) },
            valueRange = 0f..24f,
            enabled = !gainAuto,
            valueLabel = "$gain dB",
            edit =
                SliderEdit(
                    displayValue = gain.toDouble(),
                    displayRange = 0.0..24.0,
                    decimals = 0,
                    unit = "dB",
                    onCommit = { viewModel.applyPref(Effects.fetCompressor.gain, it.roundToInt().coerceIn(0, 24)) },
                ),
        )
        LabeledSwitch(
            label = stringResource(R.string.label_fet_auto_attack),
            checked = attackAuto,
            onCheckedChange = { viewModel.applyPref(Effects.fetCompressor.attackAuto, it) },
        )
        LabeledSlider(
            label = stringResource(R.string.label_attack),
            value = attack.toFloat().coerceIn(1f, 100f),
            onValueChange = { viewModel.applyPref(Effects.fetCompressor.attack, it.roundToInt()) },
            valueRange = 1f..100f,
            enabled = !attackAuto,
            valueLabel = "$attack ms",
            edit =
                SliderEdit(
                    displayValue = attack.toDouble(),
                    displayRange = 1.0..100.0,
                    decimals = 0,
                    unit = "ms",
                    onCommit = { viewModel.applyPref(Effects.fetCompressor.attack, it.roundToInt().coerceIn(1, 100)) },
                ),
        )
        LabeledSlider(
            label = stringResource(R.string.label_fet_max_attack),
            value = maxAttack.toFloat().coerceIn(1f, 100f),
            onValueChange = { viewModel.applyPref(Effects.fetCompressor.maxAttack, it.roundToInt()) },
            valueRange = 1f..100f,
            valueLabel = "$maxAttack ms",
            edit =
                SliderEdit(
                    displayValue = maxAttack.toDouble(),
                    displayRange = 1.0..100.0,
                    decimals = 0,
                    unit = "ms",
                    onCommit = { viewModel.applyPref(Effects.fetCompressor.maxAttack, it.roundToInt().coerceIn(1, 100)) },
                ),
        )
        LabeledSwitch(
            label = stringResource(R.string.label_fet_auto_release),
            checked = releaseAuto,
            onCheckedChange = { viewModel.applyPref(Effects.fetCompressor.releaseAuto, it) },
        )
        LabeledSlider(
            label = stringResource(R.string.label_release),
            value = release.toFloat().coerceIn(5f, 500f),
            onValueChange = { viewModel.applyPref(Effects.fetCompressor.release, it.roundToInt()) },
            valueRange = 5f..500f,
            enabled = !releaseAuto,
            valueLabel = "$release ms",
            edit =
                SliderEdit(
                    displayValue = release.toDouble(),
                    displayRange = 5.0..500.0,
                    decimals = 0,
                    unit = "ms",
                    onCommit = { viewModel.applyPref(Effects.fetCompressor.release, it.roundToInt().coerceIn(5, 500)) },
                ),
        )
        LabeledSlider(
            label = stringResource(R.string.label_fet_max_release),
            value = maxRelease.toFloat().coerceIn(5f, 500f),
            onValueChange = { viewModel.applyPref(Effects.fetCompressor.maxRelease, it.roundToInt()) },
            valueRange = 5f..500f,
            valueLabel = "$maxRelease ms",
            edit =
                SliderEdit(
                    displayValue = maxRelease.toDouble(),
                    displayRange = 5.0..500.0,
                    decimals = 0,
                    unit = "ms",
                    onCommit = { viewModel.applyPref(Effects.fetCompressor.maxRelease, it.roundToInt().coerceIn(5, 500)) },
                ),
        )
        LabeledSlider(
            label = stringResource(R.string.label_fet_crest),
            value = crest.toFloat().coerceIn(5f, 300f),
            onValueChange = { viewModel.applyPref(Effects.fetCompressor.crest, it.roundToInt()) },
            valueRange = 5f..300f,
            valueLabel = "$crest ms",
            edit =
                SliderEdit(
                    displayValue = crest.toDouble(),
                    displayRange = 5.0..300.0,
                    decimals = 0,
                    unit = "ms",
                    onCommit = { viewModel.applyPref(Effects.fetCompressor.crest, it.roundToInt().coerceIn(5, 300)) },
                ),
        )
        LabeledSlider(
            label = stringResource(R.string.label_fet_adapt),
            value = adapt.toFloat(),
            onValueChange = { viewModel.applyPref(Effects.fetCompressor.adapt, it.roundToInt()) },
            valueRange = 0f..200f,
            valueLabel = "$adapt%",
            edit =
                SliderEdit(
                    displayValue = adapt.toDouble(),
                    displayRange = 0.0..200.0,
                    decimals = 0,
                    unit = "%",
                    onCommit = { viewModel.applyPref(Effects.fetCompressor.adapt, it.roundToInt().coerceIn(0, 200)) },
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

    val threshold = thresholds.getOrElse(b) { -18 }
    val ratio = ratios.getOrElse(b) { 50 }
    val gain = gains.getOrElse(b) { 24 }
    val knee = knees.getOrElse(b) { 0 }
    val kneeMulti = kneeMultis.getOrElse(b) { 0 }
    val attack = attacks.getOrElse(b) { 1 }
    val maxAttack = maxAttacks.getOrElse(b) { 44 }
    val release = releases.getOrElse(b) { 100 }
    val maxRelease = maxReleases.getOrElse(b) { 200 }
    val crest = crests.getOrElse(b) { 100 }
    val adapt = adapts.getOrElse(b) { 50 }
    val bandEnabled = bandEnables.getOrElse(b) { true }
    val kneeAuto = kneeAutos.getOrElse(b) { true }
    val gainAuto = gainAutos.getOrElse(b) { true }
    val attackAuto = attackAutos.getOrElse(b) { true }
    val releaseAuto = releaseAutos.getOrElse(b) { true }
    val noClip = noClips.getOrElse(b) { true }

    val onBandEnableChange: (Boolean) -> Unit = { viewModel.applyBandPref(Effects.multibandCompressor.bandEnables, b, it) }
    val onCrossoverChange: (Int) -> Unit = { viewModel.applyBandPref(Effects.multibandCompressor.crossovers, b, it) }
    val onThresholdChange: (Int) -> Unit = { viewModel.applyBandPref(Effects.multibandCompressor.thresholds, b, it) }
    val onRatioChange: (Int) -> Unit = { viewModel.applyBandPref(Effects.multibandCompressor.ratios, b, it) }
    val onAutoKneeChange: (Boolean) -> Unit = { viewModel.applyBandPref(Effects.multibandCompressor.kneeAutos, b, it) }
    val onKneeChange: (Int) -> Unit = { viewModel.applyBandPref(Effects.multibandCompressor.knees, b, it) }
    val onKneeMultiChange: (Int) -> Unit = { viewModel.applyBandPref(Effects.multibandCompressor.kneeMultis, b, it) }
    val onAutoGainChange: (Boolean) -> Unit = { viewModel.applyBandPref(Effects.multibandCompressor.gainAutos, b, it) }
    val onGainChange: (Int) -> Unit = { viewModel.applyBandPref(Effects.multibandCompressor.gains, b, it) }
    val onAutoAttackChange: (Boolean) -> Unit = { viewModel.applyBandPref(Effects.multibandCompressor.attackAutos, b, it) }
    val onAttackChange: (Int) -> Unit = { viewModel.applyBandPref(Effects.multibandCompressor.attacks, b, it) }
    val onMaxAttackChange: (Int) -> Unit = { viewModel.applyBandPref(Effects.multibandCompressor.maxAttacks, b, it) }
    val onAutoReleaseChange: (Boolean) -> Unit = { viewModel.applyBandPref(Effects.multibandCompressor.releaseAutos, b, it) }
    val onReleaseChange: (Int) -> Unit = { viewModel.applyBandPref(Effects.multibandCompressor.releases, b, it) }
    val onMaxReleaseChange: (Int) -> Unit = { viewModel.applyBandPref(Effects.multibandCompressor.maxReleases, b, it) }
    val onCrestChange: (Int) -> Unit = { viewModel.applyBandPref(Effects.multibandCompressor.crests, b, it) }
    val onAdaptChange: (Int) -> Unit = { viewModel.applyBandPref(Effects.multibandCompressor.adapts, b, it) }
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
            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
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
            value = threshold.toFloat(),
            onValueChange = { onThresholdChange(it.roundToInt()) },
            valueRange = -48f..0f,
            valueLabel = "$threshold dB",
            edit =
                SliderEdit(
                    displayValue = threshold.toDouble(),
                    displayRange = -48.0..0.0,
                    decimals = 0,
                    unit = "dB",
                    onCommit = { onThresholdChange(it.roundToInt().coerceIn(-48, 0)) },
                ),
        )
        LabeledSlider(
            label = stringResource(R.string.label_fet_ratio),
            value = ratio / 100f,
            onValueChange = { onRatioChange((it * 100f).roundToInt()) },
            valueRange = 0f..2f,
            valueLabel = String.format(Locale.US, "%.1f", ratio / 100.0),
            edit =
                SliderEdit(
                    displayValue = ratio / 100.0,
                    displayRange = 0.0..2.0,
                    decimals = 1,
                    onCommit = { onRatioChange((it * 100).roundToInt().coerceIn(0, 200)) },
                ),
        )
        LabeledSwitch(
            label = stringResource(R.string.label_fet_auto_knee),
            checked = kneeAuto,
            onCheckedChange = onAutoKneeChange,
        )
        LabeledSlider(
            label = stringResource(R.string.label_fet_knee),
            value = knee.toFloat(),
            onValueChange = { onKneeChange(it.roundToInt()) },
            valueRange = 0f..12f,
            enabled = !kneeAuto,
            valueLabel = "$knee dB",
            edit =
                SliderEdit(
                    displayValue = knee.toDouble(),
                    displayRange = 0.0..12.0,
                    decimals = 0,
                    unit = "dB",
                    onCommit = { onKneeChange(it.roundToInt().coerceIn(0, 12)) },
                ),
        )
        LabeledSlider(
            label = stringResource(R.string.label_fet_knee_multi),
            value = (kneeMulti / 100f * 4f),
            onValueChange = { onKneeMultiChange((it / 4f * 100f).roundToInt()) },
            valueRange = 0f..4f,
            valueLabel = String.format(Locale.US, "%.1fx", kneeMulti / 100.0 * 4.0),
            edit =
                SliderEdit(
                    displayValue = kneeMulti / 100.0 * 4.0,
                    displayRange = 0.0..4.0,
                    decimals = 1,
                    unit = "x",
                    onCommit = { onKneeMultiChange((it / 4 * 100).roundToInt().coerceIn(0, 100)) },
                ),
        )
        LabeledSwitch(
            label = stringResource(R.string.label_fet_auto_gain),
            checked = gainAuto,
            onCheckedChange = onAutoGainChange,
        )
        LabeledSlider(
            label = stringResource(R.string.label_gain),
            value = gain.toFloat(),
            onValueChange = { onGainChange(it.roundToInt()) },
            valueRange = 0f..24f,
            enabled = !gainAuto,
            valueLabel = "$gain dB",
            edit =
                SliderEdit(
                    displayValue = gain.toDouble(),
                    displayRange = 0.0..24.0,
                    decimals = 0,
                    unit = "dB",
                    onCommit = { onGainChange(it.roundToInt().coerceIn(0, 24)) },
                ),
        )
        LabeledSwitch(
            label = stringResource(R.string.label_fet_auto_attack),
            checked = attackAuto,
            onCheckedChange = onAutoAttackChange,
        )
        LabeledSlider(
            label = stringResource(R.string.label_attack),
            value = attack.toFloat().coerceIn(1f, 100f),
            onValueChange = { onAttackChange(it.roundToInt()) },
            valueRange = 1f..100f,
            enabled = !attackAuto,
            valueLabel = "$attack ms",
            edit =
                SliderEdit(
                    displayValue = attack.toDouble(),
                    displayRange = 1.0..100.0,
                    decimals = 0,
                    unit = "ms",
                    onCommit = { onAttackChange(it.roundToInt().coerceIn(1, 100)) },
                ),
        )
        LabeledSlider(
            label = stringResource(R.string.label_fet_max_attack),
            value = maxAttack.toFloat().coerceIn(1f, 100f),
            onValueChange = { onMaxAttackChange(it.roundToInt()) },
            valueRange = 1f..100f,
            valueLabel = "$maxAttack ms",
            edit =
                SliderEdit(
                    displayValue = maxAttack.toDouble(),
                    displayRange = 1.0..100.0,
                    decimals = 0,
                    unit = "ms",
                    onCommit = { onMaxAttackChange(it.roundToInt().coerceIn(1, 100)) },
                ),
        )
        LabeledSwitch(
            label = stringResource(R.string.label_fet_auto_release),
            checked = releaseAuto,
            onCheckedChange = onAutoReleaseChange,
        )
        LabeledSlider(
            label = stringResource(R.string.label_release),
            value = release.toFloat().coerceIn(5f, 500f),
            onValueChange = { onReleaseChange(it.roundToInt()) },
            valueRange = 5f..500f,
            enabled = !releaseAuto,
            valueLabel = "$release ms",
            edit =
                SliderEdit(
                    displayValue = release.toDouble(),
                    displayRange = 5.0..500.0,
                    decimals = 0,
                    unit = "ms",
                    onCommit = { onReleaseChange(it.roundToInt().coerceIn(5, 500)) },
                ),
        )
        LabeledSlider(
            label = stringResource(R.string.label_fet_max_release),
            value = maxRelease.toFloat().coerceIn(5f, 500f),
            onValueChange = { onMaxReleaseChange(it.roundToInt()) },
            valueRange = 5f..500f,
            valueLabel = "$maxRelease ms",
            edit =
                SliderEdit(
                    displayValue = maxRelease.toDouble(),
                    displayRange = 5.0..500.0,
                    decimals = 0,
                    unit = "ms",
                    onCommit = { onMaxReleaseChange(it.roundToInt().coerceIn(5, 500)) },
                ),
        )
        LabeledSlider(
            label = stringResource(R.string.label_fet_crest),
            value = crest.toFloat().coerceIn(5f, 300f),
            onValueChange = { onCrestChange(it.roundToInt()) },
            valueRange = 5f..300f,
            valueLabel = "$crest ms",
            edit =
                SliderEdit(
                    displayValue = crest.toDouble(),
                    displayRange = 5.0..300.0,
                    decimals = 0,
                    unit = "ms",
                    onCommit = { onCrestChange(it.roundToInt().coerceIn(5, 300)) },
                ),
        )
        LabeledSlider(
            label = stringResource(R.string.label_fet_adapt),
            value = adapt.toFloat(),
            onValueChange = { onAdaptChange(it.roundToInt()) },
            valueRange = 0f..200f,
            valueLabel = "$adapt%",
            edit =
                SliderEdit(
                    displayValue = adapt.toDouble(),
                    displayRange = 0.0..200.0,
                    decimals = 0,
                    unit = "%",
                    onCommit = { onAdaptChange(it.roundToInt().coerceIn(0, 200)) },
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
            value = exciter.toFloat(),
            onValueChange = { viewModel.applyPref(Effects.spectrumExtension.exciter, it.roundToInt()) },
            valueRange = 0f..100f,
            valueLabel = "$exciter%",
            edit =
                SliderEdit(
                    displayValue = exciter.toDouble(),
                    displayRange = 0.0..100.0,
                    decimals = 0,
                    unit = "%",
                    onCommit = { viewModel.applyPref(Effects.spectrumExtension.exciter, it.roundToInt().coerceIn(0, 100)) },
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
        AlertDialog(
            onDismissRequest = { deleteBandIndex = -1 },
            title = { Text(stringResource(R.string.dialog_delete_band)) },
            text = { Text("Remove ${formatFreq(freqs.getOrElse(deleteBandIndex) { 1000 })} band?") },
            confirmButton = {
                TextButton(onClick = {
                    val i = deleteBandIndex
                    deleteBandIndex = -1
                    viewModel.removeDynamicEqBand(i)
                    if (selectedTab >= bandCount - 1) selectedTab = maxOf(0, bandCount - 2)
                }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    deleteBandIndex = -1
                }) { Text(stringResource(R.string.action_cancel)) }
            },
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
            edgePadding = 0.dp,
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
                            ).padding(horizontal = 16.dp, vertical = 12.dp),
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
                            }.padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }

        if (bandCount > 0) {
            val freq = freqs.getOrElse(safeTab) { 1000 }
            val q = qs.getOrElse(safeTab) { 150 }
            val gain = gains.getOrElse(safeTab) { 0 }
            val threshold = thresholds.getOrElse(safeTab) { -300 }
            val attack = attacks.getOrElse(safeTab) { 10 }
            val release = releases.getOrElse(safeTab) { 100 }
            val filterType = filterTypes.getOrElse(safeTab) { 0 }.coerceIn(0, 2)

            val minFreq =
                if (safeTab > 0) (freqs.getOrElse(safeTab - 1) { 20 } + 5).toFloat() else 20f
            val maxFreq =
                if (safeTab < bandCount - 1) (freqs.getOrElse(safeTab + 1) { 20000 } - 5).toFloat() else 20000f

            val onFreqChange: (Int) -> Unit = { viewModel.applyBandPref(Effects.dynamicEq.freqs, safeTab, it, bandCount) }
            val onQChange: (Int) -> Unit = { viewModel.applyBandPref(Effects.dynamicEq.qs, safeTab, it, bandCount) }
            val onGainChange: (Int) -> Unit = { viewModel.applyBandPref(Effects.dynamicEq.gains, safeTab, it, bandCount) }
            val onThresholdChange: (Int) -> Unit =
                { viewModel.applyBandPref(Effects.dynamicEq.thresholds, safeTab, it, bandCount) }
            val onAttackChange: (Int) -> Unit = { viewModel.applyBandPref(Effects.dynamicEq.attacks, safeTab, it, bandCount) }
            val onReleaseChange: (Int) -> Unit = { viewModel.applyBandPref(Effects.dynamicEq.releases, safeTab, it, bandCount) }
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
                value = q.toFloat(),
                onValueChange = { onQChange(it.roundToInt()) },
                valueRange = 50f..800f,
                valueLabel = String.format(Locale.US, "%.1f", q / 100f),
                edit =
                    SliderEdit(
                        displayValue = q / 100.0,
                        displayRange = 0.5..8.0,
                        decimals = 1,
                        onCommit = { onQChange((it * 100).roundToInt().coerceIn(50, 800)) },
                    ),
            )
            LabeledSlider(
                label = stringResource(R.string.label_dynamic_eq_target_gain),
                value = gain.toFloat(),
                onValueChange = { onGainChange(it.roundToInt()) },
                valueRange = -120f..120f,
                valueLabel = String.format(Locale.US, "%.1f dB", gain / 10f),
                edit =
                    SliderEdit(
                        displayValue = gain / 10.0,
                        displayRange = -12.0..12.0,
                        decimals = 1,
                        unit = "dB",
                        onCommit = { onGainChange((it * 10).roundToInt().coerceIn(-120, 120)) },
                    ),
            )
            LabeledSlider(
                label = stringResource(R.string.label_threshold),
                value = threshold.toFloat(),
                onValueChange = { onThresholdChange(it.roundToInt()) },
                valueRange = -800f..0f,
                valueLabel = "${threshold / 10} dB",
                edit =
                    SliderEdit(
                        displayValue = threshold / 10.0,
                        displayRange = -80.0..0.0,
                        decimals = 1,
                        unit = "dB",
                        onCommit = { onThresholdChange((it * 10).roundToInt().coerceIn(-800, 0)) },
                    ),
            )
            LabeledSlider(
                label = stringResource(R.string.label_attack),
                value = attack.toFloat(),
                onValueChange = { onAttackChange(it.roundToInt()) },
                valueRange = 1f..100f,
                valueLabel = "$attack ms",
                edit =
                    SliderEdit(
                        displayValue = attack.toDouble(),
                        displayRange = 1.0..100.0,
                        decimals = 0,
                        unit = "ms",
                        onCommit = { onAttackChange(it.roundToInt().coerceIn(1, 100)) },
                    ),
            )
            LabeledSlider(
                label = stringResource(R.string.label_release),
                value = release.toFloat(),
                onValueChange = { onReleaseChange(it.roundToInt()) },
                valueRange = 10f..500f,
                valueLabel = "$release ms",
                edit =
                    SliderEdit(
                        displayValue = release.toDouble(),
                        displayRange = 10.0..500.0,
                        decimals = 0,
                        unit = "ms",
                        onCommit = { onReleaseChange(it.roundToInt().coerceIn(10, 500)) },
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
            value = crossChannel.toFloat(),
            onValueChange = { viewModel.applyPref(Effects.convolver.crossChannel, it.roundToInt()) },
            valueRange = 0f..100f,
            edit =
                SliderEdit(
                    displayValue = crossChannel.toDouble(),
                    displayRange = 0.0..100.0,
                    decimals = 0,
                    onCommit = { viewModel.applyPref(Effects.convolver.crossChannel, it.roundToInt().coerceIn(0, 100)) },
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
            value = widening.toFloat(),
            onValueChange = { viewModel.applyPref(Effects.fieldSurround.widening, it.roundToInt()) },
            valueRange = 0f..8f,
            steps = 7,
            valueLabel = "$widening",
            edit =
                SliderEdit(
                    displayValue = widening.toDouble(),
                    displayRange = 0.0..8.0,
                    decimals = 0,
                    onCommit = { viewModel.applyPref(Effects.fieldSurround.widening, it.roundToInt().coerceIn(0, 8)) },
                ),
        )
        LabeledSlider(
            label = stringResource(R.string.label_field_surround_mid_image),
            value = midImage.toFloat(),
            onValueChange = { viewModel.applyPref(Effects.fieldSurround.midImage, it.roundToInt()) },
            valueRange = 0f..10f,
            steps = 9,
            edit =
                SliderEdit(
                    displayValue = midImage.toDouble(),
                    displayRange = 0.0..10.0,
                    decimals = 0,
                    onCommit = { viewModel.applyPref(Effects.fieldSurround.midImage, it.roundToInt().coerceIn(0, 10)) },
                ),
        )
        LabeledSlider(
            label = stringResource(R.string.label_depth),
            value = depth.toFloat(),
            onValueChange = { viewModel.applyPref(Effects.fieldSurround.depth, it.roundToInt()) },
            valueRange = 0f..10f,
            steps = 9,
            edit =
                SliderEdit(
                    displayValue = depth.toDouble(),
                    displayRange = 0.0..10.0,
                    decimals = 0,
                    onCommit = { viewModel.applyPref(Effects.fieldSurround.depth, it.roundToInt().coerceIn(0, 10)) },
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
            value = delay.toFloat(),
            onValueChange = { viewModel.applyPref(Effects.diffSurround.delay, it.roundToInt()) },
            valueRange = 1f..20f,
            steps = 18,
            valueLabel = "$delay ms",
            edit =
                SliderEdit(
                    displayValue = delay.toDouble(),
                    displayRange = 1.0..20.0,
                    decimals = 0,
                    unit = "ms",
                    onCommit = { viewModel.applyPref(Effects.diffSurround.delay, it.roundToInt().coerceIn(1, 20)) },
                ),
        )
        LabeledSwitch(
            label = stringResource(R.string.label_diff_surround_reverse),
            checked = reverse,
            onCheckedChange = { viewModel.applyPref(Effects.diffSurround.reverse, it) },
        )
        LabeledSlider(
            label = stringResource(R.string.label_diff_surround_wet_dry_mix),
            value = wetDryMix.toFloat(),
            onValueChange = { viewModel.applyPref(Effects.diffSurround.wetDryMix, it.roundToInt()) },
            valueRange = 0f..100f,
            valueLabel = "$wetDryMix%",
            edit =
                SliderEdit(
                    displayValue = wetDryMix.toDouble(),
                    displayRange = 0.0..100.0,
                    decimals = 0,
                    unit = "%",
                    onCommit = { viewModel.applyPref(Effects.diffSurround.wetDryMix, it.roundToInt().coerceIn(0, 100)) },
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
            value = lowWidth.toFloat(),
            onValueChange = { viewModel.applyPref(Effects.stereoImager.lowWidth, it.roundToInt()) },
            valueRange = 0f..200f,
            valueLabel = "$lowWidth%",
            edit =
                SliderEdit(
                    displayValue = lowWidth.toDouble(),
                    displayRange = 0.0..200.0,
                    decimals = 0,
                    unit = "%",
                    onCommit = { viewModel.applyPref(Effects.stereoImager.lowWidth, it.roundToInt().coerceIn(0, 200)) },
                ),
        )
        LabeledSlider(
            label = stringResource(R.string.label_stereo_imager_mid_width),
            value = midWidth.toFloat(),
            onValueChange = { viewModel.applyPref(Effects.stereoImager.midWidth, it.roundToInt()) },
            valueRange = 0f..200f,
            valueLabel = "$midWidth%",
            edit =
                SliderEdit(
                    displayValue = midWidth.toDouble(),
                    displayRange = 0.0..200.0,
                    decimals = 0,
                    unit = "%",
                    onCommit = { viewModel.applyPref(Effects.stereoImager.midWidth, it.roundToInt().coerceIn(0, 200)) },
                ),
        )
        LabeledSlider(
            label = stringResource(R.string.label_stereo_imager_high_width),
            value = highWidth.toFloat(),
            onValueChange = { viewModel.applyPref(Effects.stereoImager.highWidth, it.roundToInt()) },
            valueRange = 0f..200f,
            valueLabel = "$highWidth%",
            edit =
                SliderEdit(
                    displayValue = highWidth.toDouble(),
                    displayRange = 0.0..200.0,
                    decimals = 0,
                    unit = "%",
                    onCommit = { viewModel.applyPref(Effects.stereoImager.highWidth, it.roundToInt().coerceIn(0, 200)) },
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
            value = roomSize.toFloat(),
            onValueChange = { viewModel.applyPref(Effects.reverb.roomSize, it.roundToInt()) },
            valueRange = 0f..10f,
            steps = 9,
            edit =
                SliderEdit(
                    displayValue = roomSize.toDouble(),
                    displayRange = 0.0..10.0,
                    decimals = 0,
                    onCommit = { viewModel.applyPref(Effects.reverb.roomSize, it.roundToInt().coerceIn(0, 10)) },
                ),
        )
        LabeledSlider(
            label = stringResource(R.string.label_width),
            value = width.toFloat(),
            onValueChange = { viewModel.applyPref(Effects.reverb.width, it.roundToInt()) },
            valueRange = 0f..10f,
            steps = 9,
            edit =
                SliderEdit(
                    displayValue = width.toDouble(),
                    displayRange = 0.0..10.0,
                    decimals = 0,
                    onCommit = { viewModel.applyPref(Effects.reverb.width, it.roundToInt().coerceIn(0, 10)) },
                ),
        )
        LabeledSlider(
            label = stringResource(R.string.label_reverb_dampening),
            value = damp.toFloat(),
            onValueChange = { viewModel.applyPref(Effects.reverb.damp, it.roundToInt()) },
            valueRange = 0f..10f,
            steps = 9,
            edit =
                SliderEdit(
                    displayValue = damp.toDouble(),
                    displayRange = 0.0..10.0,
                    decimals = 0,
                    onCommit = { viewModel.applyPref(Effects.reverb.damp, it.roundToInt().coerceIn(0, 10)) },
                ),
        )
        LabeledSlider(
            label = stringResource(R.string.label_reverb_wet),
            value = wet.toFloat(),
            onValueChange = { viewModel.applyPref(Effects.reverb.wet, it.roundToInt()) },
            valueRange = 0f..100f,
            valueLabel = "$wet%",
            edit =
                SliderEdit(
                    displayValue = wet.toDouble(),
                    displayRange = 0.0..100.0,
                    decimals = 0,
                    unit = "%",
                    onCommit = { viewModel.applyPref(Effects.reverb.wet, it.roundToInt().coerceIn(0, 100)) },
                ),
        )
        LabeledSlider(
            label = stringResource(R.string.label_reverb_dry),
            value = dry.toFloat(),
            onValueChange = { viewModel.applyPref(Effects.reverb.dry, it.roundToInt()) },
            valueRange = 0f..100f,
            valueLabel = "$dry%",
            edit =
                SliderEdit(
                    displayValue = dry.toDouble(),
                    displayRange = 0.0..100.0,
                    decimals = 0,
                    unit = "%",
                    onCommit = { viewModel.applyPref(Effects.reverb.dry, it.roundToInt().coerceIn(0, 100)) },
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
    var presetNameInput by remember { mutableStateOf("") }

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
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TextButton(onClick = { showSaveDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(stringResource(R.string.action_save))
            }
            TextButton(
                onClick = { dsPresetId?.let { onPresetDelete(it) } },
                enabled = dsPresetId != null,
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(stringResource(R.string.action_delete))
            }
            TextButton(onClick = onReset) {
                Icon(
                    Icons.Default.RestartAlt,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(stringResource(R.string.action_reset))
            }
        }

        LabeledSlider(
            label = stringResource(R.string.label_dynamic_system_strength),
            value = strength.toFloat(),
            onValueChange = { viewModel.applyPref(Effects.dynamicSystem.strength, it.roundToInt()) },
            valueRange = 0f..100f,
            valueLabel = "$strength%",
            edit =
                SliderEdit(
                    displayValue = strength.toDouble(),
                    displayRange = 0.0..100.0,
                    decimals = 0,
                    unit = "%",
                    onCommit = { viewModel.applyPref(Effects.dynamicSystem.strength, it.roundToInt().coerceIn(0, 100)) },
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
            value = sideGainLow.toFloat(),
            onValueChange = { onSideGainLowChange(it.roundToInt()) },
            valueRange = 0f..100f,
            valueLabel = "$sideGainLow%",
            edit =
                SliderEdit(
                    displayValue = sideGainLow.toDouble(),
                    displayRange = 0.0..100.0,
                    decimals = 0,
                    unit = "%",
                    onCommit = { onSideGainLowChange(it.roundToInt().coerceIn(0, 100)) },
                ),
        )

        LabeledSlider(
            label = stringResource(R.string.label_dynamic_system_side_gain_high),
            value = sideGainHigh.toFloat(),
            onValueChange = { onSideGainHighChange(it.roundToInt()) },
            valueRange = 0f..100f,
            valueLabel = "$sideGainHigh%",
            edit =
                SliderEdit(
                    displayValue = sideGainHigh.toDouble(),
                    displayRange = 0.0..100.0,
                    decimals = 0,
                    unit = "%",
                    onCommit = { onSideGainHighChange(it.roundToInt().coerceIn(0, 100)) },
                ),
        )
    }

    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text(stringResource(R.string.preset_save_title)) },
            text = {
                OutlinedTextField(
                    value = presetNameInput,
                    onValueChange = { presetNameInput = it },
                    label = { Text(stringResource(R.string.preset_name_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (presetNameInput.isNotBlank()) {
                            onPresetAdd(presetNameInput.trim())
                            presetNameInput = ""
                            showSaveDialog = false
                        }
                    },
                ) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveDialog = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
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

    EffectSection(
        title = stringResource(R.string.section_tube_simulator),
        enabled = enabled,
        onEnabledChange = viewModel::setTubeSimulatorEnabled,
        descriptionRes = R.string.effect_desc_tube_simulator,
        icon = Icons.Default.MusicNote,
        toggleOnly = true,
    ) {}
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
            value = intensity.toFloat(),
            onValueChange = { viewModel.applyPref(Effects.psychoacousticBass.intensity, it.roundToInt()) },
            valueRange = 0f..100f,
            valueLabel = "$intensity%",
            edit =
                SliderEdit(
                    displayValue = intensity.toDouble(),
                    displayRange = 0.0..100.0,
                    decimals = 0,
                    unit = "%",
                    onCommit = { viewModel.applyPref(Effects.psychoacousticBass.intensity, it.roundToInt().coerceIn(0, 100)) },
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
            value = originalLevel.toFloat(),
            onValueChange = { viewModel.applyPref(Effects.psychoacousticBass.originalLevel, it.roundToInt()) },
            valueRange = 0f..100f,
            valueLabel = "$originalLevel%",
            edit =
                SliderEdit(
                    displayValue = originalLevel.toDouble(),
                    displayRange = 0.0..100.0,
                    decimals = 0,
                    unit = "%",
                    onCommit = { viewModel.applyPref(Effects.psychoacousticBass.originalLevel, it.roundToInt().coerceIn(0, 100)) },
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
                valueRange = 0f..135f,
                steps = 134,
                valueLabel = "${frequency + 15}Hz",
                edit =
                    SliderEdit(
                        displayValue = (frequency + 15).toDouble(),
                        displayRange = 15.0..150.0,
                        decimals = 0,
                        unit = "Hz",
                        onCommit = { viewModel.applyPref(Effects.bass.frequency, (it - 15).roundToInt().coerceIn(0, 135)) },
                    ),
            )
        }
        LabeledSlider(
            label = stringResource(R.string.label_gain),
            value = gain.toFloat(),
            onValueChange = { viewModel.applyPref(Effects.bass.gain, it.roundToInt()) },
            valueRange = 50f..1000f,
            valueLabel = "${"%.1f".format(gain / 100.0)}x",
            edit =
                SliderEdit(
                    displayValue = gain / 100.0,
                    displayRange = 0.5..10.0,
                    decimals = 1,
                    unit = "x",
                    onCommit = { viewModel.applyPref(Effects.bass.gain, (it * 100).roundToInt().coerceIn(50, 1000)) },
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
                valueRange = 0f..135f,
                steps = 134,
                valueLabel = "${frequency + 15}Hz",
                edit =
                    SliderEdit(
                        displayValue = (frequency + 15).toDouble(),
                        displayRange = 15.0..150.0,
                        decimals = 0,
                        unit = "Hz",
                        onCommit = { viewModel.applyPref(Effects.bassMono.frequency, (it - 15).roundToInt().coerceIn(0, 135)) },
                    ),
            )
        }
        LabeledSlider(
            label = stringResource(R.string.label_gain),
            value = gain.toFloat(),
            onValueChange = { viewModel.applyPref(Effects.bassMono.gain, it.roundToInt()) },
            valueRange = 50f..1000f,
            valueLabel = "${"%.1f".format(gain / 100.0)}x",
            edit =
                SliderEdit(
                    displayValue = gain / 100.0,
                    displayRange = 0.5..10.0,
                    decimals = 1,
                    unit = "x",
                    onCommit = { viewModel.applyPref(Effects.bassMono.gain, (it * 100).roundToInt().coerceIn(50, 1000)) },
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
            value = gain.toFloat(),
            onValueChange = { viewModel.applyPref(Effects.clarity.gain, it.roundToInt()) },
            valueRange = 0f..450f,
            valueLabel = "${"%.1f".format(gain / 100.0)}x",
            edit =
                SliderEdit(
                    displayValue = gain / 100.0,
                    displayRange = 0.0..4.5,
                    decimals = 1,
                    unit = "x",
                    onCommit = { viewModel.applyPref(Effects.clarity.gain, (it * 100).roundToInt().coerceIn(0, 450)) },
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
