package com.llsl.viper4android.ui.screens.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.SpatialAudio
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material.icons.filled.SurroundSound
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.llsl.viper4android.R
import com.llsl.viper4android.ui.components.EffectSection
import com.llsl.viper4android.ui.components.EqCurveGraph
import com.llsl.viper4android.ui.components.EqEditDialog
import com.llsl.viper4android.ui.components.LabeledDropdown
import com.llsl.viper4android.ui.components.LabeledSlider
import com.llsl.viper4android.ui.components.LabeledSwitch
import kotlin.math.log10
import kotlin.math.roundToInt

@Composable
fun MasterLimiterSection(state: MainUiState, viewModel: MainViewModel, isSpkMode: Boolean = false) {
    val outputVolume = if (isSpkMode) state.spkOutputVolume else state.outputVolume
    val limiter = if (isSpkMode) state.spkLimiter else state.limiter
    val onOutputVolumeChange: (Int) -> Unit =
        if (isSpkMode) viewModel::setSpkOutputVolume else viewModel::setOutputVolume
    val onLimiterChange: (Int) -> Unit =
        if (isSpkMode) viewModel::setSpkLimiter else viewModel::setLimiter

    val masterEnabled = if (isSpkMode) state.spkMasterEnabled else state.masterEnabled
    val onMasterEnabledChange: (Boolean) -> Unit =
        if (isSpkMode) viewModel::setSpkMasterEnabled else viewModel::setMasterEnabled

    EffectSection(
        title = stringResource(R.string.section_output),
        enabled = masterEnabled,
        onEnabledChange = onMasterEnabledChange,
        icon = Icons.AutoMirrored.Filled.VolumeUp,
        initiallyExpanded = true
    ) {
        val gainPct = MainViewModel.OUTPUT_VOLUME_VALUES.getOrElse(outputVolume) { 100 }
        val gainDb = if (gainPct > 0) 20.0 * log10(gainPct / 100.0) else -99.9
        LabeledSlider(
            label = stringResource(R.string.label_volume),
            value = outputVolume.toFloat(),
            onValueChange = { onOutputVolumeChange(it.roundToInt()) },
            valueRange = 0f..21f,
            steps = 20,
            valueLabel = "${"%.1f".format(gainDb)}dB"
        )
        if (!isSpkMode) {
            val left = 50 - state.channelPan / 2
            val right = 50 + state.channelPan / 2
            LabeledSlider(
                label = stringResource(R.string.label_pan),
                value = state.channelPan.toFloat(),
                onValueChange = { viewModel.setChannelPan(it.roundToInt()) },
                valueRange = -100f..100f,
                valueLabel = "${left}:${right}"
            )
        }
        val limPct = MainViewModel.OUTPUT_DB_VALUES.getOrElse(limiter) { 100 }
        val limDb = if (limPct > 0) 20.0 * log10(limPct / 100.0) else -99.9
        LabeledSlider(
            label = stringResource(R.string.label_limiter),
            value = limiter.toFloat(),
            onValueChange = { onLimiterChange(it.roundToInt()) },
            valueRange = 0f..5f,
            steps = 4,
            valueLabel = "${"%.1f".format(limDb)}dB"
        )
    }
}

@Composable
fun PlaybackGainSection(state: MainUiState, viewModel: MainViewModel, isSpkMode: Boolean = false) {
    val enabled = if (isSpkMode) state.spkAgcEnabled else state.agcEnabled
    val strength = if (isSpkMode) state.spkAgcStrength else state.agcStrength
    val maxGain = if (isSpkMode) state.spkAgcMaxGain else state.agcMaxGain
    val threshold = if (isSpkMode) state.spkAgcOutputThreshold else state.agcOutputThreshold
    val onEnabledChange: (Boolean) -> Unit =
        if (isSpkMode) viewModel::setSpkAgcEnabled else viewModel::setAgcEnabled
    val onStrengthChange: (Int) -> Unit =
        if (isSpkMode) viewModel::setSpkAgcStrength else viewModel::setAgcStrength
    val onMaxGainChange: (Int) -> Unit =
        if (isSpkMode) viewModel::setSpkAgcMaxGain else viewModel::setAgcMaxGain
    val onThresholdChange: (Int) -> Unit =
        if (isSpkMode) viewModel::setSpkAgcOutputThreshold else viewModel::setAgcOutputThreshold

    EffectSection(
        title = stringResource(R.string.section_agc),
        enabled = enabled,
        onEnabledChange = onEnabledChange,
        icon = Icons.AutoMirrored.Filled.TrendingUp
    ) {
        LabeledSlider(
            label = stringResource(R.string.label_agc_strength),
            value = strength.toFloat(),
            onValueChange = { onStrengthChange(it.roundToInt()) },
            valueRange = 0f..2f,
            steps = 1
        )
        LabeledSlider(
            label = stringResource(R.string.label_agc_max_gain),
            value = maxGain.toFloat(),
            onValueChange = { onMaxGainChange(it.roundToInt()) },
            valueRange = 0f..10f,
            steps = 9
        )
        val threshPct = MainViewModel.OUTPUT_DB_VALUES.getOrElse(threshold) { 100 }
        val threshDb = if (threshPct > 0) 20.0 * log10(threshPct / 100.0) else -99.9
        LabeledSlider(
            label = stringResource(R.string.label_agc_output_threshold),
            value = threshold.toFloat(),
            onValueChange = { onThresholdChange(it.roundToInt()) },
            valueRange = 0f..5f,
            steps = 4,
            valueLabel = "${"%.1f".format(threshDb)}dB"
        )
    }
}

