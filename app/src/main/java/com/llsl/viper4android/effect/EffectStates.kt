package com.llsl.viper4android.effect

import com.llsl.viper4android.data.model.DsPreset
import com.llsl.viper4android.data.model.EqPreset

data class OutputState(
    val volume: Float = 1.0f,
    val channelPan: Float = 0.0f,
    val limiter: Float = 1.0f,
)

data class PlaybackGainControlState(
    val enable: Boolean = false,
    val strength: Float = 1.0f,
    val maxGain: Float = 1.0f,
    val outputThreshold: Float = 1.0f,
)

data class LufsState(
    val enable: Boolean = false,
    val target: Float = -14.0f,
    val maxGain: Float = 6.0f,
    val speed: Int = 1,
)

data class FetCompressorState(
    val enable: Boolean = false,
    val threshold: Float = compressorDbToRaw(-18.0f),
    val ratio: Float = compressorRatioToRaw(1.0f),
    val kneeAuto: Boolean = true,
    val knee: Float = 0.0f,
    val kneeMulti: Float = 0.0f,
    val gainAuto: Boolean = true,
    val gain: Float = 0.0f,
    val attackAuto: Boolean = true,
    val attack: Float = compressorMsToSeconds(20.0f),
    val maxAttack: Float = compressorMsToSeconds(80.0f),
    val releaseAuto: Boolean = true,
    val release: Float = compressorMsToSeconds(50.0f),
    val maxRelease: Float = compressorMsToSeconds(100.0f),
    val crest: Float = compressorMsToSeconds(100.0f),
    val adapt: Float = compressorAdaptAmountToSeconds(0.5f),
    val noClip: Boolean = true,
)

data class MultibandCompressorState(
    val enable: Boolean = false,
    val bandEnables: List<Boolean> = listOf(true, true, true, true, true),
    val crossovers: List<Int> = listOf(120, 500, 4000, 8000),
    val thresholds: List<Float> = List(5) { compressorDbToRaw(-18.0f) },
    val ratios: List<Float> = List(5) { compressorRatioToRaw(0.5f) },
    val gains: List<Float> = listOf(0.0f, 0.0f, 0.0f, 0.0f, 0.0f),
    val knees: List<Float> = listOf(0.0f, 0.0f, 0.0f, 0.0f, 0.0f),
    val kneeMultis: List<Float> = listOf(0.0f, 0.0f, 0.0f, 0.0f, 0.0f),
    val attacks: List<Float> = List(5) { compressorMsToSeconds(1.0f) },
    val maxAttacks: List<Float> = List(5) { compressorMsToSeconds(44.0f) },
    val releases: List<Float> = List(5) { compressorMsToSeconds(100.0f) },
    val maxReleases: List<Float> = List(5) { compressorMsToSeconds(200.0f) },
    val crests: List<Float> = List(5) { compressorMsToSeconds(100.0f) },
    val adapts: List<Float> = List(5) { compressorAdaptAmountToSeconds(0.5f) },
    val kneeAutos: List<Boolean> = listOf(true, true, true, true, true),
    val gainAutos: List<Boolean> = listOf(true, true, true, true, true),
    val attackAutos: List<Boolean> = listOf(true, true, true, true, true),
    val releaseAutos: List<Boolean> = listOf(true, true, true, true, true),
    val noClips: List<Boolean> = listOf(true, true, true, true, true),
)

data class DdcState(
    val enable: Boolean = false,
    val device: String = "",
)

data class SpectrumExtensionState(
    val enable: Boolean = false,
    val strength: Int = 7600,
    val exciter: Float = 0.0f,
)

data class EqState(
    val enable: Boolean = false,
    val bandCount: Int = 10,
    val presetId: Long? = null,
    val bands: List<Double> = List(10) { 0.0 },
    val bandsMap: Map<Int, List<Double>> = mapOf(10 to List(10) { 0.0 }),
    val presets: List<EqPreset> = emptyList(),
)

data class DynamicEqState(
    val enable: Boolean = false,
    val bandCount: Int = 3,
    val freqs: List<Int> = listOf(60, 150, 400),
    val qs: List<Float> = listOf(1.0f, 1.0f, 1.5f),
    val gains: List<Float> = listOf(0.0f, 0.0f, 0.0f),
    val thresholds: List<Float> = listOf(-20.0f, -20.0f, -20.0f),
    val attacks: List<Float> = listOf(10.0f, 10.0f, 10.0f),
    val releases: List<Float> = listOf(100.0f, 100.0f, 100.0f),
    val filterTypes: List<Int> = listOf(0, 0, 0),
)

