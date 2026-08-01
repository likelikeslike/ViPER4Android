package com.llsl.viper4android.effect

import com.llsl.viper4android.data.model.DsPreset
import com.llsl.viper4android.data.model.EqPreset

data class OutputState(
    val volume: Int = 100,
    val channelPan: Int = 0,
    val limiter: Int = 100,
)

data class PlaybackGainControlState(
    val enable: Boolean = false,
    val strength: Int = 100,
    val maxGain: Int = 100,
    val outputThreshold: Int = 100,
)

data class LufsState(
    val enable: Boolean = false,
    val target: Int = 140,
    val maxGain: Int = 60,
    val speed: Int = 1,
)

data class FetCompressorState(
    val enable: Boolean = false,
    val threshold: Int = 100,
    val ratio: Int = 100,
    val kneeAuto: Boolean = true,
    val knee: Int = 0,
    val kneeMulti: Int = 0,
    val gainAuto: Boolean = true,
    val gain: Int = 0,
    val attackAuto: Boolean = true,
    val attack: Int = 20,
    val maxAttack: Int = 80,
    val releaseAuto: Boolean = true,
    val release: Int = 50,
    val maxRelease: Int = 100,
    val crest: Int = 100,
    val adapt: Int = 50,
    val noClip: Boolean = true,
)

data class MultibandCompressorState(
    val enable: Boolean = false,
    val bandEnables: List<Boolean> = listOf(true, true, true, true, true),
    val crossovers: List<Int> = listOf(120, 500, 4000, 8000),
    val thresholds: List<Int> = listOf(-18, -18, -18, -18, -18),
    val ratios: List<Int> = listOf(50, 50, 50, 50, 50),
    val gains: List<Int> = listOf(0, 0, 0, 0, 0),
    val knees: List<Int> = listOf(0, 0, 0, 0, 0),
    val kneeMultis: List<Int> = listOf(0, 0, 0, 0, 0),
    val attacks: List<Int> = listOf(1, 1, 1, 1, 1),
    val maxAttacks: List<Int> = listOf(44, 44, 44, 44, 44),
    val releases: List<Int> = listOf(100, 100, 100, 100, 100),
    val maxReleases: List<Int> = listOf(200, 200, 200, 200, 200),
    val crests: List<Int> = listOf(100, 100, 100, 100, 100),
    val adapts: List<Int> = listOf(50, 50, 50, 50, 50),
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
    val exciter: Int = 0,
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
    val qs: List<Int> = listOf(100, 100, 150),
    val gains: List<Int> = listOf(0, 0, 0),
    val thresholds: List<Int> = listOf(-200, -200, -200),
    val attacks: List<Int> = listOf(10, 10, 10),
    val releases: List<Int> = listOf(100, 100, 100),
    val filterTypes: List<Int> = listOf(0, 0, 0),
)

data class ConvolverState(
    val enable: Boolean = false,
    val kernelFile: String = "",
    val crossChannel: Int = 0,
)

data class FieldSurroundState(
    val enable: Boolean = false,
    val widening: Int = 0,
    val midImage: Int = 5,
    val depth: Int = 0,
)

data class DiffSurroundState(
    val enable: Boolean = false,
    val delay: Int = 5,
    val reverse: Boolean = false,
    val wetDryMix: Int = 100,
    val lpCutoff: Int = 0,
)

data class StereoImagerState(
    val enable: Boolean = false,
    val lowWidth: Int = 100,
    val midWidth: Int = 100,
    val highWidth: Int = 100,
    val lowCrossover: Int = 200,
    val highCrossover: Int = 4000,
)

data class HeadphoneSurroundState(
    val enable: Boolean = false,
    val quality: Int = 0,
)

data class ReverbState(
    val enable: Boolean = false,
    val roomSize: Int = 0,
    val width: Int = 0,
    val damp: Int = 50,
    val wet: Int = 0,
    val dry: Int = 100,
)

data class DynamicSystemState(
    val enable: Boolean = false,
    val xLow: Int = 0,
    val xHigh: Int = 0,
    val yLow: Int = 0,
    val yHigh: Int = 0,
    val sideGainLow: Int = 0,
    val sideGainHigh: Int = 0,
    val strength: Int = 0,
    val device: Int = 0,
    val presetId: Long? = null,
    val presets: List<DsPreset> = emptyList(),
)

data class PsychoacousticBassState(
    val enable: Boolean = false,
    val cutoff: Int = 80,
    val intensity: Int = 50,
    val harmonicOrder: Int = 3,
    val originalLevel: Int = 100,
)

data class BassState(
    val enable: Boolean = false,
    val mode: Int = 0,
    val frequency: Int = 60,
    val gain: Int = 0,
    val antiPop: Boolean = false,
)

data class BassMonoState(
    val enable: Boolean = false,
    val mode: Int = 0,
    val frequency: Int = 60,
    val gain: Int = 0,
    val antiPop: Boolean = false,
)

data class ClarityState(
    val enable: Boolean = false,
    val mode: Int = 0,
    val gain: Int = 0,
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