@Composable
fun FetCompressorSection(state: MainUiState, viewModel: MainViewModel, isSpkMode: Boolean = false) {
    val fetEnabled = if (isSpkMode) state.spkFetEnabled else state.fetEnabled
    val fetThreshold = if (isSpkMode) state.spkFetThreshold else state.fetThreshold
    val fetRatio = if (isSpkMode) state.spkFetRatio else state.fetRatio
    val fetAutoKnee = if (isSpkMode) state.spkFetAutoKnee else state.fetAutoKnee
    val fetKnee = if (isSpkMode) state.spkFetKnee else state.fetKnee
    val fetKneeMulti = if (isSpkMode) state.spkFetKneeMulti else state.fetKneeMulti
    val fetAutoGain = if (isSpkMode) state.spkFetAutoGain else state.fetAutoGain
    val fetGain = if (isSpkMode) state.spkFetGain else state.fetGain
    val fetAutoAttack = if (isSpkMode) state.spkFetAutoAttack else state.fetAutoAttack
    val fetAttack = if (isSpkMode) state.spkFetAttack else state.fetAttack
    val fetMaxAttack = if (isSpkMode) state.spkFetMaxAttack else state.fetMaxAttack
    val fetAutoRelease = if (isSpkMode) state.spkFetAutoRelease else state.fetAutoRelease
    val fetRelease = if (isSpkMode) state.spkFetRelease else state.fetRelease
    val fetMaxRelease = if (isSpkMode) state.spkFetMaxRelease else state.fetMaxRelease
    val fetCrest = if (isSpkMode) state.spkFetCrest else state.fetCrest
    val fetAdapt = if (isSpkMode) state.spkFetAdapt else state.fetAdapt
    val fetNoClip = if (isSpkMode) state.spkFetNoClip else state.fetNoClip

    val onEnabledChange: (Boolean) -> Unit =
        if (isSpkMode) viewModel::setSpkFetEnabled else viewModel::setFetEnabled
    val onThresholdChange: (Int) -> Unit =
        if (isSpkMode) viewModel::setSpkFetThreshold else viewModel::setFetThreshold
    val onRatioChange: (Int) -> Unit =
        if (isSpkMode) viewModel::setSpkFetRatio else viewModel::setFetRatio
    val onAutoKneeChange: (Boolean) -> Unit =
        if (isSpkMode) viewModel::setSpkFetAutoKnee else viewModel::setFetAutoKnee
    val onKneeChange: (Int) -> Unit =
        if (isSpkMode) viewModel::setSpkFetKnee else viewModel::setFetKnee
    val onKneeMultiChange: (Int) -> Unit =
        if (isSpkMode) viewModel::setSpkFetKneeMulti else viewModel::setFetKneeMulti
    val onAutoGainChange: (Boolean) -> Unit =
        if (isSpkMode) viewModel::setSpkFetAutoGain else viewModel::setFetAutoGain
    val onGainChange: (Int) -> Unit =
        if (isSpkMode) viewModel::setSpkFetGain else viewModel::setFetGain
    val onAutoAttackChange: (Boolean) -> Unit =
        if (isSpkMode) viewModel::setSpkFetAutoAttack else viewModel::setFetAutoAttack
    val onAttackChange: (Int) -> Unit =
        if (isSpkMode) viewModel::setSpkFetAttack else viewModel::setFetAttack
    val onMaxAttackChange: (Int) -> Unit =
        if (isSpkMode) viewModel::setSpkFetMaxAttack else viewModel::setFetMaxAttack
    val onAutoReleaseChange: (Boolean) -> Unit =
        if (isSpkMode) viewModel::setSpkFetAutoRelease else viewModel::setFetAutoRelease
    val onReleaseChange: (Int) -> Unit =
        if (isSpkMode) viewModel::setSpkFetRelease else viewModel::setFetRelease
    val onMaxReleaseChange: (Int) -> Unit =
        if (isSpkMode) viewModel::setSpkFetMaxRelease else viewModel::setFetMaxRelease
    val onCrestChange: (Int) -> Unit =
        if (isSpkMode) viewModel::setSpkFetCrest else viewModel::setFetCrest
    val onAdaptChange: (Int) -> Unit =
        if (isSpkMode) viewModel::setSpkFetAdapt else viewModel::setFetAdapt
    val onNoClipChange: (Boolean) -> Unit =
        if (isSpkMode) viewModel::setSpkFetNoClip else viewModel::setFetNoClip

    EffectSection(
        title = stringResource(R.string.section_fet_compressor),
        enabled = fetEnabled,
        onEnabledChange = onEnabledChange,
        icon = Icons.Default.Compress
    ) {
        LabeledSlider(
            label = stringResource(R.string.label_fet_threshold),
            value = fetThreshold.toFloat(),
            onValueChange = { onThresholdChange(it.roundToInt()) },
            valueRange = 0f..200f
        )
        LabeledSlider(
            label = stringResource(R.string.label_fet_ratio),
            value = fetRatio.toFloat(),
            onValueChange = { onRatioChange(it.roundToInt()) },
            valueRange = 0f..200f
        )
        LabeledSwitch(
            label = stringResource(R.string.label_fet_auto_knee),
            checked = fetAutoKnee,
            onCheckedChange = onAutoKneeChange
        )
        LabeledSlider(
            label = stringResource(R.string.label_fet_knee),
            value = fetKnee.toFloat(),
            onValueChange = { onKneeChange(it.roundToInt()) },
            valueRange = 0f..200f,
            enabled = !fetAutoKnee
        )
        LabeledSlider(
            label = stringResource(R.string.label_fet_knee_multi),
            value = fetKneeMulti.toFloat(),
            onValueChange = { onKneeMultiChange(it.roundToInt()) },
            valueRange = 0f..200f
        )
        LabeledSwitch(
            label = stringResource(R.string.label_fet_auto_gain),
            checked = fetAutoGain,
            onCheckedChange = onAutoGainChange
        )
        LabeledSlider(
            label = stringResource(R.string.label_fet_gain),
            value = fetGain.toFloat(),
            onValueChange = { onGainChange(it.roundToInt()) },
            valueRange = 0f..200f,
            enabled = !fetAutoGain
        )
        LabeledSwitch(
            label = stringResource(R.string.label_fet_auto_attack),
            checked = fetAutoAttack,
            onCheckedChange = onAutoAttackChange
        )
        LabeledSlider(
            label = stringResource(R.string.label_fet_attack),
            value = fetAttack.toFloat(),
            onValueChange = { onAttackChange(it.roundToInt()) },
            valueRange = 0f..200f,
            enabled = !fetAutoAttack
        )
        LabeledSlider(
            label = stringResource(R.string.label_fet_max_attack),
            value = fetMaxAttack.toFloat(),
            onValueChange = { onMaxAttackChange(it.roundToInt()) },
            valueRange = 0f..200f
        )
        LabeledSwitch(
            label = stringResource(R.string.label_fet_auto_release),
            checked = fetAutoRelease,
            onCheckedChange = onAutoReleaseChange
        )
        LabeledSlider(
            label = stringResource(R.string.label_fet_release),
            value = fetRelease.toFloat(),
            onValueChange = { onReleaseChange(it.roundToInt()) },
            valueRange = 0f..200f,
            enabled = !fetAutoRelease
        )
        LabeledSlider(
            label = stringResource(R.string.label_fet_max_release),
            value = fetMaxRelease.toFloat(),
            onValueChange = { onMaxReleaseChange(it.roundToInt()) },
            valueRange = 0f..200f
        )
        LabeledSlider(
            label = stringResource(R.string.label_fet_crest),
            value = fetCrest.toFloat(),
            onValueChange = { onCrestChange(it.roundToInt()) },
            valueRange = 0f..300f
        )
        LabeledSlider(
            label = stringResource(R.string.label_fet_adapt),
            value = fetAdapt.toFloat(),
            onValueChange = { onAdaptChange(it.roundToInt()) },
            valueRange = 0f..200f
        )
        LabeledSwitch(
            label = stringResource(R.string.label_fet_no_clip),
            checked = fetNoClip,
            onCheckedChange = onNoClipChange
        )
    }
}