data class ConvolverState(
    val enable: Boolean = false,
    val kernelFile: String = "",
    val crossChannel: Float = 0.0f,
)

data class FieldSurroundState(
    val enable: Boolean = false,
    val widening: Float = 0.0f,
    val midImage: Float = 1.5f,
    val depth: Int = 200,
)

data class DiffSurroundState(
    val enable: Boolean = false,
    val delay: Float = 5.0f,
    val reverse: Boolean = false,
    val wetDryMix: Float = 1.0f,
    val lpCutoff: Int = 0,
)

data class StereoImagerState(
    val enable: Boolean = false,
    val lowWidth: Float = 1.0f,
    val midWidth: Float = 1.0f,
    val highWidth: Float = 1.0f,
    val lowCrossover: Int = 200,
    val highCrossover: Int = 4000,
)

data class HeadphoneSurroundState(
    val enable: Boolean = false,
    val quality: Int = 0,
)

data class ReverbState(
    val enable: Boolean = false,
    val roomSize: Float = 0.0f,
    val width: Float = 0.0f,
    val damp: Float = 0.5f,
    val wet: Float = 0.0f,
    val dry: Float = 1.0f,
)

data class DynamicSystemState(
    val enable: Boolean = false,
    val xLow: Int = 0,
    val xHigh: Int = 0,
    val yLow: Int = 0,
    val yHigh: Int = 0,
    val sideGainLow: Float = 0.5f,
    val sideGainHigh: Float = 0.5f,
    val strength: Float = 0.5f,
    val device: Int = 0,
    val presetId: Long? = null,
    val presets: List<DsPreset> = emptyList(),
)

data class PsychoacousticBassState(
    val enable: Boolean = false,
    val cutoff: Int = 80,
    val intensity: Float = 0.5f,
    val harmonicOrder: Int = 3,
    val originalLevel: Float = 1.0f,
)

data class BassState(
    val enable: Boolean = false,
    val mode: Int = 0,
    val frequency: Int = 60,
    val gain: Float = 0.5f,
    val antiPop: Boolean = false,
)

data class BassMonoState(
    val enable: Boolean = false,
    val mode: Int = 0,
    val frequency: Int = 60,
    val gain: Float = 0.5f,
    val antiPop: Boolean = false,
)

data class ClarityState(
    val enable: Boolean = false,
    val mode: Int = 0,
    val gain: Float = 0.5f,
)

data class CureState(
    val enable: Boolean = false,
    val crossfeedPreset: Int = 0,
)

data class AnalogXState(
    val enable: Boolean = false,
    val mode: Int = 0,
)

data class TubeSimulatorState(
    val enable: Boolean = false,
    val model: Int = 0,
    val drive: Float = 0.5f,
    val mix: Float = 0.3f,
)

data class SpeakerCorrectionState(
    val enable: Boolean = false,
)

data class EffectState(
    val masterEnable: Boolean = false,
    val out: OutputState = OutputState(),
    val playbackGainControl: PlaybackGainControlState = PlaybackGainControlState(),
    val lufs: LufsState = LufsState(),
    val fetCompressor: FetCompressorState = FetCompressorState(),
    val multibandCompressor: MultibandCompressorState = MultibandCompressorState(),
    val ddc: DdcState = DdcState(),
    val spectrumExtension: SpectrumExtensionState = SpectrumExtensionState(),
    val eq: EqState = EqState(),
    val dynamicEq: DynamicEqState = DynamicEqState(),
    val convolver: ConvolverState = ConvolverState(),
    val fieldSurround: FieldSurroundState = FieldSurroundState(),
    val diffSurround: DiffSurroundState = DiffSurroundState(),
    val stereoImager: StereoImagerState = StereoImagerState(),
    val headphoneSurround: HeadphoneSurroundState = HeadphoneSurroundState(),
    val reverb: ReverbState = ReverbState(),
    val dynamicSystem: DynamicSystemState = DynamicSystemState(),
    val psychoacousticBass: PsychoacousticBassState = PsychoacousticBassState(),
    val bass: BassState = BassState(),
    val bassMono: BassMonoState = BassMonoState(),
    val clarity: ClarityState = ClarityState(),
    val cure: CureState = CureState(),
    val analogX: AnalogXState = AnalogXState(),
    val tubeSimulator: TubeSimulatorState = TubeSimulatorState(),
    val speakerCorrection: SpeakerCorrectionState = SpeakerCorrectionState(),
    val activeDeviceName: String = "",
    val activeDeviceId: String = "",
)