@Composable
fun DdcSection(state: MainUiState, viewModel: MainViewModel, isSpkMode: Boolean = false) {
    val ddcEnabled = if (isSpkMode) state.spkDdcEnabled else state.ddcEnabled
    val ddcDevice = if (isSpkMode) state.spkDdcDevice else state.ddcDevice
    val onEnabledChange: (Boolean) -> Unit =
        if (isSpkMode) viewModel::setSpkDdcEnabled else viewModel::setDdcEnabled
    val onDeviceChange: (String) -> Unit =
        if (isSpkMode) viewModel::setSpkDdcDevice else viewModel::setDdcDevice

    val vdcFiles by viewModel.vdcFileList.collectAsStateWithLifecycle()
    val noneLabel = stringResource(R.string.label_ddc_none)
    val ddcOptions = listOf(noneLabel) + vdcFiles

    EffectSection(
        title = stringResource(R.string.section_ddc),
        enabled = ddcEnabled,
        onEnabledChange = onEnabledChange,
        icon = Icons.Default.Tune
    ) {
        LabeledDropdown(
            label = stringResource(R.string.label_ddc_device),
            selectedValue = ddcDevice.ifEmpty { noneLabel },
            options = ddcOptions,
            onOptionSelected = { index, value ->
                onDeviceChange(if (index == 0) "" else value)
            },
            onDeleteOption = { _, name -> viewModel.deleteVdcFile(name) }
        )
    }
}

@Composable
fun SpectrumExtensionSection(
    state: MainUiState,
    viewModel: MainViewModel,
    isSpkMode: Boolean = false
) {
    val vseEnabled = if (isSpkMode) state.spkVseEnabled else state.vseEnabled
    val vseStrength = if (isSpkMode) state.spkVseStrength else state.vseStrength
    val vseExciter = if (isSpkMode) state.spkVseExciter else state.vseExciter
    val onEnabledChange: (Boolean) -> Unit =
        if (isSpkMode) viewModel::setSpkVseEnabled else viewModel::setVseEnabled
    val onStrengthChange: (Int) -> Unit =
        if (isSpkMode) viewModel::setSpkVseStrength else viewModel::setVseStrength
    val onExciterChange: (Int) -> Unit =
        if (isSpkMode) viewModel::setSpkVseExciter else viewModel::setVseExciter

    EffectSection(
        title = stringResource(R.string.section_spectrum_extension),
        enabled = vseEnabled,
        onEnabledChange = onEnabledChange,
        icon = Icons.Default.Waves
    ) {
        LabeledSlider(
            label = stringResource(R.string.label_vse_strength),
            value = vseStrength.toFloat(),
            onValueChange = { onStrengthChange(it.roundToInt()) },
            valueRange = 0f..10f,
            steps = 9,
            valueLabel = "$vseStrength"
        )
        LabeledSlider(
            label = stringResource(R.string.label_vse_exciter),
            value = vseExciter.toFloat(),
            onValueChange = { onExciterChange(it.roundToInt()) },
            valueRange = 0f..100f,
            valueLabel = "${vseExciter}%"
        )
    }
}

@Composable
fun EqualizerSection(state: MainUiState, viewModel: MainViewModel, isSpkMode: Boolean = false) {
    val eqEnabled = if (isSpkMode) state.spkEqEnabled else state.eqEnabled
    val eqBandCount = if (isSpkMode) state.spkEqBandCount else state.eqBandCount
    val eqPresetId = if (isSpkMode) state.spkEqPresetId else state.eqPresetId
    val eqBands = if (isSpkMode) state.spkEqBands else state.eqBands
    val onEnabledChange: (Boolean) -> Unit =
        if (isSpkMode) viewModel::setSpkEqEnabled else viewModel::setEqEnabled
    val onBandCountChange: (Int) -> Unit =
        if (isSpkMode) viewModel::setSpkEqBandCount else viewModel::setEqBandCount
    val onPresetSelect: (Long) -> Unit =
        if (isSpkMode) viewModel::setSpkEqPreset else viewModel::setEqPreset
    val onBandsChange: (String) -> Unit =
        if (isSpkMode) viewModel::setSpkEqBands else viewModel::setEqBands
    val eqPresets = if (isSpkMode) state.spkEqPresets else state.eqPresets
    val onPresetAdd: (String) -> Unit =
        if (isSpkMode) viewModel::addSpkEqPreset else viewModel::addEqPreset
    val onPresetDelete: (Long) -> Unit =
        if (isSpkMode) viewModel::deleteSpkEqPreset else viewModel::deleteEqPreset
    val onReset: () -> Unit = if (isSpkMode) viewModel::resetSpkEqBands else viewModel::resetEqBands

    val bands = remember(eqBands) {
        eqBands.split(";").mapNotNull { it.toFloatOrNull() }
    }

    EffectSection(
        title = stringResource(R.string.section_equalizer),
        enabled = eqEnabled,
        onEnabledChange = onEnabledChange,
        icon = Icons.Default.Equalizer
    ) {
        var showEqDialog by remember { mutableStateOf(false) }

        val bandCounts = listOf(10, 15, 25, 31)
        val bandCountOptions = bandCounts.map { stringResource(R.string.label_eq_n_bands, it) }
        val bandCountIndex = when (eqBandCount) {
            15 -> 1; 25 -> 2; 31 -> 3; else -> 0
        }
        LabeledDropdown(
            label = stringResource(R.string.label_eq_bands),
            selectedValue = bandCountOptions[bandCountIndex],
            options = bandCountOptions,
            onOptionSelected = { index, _ -> onBandCountChange(bandCounts[index]) }
        )

        if (bands.size >= eqBandCount) {
            EqCurveGraph(
                bands = bands,
                onClick = { showEqDialog = true },
                bandCount = eqBandCount
            )
        }

        if (showEqDialog) {
            EqEditDialog(
                bands = bands,
                onBandsChange = onBandsChange,
                presetId = eqPresetId,
                presets = eqPresets,
                onPresetSelect = onPresetSelect,
                onPresetAdd = onPresetAdd,
                onPresetDelete = onPresetDelete,
                onReset = onReset,
                onDismiss = { showEqDialog = false },
                bandCount = eqBandCount
            )
        }
    }
}

@Composable
fun ConvolverSection(state: MainUiState, viewModel: MainViewModel, isSpkMode: Boolean = false) {
    val convolverEnabled = if (isSpkMode) state.spkConvolverEnabled else state.convolverEnabled
    val convolverKernel = if (isSpkMode) state.spkConvolverKernel else state.convolverKernel
    val convolverCrossChannel =
        if (isSpkMode) state.spkConvolverCrossChannel else state.convolverCrossChannel
    val onEnabledChange: (Boolean) -> Unit =
        if (isSpkMode) viewModel::setSpkConvolverEnabled else viewModel::setConvolverEnabled
    val onKernelChange: (String) -> Unit =
        if (isSpkMode) viewModel::setSpkConvolverKernel else viewModel::setConvolverKernel
    val onCrossChannelChange: (Int) -> Unit =
        if (isSpkMode) viewModel::setSpkConvolverCrossChannel else viewModel::setConvolverCrossChannel

    val kernelFiles by viewModel.kernelFileList.collectAsStateWithLifecycle()
    val kernelNoneLabel = stringResource(R.string.label_convolver_none)
    val kernelOptions = listOf(kernelNoneLabel) + kernelFiles

    EffectSection(
        title = stringResource(R.string.section_convolver),
        enabled = convolverEnabled,
        onEnabledChange = onEnabledChange,
        icon = Icons.Default.GraphicEq
    ) {
        LabeledDropdown(
            label = stringResource(R.string.label_convolver_kernel),
            selectedValue = convolverKernel.ifEmpty { kernelNoneLabel },
            options = kernelOptions,
            onOptionSelected = { index, value ->
                onKernelChange(if (index == 0) "" else value)
            },
            onDeleteOption = { _, name -> viewModel.deleteKernelFile(name) }
        )
        LabeledSlider(
            label = stringResource(R.string.label_convolver_cross_channel),
            value = convolverCrossChannel.toFloat(),
            onValueChange = { onCrossChannelChange(it.roundToInt()) },
            valueRange = 0f..100f
        )
    }
}

@Composable
fun FieldSurroundSection(state: MainUiState, viewModel: MainViewModel, isSpkMode: Boolean = false) {
    val enabled = if (isSpkMode) state.spkFieldSurroundEnabled else state.fieldSurroundEnabled
    val widening = if (isSpkMode) state.spkFieldSurroundWidening else state.fieldSurroundWidening
    val midImage = if (isSpkMode) state.spkFieldSurroundMidImage else state.fieldSurroundMidImage
    val depth = if (isSpkMode) state.spkFieldSurroundDepth else state.fieldSurroundDepth
    val onEnabledChange: (Boolean) -> Unit =
        if (isSpkMode) viewModel::setSpkFieldSurroundEnabled else viewModel::setFieldSurroundEnabled
    val onWideningChange: (Int) -> Unit =
        if (isSpkMode) viewModel::setSpkFieldSurroundWidening else viewModel::setFieldSurroundWidening
    val onMidImageChange: (Int) -> Unit =
        if (isSpkMode) viewModel::setSpkFieldSurroundMidImage else viewModel::setFieldSurroundMidImage
    val onDepthChange: (Int) -> Unit =
        if (isSpkMode) viewModel::setSpkFieldSurroundDepth else viewModel::setFieldSurroundDepth

    EffectSection(
        title = stringResource(R.string.section_field_surround),
        enabled = enabled,
        onEnabledChange = onEnabledChange,
        icon = Icons.Default.SurroundSound
    ) {
        LabeledSlider(
            label = stringResource(R.string.label_fs_widening),
            value = widening.toFloat(),
            onValueChange = { onWideningChange(it.roundToInt()) },
            valueRange = 0f..8f,
            steps = 7
        )
        LabeledSlider(
            label = stringResource(R.string.label_fs_mid_image),
            value = midImage.toFloat(),
            onValueChange = { onMidImageChange(it.roundToInt()) },
            valueRange = 0f..10f,
            steps = 9
        )
        LabeledSlider(
            label = stringResource(R.string.label_fs_depth),
            value = depth.toFloat(),
            onValueChange = { onDepthChange(it.roundToInt()) },
            valueRange = 0f..10f,
            steps = 9
        )
    }
}

@Composable
fun DiffSurroundSection(state: MainUiState, viewModel: MainViewModel, isSpkMode: Boolean = false) {
    val enabled = if (isSpkMode) state.spkDiffSurroundEnabled else state.diffSurroundEnabled
    val delay = if (isSpkMode) state.spkDiffSurroundDelay else state.diffSurroundDelay
    val onEnabledChange: (Boolean) -> Unit =
        if (isSpkMode) viewModel::setSpkDiffSurroundEnabled else viewModel::setDiffSurroundEnabled
    val onDelayChange: (Int) -> Unit =
        if (isSpkMode) viewModel::setSpkDiffSurroundDelay else viewModel::setDiffSurroundDelay

    val delayValue = MainViewModel.DIFF_SURROUND_DELAY_VALUES.getOrElse(delay) { 500 }

    EffectSection(
        title = stringResource(R.string.section_differential_surround),
        enabled = enabled,
        onEnabledChange = onEnabledChange,
        icon = Icons.Default.SpatialAudio
    ) {
        LabeledSlider(
            label = stringResource(R.string.label_ds_delay),
            value = delay.toFloat(),
            onValueChange = { onDelayChange(it.roundToInt()) },
            valueRange = 0f..19f,
            steps = 18,
            valueLabel = "${delayValue / 100}ms"
        )
    }
}

@Composable
fun HeadphoneSurroundSection(
    state: MainUiState,
    viewModel: MainViewModel,
    isSpkMode: Boolean = false
) {
    val enabled = if (isSpkMode) state.spkVheEnabled else state.vheEnabled
    val quality = if (isSpkMode) state.spkVheQuality else state.vheQuality
    val onEnabledChange: (Boolean) -> Unit =
        if (isSpkMode) viewModel::setSpkVheEnabled else viewModel::setVheEnabled
    val onQualityChange: (Int) -> Unit =
        if (isSpkMode) viewModel::setSpkVheQuality else viewModel::setVheQuality

    EffectSection(
        title = stringResource(R.string.section_headphone_surround),
        enabled = enabled,
        onEnabledChange = onEnabledChange,
        icon = Icons.Default.Headphones
    ) {
        LabeledSlider(
            label = stringResource(R.string.label_vhe_quality),
            value = quality.toFloat(),
            onValueChange = { onQualityChange(it.roundToInt()) },
            valueRange = 0f..4f,
            steps = 3
        )
    }
}

@Composable
fun ReverberationSection(state: MainUiState, viewModel: MainViewModel, isSpkMode: Boolean = false) {
    val enabled = if (isSpkMode) state.spkReverbEnabled else state.reverbEnabled
    val roomSize = if (isSpkMode) state.spkReverbRoomSize else state.reverbRoomSize
    val width = if (isSpkMode) state.spkReverbWidth else state.reverbWidth
    val dampening = if (isSpkMode) state.spkReverbDampening else state.reverbDampening
    val wet = if (isSpkMode) state.spkReverbWet else state.reverbWet
    val dry = if (isSpkMode) state.spkReverbDry else state.reverbDry
    val onEnabledChange: (Boolean) -> Unit =
        if (isSpkMode) viewModel::setSpkReverbEnabled else viewModel::setReverbEnabled
    val onRoomSizeChange: (Int) -> Unit =
        if (isSpkMode) viewModel::setSpkReverbRoomSize else viewModel::setReverbRoomSize
    val onWidthChange: (Int) -> Unit =
        if (isSpkMode) viewModel::setSpkReverbWidth else viewModel::setReverbWidth
    val onDampeningChange: (Int) -> Unit =
        if (isSpkMode) viewModel::setSpkReverbDampening else viewModel::setReverbDampening
    val onWetChange: (Int) -> Unit =
        if (isSpkMode) viewModel::setSpkReverbWet else viewModel::setReverbWet
    val onDryChange: (Int) -> Unit =
        if (isSpkMode) viewModel::setSpkReverbDry else viewModel::setReverbDry

    EffectSection(
        title = stringResource(R.string.section_reverberation),
        enabled = enabled,
        onEnabledChange = onEnabledChange,
        icon = Icons.Default.BlurOn
    ) {
        LabeledSlider(
            label = stringResource(R.string.label_reverb_room_size),
            value = roomSize.toFloat(),
            onValueChange = { onRoomSizeChange(it.roundToInt()) },
            valueRange = 0f..10f,
            steps = 9
        )
        LabeledSlider(
            label = stringResource(R.string.label_reverb_width),
            value = width.toFloat(),
            onValueChange = { onWidthChange(it.roundToInt()) },
            valueRange = 0f..10f,
            steps = 9
        )
        LabeledSlider(
            label = stringResource(R.string.label_reverb_dampening),
            value = dampening.toFloat(),
            onValueChange = { onDampeningChange(it.roundToInt()) },
            valueRange = 0f..10f,
            steps = 9
        )
        LabeledSlider(
            label = stringResource(R.string.label_reverb_wet),
            value = wet.toFloat(),
            onValueChange = { onWetChange(it.roundToInt()) },
            valueRange = 0f..100f
        )
        LabeledSlider(
            label = stringResource(R.string.label_reverb_dry),
            value = dry.toFloat(),
            onValueChange = { onDryChange(it.roundToInt()) },
            valueRange = 0f..100f
        )
    }
}

@Composable
fun DynamicSystemSection(state: MainUiState, viewModel: MainViewModel, isSpkMode: Boolean = false) {
    val enabled = if (isSpkMode) state.spkDynamicSystemEnabled else state.dynamicSystemEnabled
    val strength = if (isSpkMode) state.spkDynamicSystemStrength else state.dynamicSystemStrength
    val dsPresetId = if (isSpkMode) state.spkDsPresetId else state.dsPresetId
    val dsPresets = if (isSpkMode) state.spkDsPresets else state.dsPresets
    val xLow = if (isSpkMode) state.spkDsXLow else state.dsXLow
    val xHigh = if (isSpkMode) state.spkDsXHigh else state.dsXHigh
    val yLow = if (isSpkMode) state.spkDsYLow else state.dsYLow
    val yHigh = if (isSpkMode) state.spkDsYHigh else state.dsYHigh
    val sideGainLow = if (isSpkMode) state.spkDsSideGainLow else state.dsSideGainLow
    val sideGainHigh = if (isSpkMode) state.spkDsSideGainHigh else state.dsSideGainHigh
    val onEnabledChange: (Boolean) -> Unit =
        if (isSpkMode) viewModel::setSpkDynamicSystemEnabled else viewModel::setDynamicSystemEnabled
    val onStrengthChange: (Int) -> Unit =
        if (isSpkMode) viewModel::setSpkDynamicSystemStrength else viewModel::setDynamicSystemStrength
    val onPresetSelect: (Long) -> Unit =
        if (isSpkMode) viewModel::setSpkDsPreset else viewModel::setDsPreset
    val onXLowChange: (Int) -> Unit =
        if (isSpkMode) viewModel::setSpkDsXLow else viewModel::setDsXLow
    val onXHighChange: (Int) -> Unit =
        if (isSpkMode) viewModel::setSpkDsXHigh else viewModel::setDsXHigh
    val onYLowChange: (Int) -> Unit =
        if (isSpkMode) viewModel::setSpkDsYLow else viewModel::setDsYLow
    val onYHighChange: (Int) -> Unit =
        if (isSpkMode) viewModel::setSpkDsYHigh else viewModel::setDsYHigh
    val onSideGainLowChange: (Int) -> Unit =
        if (isSpkMode) viewModel::setSpkDsSideGainLow else viewModel::setDsSideGainLow
    val onSideGainHighChange: (Int) -> Unit =
        if (isSpkMode) viewModel::setSpkDsSideGainHigh else viewModel::setDsSideGainHigh
    val onPresetAdd: (String) -> Unit =
        if (isSpkMode) viewModel::addSpkDsPreset else viewModel::addDsPreset
    val onPresetDelete: (Long) -> Unit =
        if (isSpkMode) viewModel::deleteSpkDsPreset else viewModel::deleteDsPreset
    val onReset: () -> Unit =
        if (isSpkMode) viewModel::resetSpkDsCoefficients else viewModel::resetDsCoefficients

    var showSaveDialog by remember { mutableStateOf(false) }
    var presetNameInput by remember { mutableStateOf("") }

    EffectSection(
        title = stringResource(R.string.section_dynamic_system),
        enabled = enabled,
        onEnabledChange = onEnabledChange,
        icon = Icons.Default.Speaker
    ) {
        val presetName = dsPresets.find { it.id == dsPresetId }?.name ?: "Custom"
        LabeledDropdown(
            label = stringResource(R.string.label_ds_preset),
            selectedValue = presetName,
            options = dsPresets.map { it.name },
            onOptionSelected = { index, _ -> onPresetSelect(dsPresets[index].id) }
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TextButton(onClick = { showSaveDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(stringResource(R.string.action_save))
            }
            TextButton(
                onClick = { dsPresetId?.let { onPresetDelete(it) } },
                enabled = dsPresetId != null
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(stringResource(R.string.action_delete))
            }
            TextButton(onClick = onReset) {
                Icon(
                    Icons.Default.RestartAlt,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(stringResource(R.string.action_reset))
            }
        }

        LabeledSlider(
            label = stringResource(R.string.label_ds_strength),
            value = strength.toFloat(),
            onValueChange = { onStrengthChange(it.roundToInt()) },
            valueRange = 0f..100f,
            valueLabel = "${strength}%"
        )

        LabeledSlider(
            label = stringResource(R.string.label_ds_x_low_freq),
            value = xLow.toFloat(),
            onValueChange = { onXLowChange((it / 100f).roundToInt() * 100) },
            valueRange = 0f..2400f,
            steps = 23,
            valueLabel = "$xLow Hz"
        )

        LabeledSlider(
            label = stringResource(R.string.label_ds_x_high_freq),
            value = xHigh.toFloat(),
            onValueChange = { onXHighChange((it / 100f).roundToInt() * 100) },
            valueRange = 0f..12000f,
            steps = 119,
            valueLabel = "$xHigh Hz"
        )

        LabeledSlider(
            label = stringResource(R.string.label_ds_y_low_freq),
            value = yLow.toFloat(),
            onValueChange = { onYLowChange(it.roundToInt()) },
            valueRange = 0f..200f,
            valueLabel = "$yLow Hz"
        )

        LabeledSlider(
            label = stringResource(R.string.label_ds_y_high_freq),
            value = yHigh.toFloat(),
            onValueChange = { onYHighChange(it.roundToInt()) },
            valueRange = 0f..300f,
            valueLabel = "$yHigh Hz"
        )

        LabeledSlider(
            label = stringResource(R.string.label_ds_side_gain_low),
            value = sideGainLow.toFloat(),
            onValueChange = { onSideGainLowChange(it.roundToInt()) },
            valueRange = 0f..100f,
            valueLabel = "${sideGainLow}%"
        )

        LabeledSlider(
            label = stringResource(R.string.label_ds_side_gain_high),
            value = sideGainHigh.toFloat(),
            onValueChange = { onSideGainHighChange(it.roundToInt()) },
            valueRange = 0f..100f,
            valueLabel = "${sideGainHigh}%"
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
                    modifier = Modifier.fillMaxWidth()
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
                    }
                ) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveDialog = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }
}

@Composable
fun TubeSimulatorSection(state: MainUiState, viewModel: MainViewModel, isSpkMode: Boolean = false) {
    val enabled = if (isSpkMode) state.spkTubeSimulatorEnabled else state.tubeSimulatorEnabled
    val onEnabledChange: (Boolean) -> Unit =
        if (isSpkMode) viewModel::setSpkTubeSimulatorEnabled else viewModel::setTubeSimulatorEnabled

    EffectSection(
        title = stringResource(R.string.section_tube_simulator),
        enabled = enabled,
        onEnabledChange = onEnabledChange,
        icon = Icons.Default.MusicNote,
        toggleOnly = true
    ) {}
}

@Composable
fun ViperBassSection(state: MainUiState, viewModel: MainViewModel, isSpkMode: Boolean = false) {
    val bassEnabled = if (isSpkMode) state.spkBassEnabled else state.bassEnabled
    val bassMode = if (isSpkMode) state.spkBassMode else state.bassMode
    val bassFrequency = if (isSpkMode) state.spkBassFrequency else state.bassFrequency
    val bassGain = if (isSpkMode) state.spkBassGain else state.bassGain
    val onEnabledChange: (Boolean) -> Unit =
        if (isSpkMode) viewModel::setSpkBassEnabled else viewModel::setBassEnabled
    val onModeChange: (Int) -> Unit =
        if (isSpkMode) viewModel::setSpkBassMode else viewModel::setBassMode
    val onFrequencyChange: (Int) -> Unit =
        if (isSpkMode) viewModel::setSpkBassFrequency else viewModel::setBassFrequency
    val onGainChange: (Int) -> Unit =
        if (isSpkMode) viewModel::setSpkBassGain else viewModel::setBassGain

    val modeNames = listOf(
        stringResource(R.string.bass_mode_natural),
        stringResource(R.string.bass_mode_pure),
        stringResource(R.string.bass_mode_subwoofer)
    )

    EffectSection(
        title = stringResource(R.string.section_viper_bass),
        enabled = bassEnabled,
        onEnabledChange = onEnabledChange,
        icon = Icons.Default.GraphicEq
    ) {
        LabeledDropdown(
            label = stringResource(R.string.label_bass_mode),
            selectedValue = modeNames.getOrElse(bassMode) { modeNames[0] },
            options = modeNames,
            onOptionSelected = { index, _ -> onModeChange(index) }
        )
        LabeledSlider(
            label = stringResource(R.string.label_bass_frequency),
            value = bassFrequency.toFloat(),
            onValueChange = { onFrequencyChange(it.roundToInt()) },
            valueRange = 0f..135f,
            valueLabel = "${bassFrequency + 15}Hz"
        )
        LabeledSlider(
            label = stringResource(R.string.label_bass_gain),
            value = bassGain.toFloat(),
            onValueChange = { onGainChange(it.roundToInt()) },
            valueRange = 0f..11f,
            steps = 10,
            valueLabel = "${
                MainViewModel.BASS_GAIN_DB_LABELS.getOrElse(bassGain) {
                    "%.1f".format(
                        bassGain * 0.5
                    )
                }
            }dB"
        )
    }
}

@Composable
fun ViperBassMonoSection(state: MainUiState, viewModel: MainViewModel, isSpkMode: Boolean = false) {
    val enabled = if (isSpkMode) state.spkBassMonoEnabled else state.bassMonoEnabled
    val mode = if (isSpkMode) state.spkBassMonoMode else state.bassMonoMode
    val frequency = if (isSpkMode) state.spkBassMonoFrequency else state.bassMonoFrequency
    val gain = if (isSpkMode) state.spkBassMonoGain else state.bassMonoGain
    val onEnabledChange: (Boolean) -> Unit =
        if (isSpkMode) viewModel::setSpkBassMonoEnabled else viewModel::setBassMonoEnabled
    val onModeChange: (Int) -> Unit =
        if (isSpkMode) viewModel::setSpkBassMonoMode else viewModel::setBassMonoMode
    val onFrequencyChange: (Int) -> Unit =
        if (isSpkMode) viewModel::setSpkBassMonoFrequency else viewModel::setBassMonoFrequency
    val onGainChange: (Int) -> Unit =
        if (isSpkMode) viewModel::setSpkBassMonoGain else viewModel::setBassMonoGain

    val modeNames = listOf(
        stringResource(R.string.bass_mode_natural),
        stringResource(R.string.bass_mode_pure),
        stringResource(R.string.bass_mode_subwoofer)
    )

    EffectSection(
        title = stringResource(R.string.section_viper_bass_mono),
        enabled = enabled,
        onEnabledChange = onEnabledChange,
        icon = Icons.Default.GraphicEq
    ) {
        LabeledDropdown(
            label = stringResource(R.string.label_bass_mode),
            selectedValue = modeNames.getOrElse(mode) { modeNames[0] },
            options = modeNames,
            onOptionSelected = { index, _ -> onModeChange(index) }
        )
        LabeledSlider(
            label = stringResource(R.string.label_bass_frequency),
            value = frequency.toFloat(),
            onValueChange = { onFrequencyChange(it.roundToInt()) },
            valueRange = 0f..135f,
            valueLabel = "${frequency + 15}Hz"
        )
        LabeledSlider(
            label = stringResource(R.string.label_bass_gain),
            value = gain.toFloat(),
            onValueChange = { onGainChange(it.roundToInt()) },
            valueRange = 50f..1000f,
            steps = 94,
            valueLabel = "$gain"
        )
    }
}

@Composable
fun ViperClaritySection(state: MainUiState, viewModel: MainViewModel, isSpkMode: Boolean = false) {
    val clarityEnabled = if (isSpkMode) state.spkClarityEnabled else state.clarityEnabled
    val clarityMode = if (isSpkMode) state.spkClarityMode else state.clarityMode
    val clarityGain = if (isSpkMode) state.spkClarityGain else state.clarityGain
    val onEnabledChange: (Boolean) -> Unit =
        if (isSpkMode) viewModel::setSpkClarityEnabled else viewModel::setClarityEnabled
    val onModeChange: (Int) -> Unit =
        if (isSpkMode) viewModel::setSpkClarityMode else viewModel::setClarityMode
    val onGainChange: (Int) -> Unit =
        if (isSpkMode) viewModel::setSpkClarityGain else viewModel::setClarityGain

    val modeNames = listOf(
        stringResource(R.string.clarity_mode_natural),
        stringResource(R.string.clarity_mode_ozone),
        stringResource(R.string.clarity_mode_xhifi)
    )

    EffectSection(
        title = stringResource(R.string.section_viper_clarity),
        enabled = clarityEnabled,
        onEnabledChange = onEnabledChange,
        icon = Icons.Default.Hearing
    ) {
        LabeledDropdown(
            label = stringResource(R.string.label_clarity_mode),
            selectedValue = modeNames.getOrElse(clarityMode) { modeNames[0] },
            options = modeNames,
            onOptionSelected = { index, _ -> onModeChange(index) }
        )
        LabeledSlider(
            label = stringResource(R.string.label_clarity_gain),
            value = clarityGain.toFloat(),
            onValueChange = { onGainChange(it.roundToInt()) },
            valueRange = 0f..9f,
            steps = 8,
            valueLabel = "${
                MainViewModel.CLARITY_GAIN_DB_LABELS.getOrElse(clarityGain) {
                    "%.1f".format(
                        clarityGain * 0.5
                    )
                }
            }dB"
        )
    }
}

@Composable
fun AuditoryProtectionSection(
    state: MainUiState,
    viewModel: MainViewModel,
    isSpkMode: Boolean = false
) {
    val cureEnabled = if (isSpkMode) state.spkCureEnabled else state.cureEnabled
    val cureStrength = if (isSpkMode) state.spkCureStrength else state.cureStrength
    val onEnabledChange: (Boolean) -> Unit =
        if (isSpkMode) viewModel::setSpkCureEnabled else viewModel::setCureEnabled
    val onStrengthChange: (Int) -> Unit =
        if (isSpkMode) viewModel::setSpkCureStrength else viewModel::setCureStrength

    val strengthNames = listOf(
        stringResource(R.string.cure_level_mild),
        stringResource(R.string.cure_level_medium),
        stringResource(R.string.cure_level_strong)
    )

    EffectSection(
        title = stringResource(R.string.section_cure),
        enabled = cureEnabled,
        onEnabledChange = onEnabledChange,
        icon = Icons.Default.HealthAndSafety
    ) {
        LabeledDropdown(
            label = stringResource(R.string.label_cure_strength),
            selectedValue = strengthNames.getOrElse(cureStrength) { strengthNames[0] },
            options = strengthNames,
            onOptionSelected = { index, _ -> onStrengthChange(index) }
        )
    }
}

@Composable
fun AnalogXSection(state: MainUiState, viewModel: MainViewModel, isSpkMode: Boolean = false) {
    val analogxEnabled = if (isSpkMode) state.spkAnalogxEnabled else state.analogxEnabled
    val analogxMode = if (isSpkMode) state.spkAnalogxMode else state.analogxMode
    val onEnabledChange: (Boolean) -> Unit =
        if (isSpkMode) viewModel::setSpkAnalogxEnabled else viewModel::setAnalogxEnabled
    val onModeChange: (Int) -> Unit =
        if (isSpkMode) viewModel::setSpkAnalogxMode else viewModel::setAnalogxMode

    val modeNames = listOf(
        stringResource(R.string.analogx_mode_mild),
        stringResource(R.string.analogx_mode_medium),
        stringResource(R.string.analogx_mode_strong)
    )

    EffectSection(
        title = stringResource(R.string.section_analogx),
        enabled = analogxEnabled,
        onEnabledChange = onEnabledChange,
        icon = Icons.Default.Memory
    ) {
        LabeledDropdown(
            label = stringResource(R.string.label_analogx_mode),
            selectedValue = modeNames.getOrElse(analogxMode) { modeNames[0] },
            options = modeNames,
            onOptionSelected = { index, _ -> onModeChange(index) }
        )
    }
}

@Composable
fun SpeakerOptSection(state: MainUiState, viewModel: MainViewModel) {
    EffectSection(
        title = stringResource(R.string.section_speaker_optimization),
        enabled = state.speakerOptEnabled,
        onEnabledChange = viewModel::setSpeakerOptEnabled,
        icon = Icons.Default.Speaker,
        toggleOnly = true
    ) {}
}
