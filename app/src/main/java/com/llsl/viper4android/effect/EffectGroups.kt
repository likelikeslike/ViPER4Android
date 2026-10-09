package com.llsl.viper4android.effect

import com.llsl.viper4android.data.repository.ViperRepository
import com.llsl.viper4android.viper.ViperParams

data class EffectGroup(
    val effectKey: String,
    val prefs: List<EffectPref<*>>,
)

abstract class EffectGroupBuilder(
    val effectKey: String,
) {
    private val prefList = mutableListOf<EffectPref<*>>()

    protected fun int(
        paramId: Int,
        jsonKey: String,
        default: Int,
        get: (EffectState) -> Int,
        set: EffectState.(Int) -> EffectState,
        range: IntRange? = null,
    ): IntPref {
        val pref = IntPref(effectKey, paramId, jsonKey, default, get, set, range)
        prefList += pref
        return pref
    }

    protected fun float(
        paramId: Int,
        jsonKey: String,
        default: Float,
        get: (EffectState) -> Float,
        set: EffectState.(Float) -> EffectState,
        range: ClosedFloatingPointRange<Float>? = null,
    ): FloatPref {
        val pref = FloatPref(effectKey, paramId, jsonKey, default, get, set, range)
        prefList += pref
        return pref
    }

    protected fun bool(
        paramId: Int,
        jsonKey: String,
        default: Boolean,
        get: (EffectState) -> Boolean,
        set: EffectState.(Boolean) -> EffectState,
    ): BoolPref {
        val pref = BoolPref(effectKey, paramId, jsonKey, default, get, set)
        prefList += pref
        return pref
    }

    protected fun string(
        paramId: Int,
        jsonKey: String,
        default: String,
        get: (EffectState) -> String,
        set: EffectState.(String) -> EffectState,
    ): StringPref {
        val pref = StringPref(effectKey, paramId, jsonKey, default, get, set)
        prefList += pref
        return pref
    }

    protected fun nullableLong(
        jsonKey: String,
        get: (EffectState) -> Long?,
        set: EffectState.(Long?) -> EffectState,
    ): NullableLongPref {
        val pref = NullableLongPref(effectKey, jsonKey, get, set)
        prefList += pref
        return pref
    }

    protected fun intList(
        paramId: Int,
        jsonKey: String,
        default: List<Int>,
        get: (EffectState) -> List<Int>,
        set: EffectState.(List<Int>) -> EffectState,
        range: IntRange? = null,
    ): IntListPref {
        val pref = IntListPref(effectKey, paramId, jsonKey, default, get, set, range)
        prefList += pref
        return pref
    }

    protected fun floatList(
        paramId: Int,
        jsonKey: String,
        default: List<Float>,
        get: (EffectState) -> List<Float>,
        set: EffectState.(List<Float>) -> EffectState,
        range: ClosedFloatingPointRange<Float>? = null,
    ): FloatListPref {
        val pref = FloatListPref(effectKey, paramId, jsonKey, default, get, set, range)
        prefList += pref
        return pref
    }

    protected fun boolList(
        paramId: Int,
        jsonKey: String,
        default: List<Boolean>,
        get: (EffectState) -> List<Boolean>,
        set: EffectState.(List<Boolean>) -> EffectState,
    ): BoolListPref {
        val pref = BoolListPref(effectKey, paramId, jsonKey, default, get, set)
        prefList += pref
        return pref
    }

    protected fun doubleList(
        paramId: Int,
        jsonKey: String,
        default: List<Double>,
        get: (EffectState) -> List<Double>,
        set: EffectState.(List<Double>) -> EffectState,
        range: ClosedFloatingPointRange<Double>? = null,
    ): DoubleListPref {
        val pref = DoubleListPref(effectKey, paramId, jsonKey, default, get, set, range)
        prefList += pref
        return pref
    }

    fun toGroup(): EffectGroup = EffectGroup(effectKey, prefList.toList())
}

class MasterLimiterEffect : EffectGroupBuilder("masterLimiter") {
    val threshold =
        float(
            ViperParams.PARAM_MASTER_LIMITER_THRESHOLD,
            "threshold",
            1.0f,
            { it.out.limiter },
            { copy(out = out.copy(limiter = it)) },
            range = 0.3f..1.0f,
        )
    val outputVolume =
        float(
            ViperParams.PARAM_MASTER_LIMITER_OUTPUT_VOLUME,
            "outputVolume",
            1.0f,
            { it.out.volume },
            { copy(out = out.copy(volume = it)) },
            range = 0.01f..2.0f,
        )
    val channelPan =
        float(
            ViperParams.PARAM_MASTER_LIMITER_CHANNEL_PAN,
            "channelPan",
            0.0f,
            { it.out.channelPan },
            { copy(out = out.copy(channelPan = it)) },
            range = -1.0f..1.0f,
        )
}

class PlaybackGainControlEffect : EffectGroupBuilder("playbackGainControl") {
    val enable =
        bool(
            ViperParams.PARAM_PLAYBACK_GAIN_CONTROL_ENABLE,
            "enable",
            false,
            { it.playbackGainControl.enable },
            { copy(playbackGainControl = playbackGainControl.copy(enable = it)) },
        )
    val strength =
        float(
            ViperParams.PARAM_PLAYBACK_GAIN_CONTROL_STRENGTH,
            "strength",
            1.0f,
            { it.playbackGainControl.strength },
            { copy(playbackGainControl = playbackGainControl.copy(strength = it)) },
            range = 0.5f..3.0f,
        )
    val maxGain =
        float(
            ViperParams.PARAM_PLAYBACK_GAIN_CONTROL_MAX_GAIN,
            "maxGain",
            1.0f,
            { it.playbackGainControl.maxGain },
            { copy(playbackGainControl = playbackGainControl.copy(maxGain = it)) },
            range = 1.0f..10.0f,
        )
    val outputThreshold =
        float(
            ViperParams.PARAM_PLAYBACK_GAIN_CONTROL_OUTPUT_THRESHOLD,
            "outputThreshold",
            1.0f,
            { it.playbackGainControl.outputThreshold },
            { copy(playbackGainControl = playbackGainControl.copy(outputThreshold = it)) },
            range = 0.3f..1.0f,
        )
}

class LufsEffect : EffectGroupBuilder("lufs") {
    val enable =
        bool(
            ViperParams.PARAM_LUFS_ENABLE,
            "enable",
            false,
            { it.lufs.enable },
            { copy(lufs = lufs.copy(enable = it)) },
        )
    val target =
        float(
            ViperParams.PARAM_LUFS_TARGET,
            "target",
            -14.0f,
            { it.lufs.target },
            { copy(lufs = lufs.copy(target = it)) },
            range = -24.0f..-8.0f,
        )
    val maxGain =
        float(
            ViperParams.PARAM_LUFS_MAX_GAIN,
            "maxGain",
            6.0f,
            { it.lufs.maxGain },
            { copy(lufs = lufs.copy(maxGain = it)) },
            range = 0.0f..12.0f,
        )
    val speed =
        int(
            ViperParams.PARAM_LUFS_SPEED,
            "speed",
            1,
            { it.lufs.speed },
            { copy(lufs = lufs.copy(speed = it)) },
            range = 0..2,
        )
}

class FetCompressorEffect : EffectGroupBuilder("fetCompressor") {
    val enable =
        bool(
            ViperParams.PARAM_FET_COMPRESSOR_ENABLE,
            "enable",
            false,
            { it.fetCompressor.enable },
            { copy(fetCompressor = fetCompressor.copy(enable = it)) },
        )
    val threshold =
        float(
            ViperParams.PARAM_FET_COMPRESSOR_THRESHOLD,
            "threshold",
            compressorDbToRaw(-18.0f),
            { it.fetCompressor.threshold },
            { copy(fetCompressor = fetCompressor.copy(threshold = it)) },
            range = compressorDbToRaw(-48.0f)..0.0f,
        )
    val ratio =
        float(
            ViperParams.PARAM_FET_COMPRESSOR_RATIO,
            "ratio",
            compressorRatioToRaw(1.0f),
            { it.fetCompressor.ratio },
            { copy(fetCompressor = fetCompressor.copy(ratio = it)) },
            range = -2.0f..0.0f,
        )
    val kneeAuto =
        bool(
            ViperParams.PARAM_FET_COMPRESSOR_KNEE_AUTO,
            "kneeAuto",
            true,
            { it.fetCompressor.kneeAuto },
            { copy(fetCompressor = fetCompressor.copy(kneeAuto = it)) },
        )
    val knee =
        float(
            ViperParams.PARAM_FET_COMPRESSOR_KNEE,
            "knee",
            0.0f,
            { it.fetCompressor.knee },
            { copy(fetCompressor = fetCompressor.copy(knee = it)) },
            range = 0.0f..compressorDbToRaw(12.0f),
        )
    val kneeMulti =
        float(
            ViperParams.PARAM_FET_COMPRESSOR_KNEE_MULTI,
            "kneeMulti",
            0.0f,
            { it.fetCompressor.kneeMulti },
            { copy(fetCompressor = fetCompressor.copy(kneeMulti = it)) },
            range = 0.0f..4.0f,
        )
    val gainAuto =
        bool(
            ViperParams.PARAM_FET_COMPRESSOR_GAIN_AUTO,
            "gainAuto",
            true,
            { it.fetCompressor.gainAuto },
            { copy(fetCompressor = fetCompressor.copy(gainAuto = it)) },
        )
    val gain =
        float(
            ViperParams.PARAM_FET_COMPRESSOR_GAIN,
            "gain",
            0.0f,
            { it.fetCompressor.gain },
            { copy(fetCompressor = fetCompressor.copy(gain = it)) },
            range = 0.0f..compressorDbToRaw(24.0f),
        )
    val attackAuto =
        bool(
            ViperParams.PARAM_FET_COMPRESSOR_ATTACK_AUTO,
            "attackAuto",
            true,
            { it.fetCompressor.attackAuto },
            { copy(fetCompressor = fetCompressor.copy(attackAuto = it)) },
        )
    val attack =
        float(
            ViperParams.PARAM_FET_COMPRESSOR_ATTACK,
            "attack",
            compressorMsToSeconds(20.0f),
            { it.fetCompressor.attack },
            { copy(fetCompressor = fetCompressor.copy(attack = it)) },
            range = 0.001f..0.100f,
        )
    val maxAttack =
        float(
            ViperParams.PARAM_FET_COMPRESSOR_MAX_ATTACK,
            "maxAttack",
            compressorMsToSeconds(80.0f),
            { it.fetCompressor.maxAttack },
            { copy(fetCompressor = fetCompressor.copy(maxAttack = it)) },
            range = 0.001f..0.100f,
        )
    val releaseAuto =
        bool(
            ViperParams.PARAM_FET_COMPRESSOR_RELEASE_AUTO,
            "releaseAuto",
            true,
            { it.fetCompressor.releaseAuto },
            { copy(fetCompressor = fetCompressor.copy(releaseAuto = it)) },
        )
    val release =
        float(
            ViperParams.PARAM_FET_COMPRESSOR_RELEASE,
            "release",
            compressorMsToSeconds(50.0f),
            { it.fetCompressor.release },
            { copy(fetCompressor = fetCompressor.copy(release = it)) },
            range = 0.005f..0.500f,
        )
    val maxRelease =
        float(
            ViperParams.PARAM_FET_COMPRESSOR_MAX_RELEASE,
            "maxRelease",
            compressorMsToSeconds(100.0f),
            { it.fetCompressor.maxRelease },
            { copy(fetCompressor = fetCompressor.copy(maxRelease = it)) },
            range = 0.005f..0.500f,
        )
    val crest =
        float(
            ViperParams.PARAM_FET_COMPRESSOR_CREST,
            "crest",
            compressorMsToSeconds(100.0f),
            { it.fetCompressor.crest },
            { copy(fetCompressor = fetCompressor.copy(crest = it)) },
            range = 0.005f..0.300f,
        )
    val adapt =
        float(
            ViperParams.PARAM_FET_COMPRESSOR_ADAPT,
            "adapt",
            compressorAdaptAmountToSeconds(0.5f),
            { it.fetCompressor.adapt },
            { copy(fetCompressor = fetCompressor.copy(adapt = it)) },
            range = 1.0f..16.0f,
        )
    val noClip =
        bool(
            ViperParams.PARAM_FET_COMPRESSOR_NO_CLIP,
            "noClip",
            true,
            { it.fetCompressor.noClip },
            { copy(fetCompressor = fetCompressor.copy(noClip = it)) },
        )
}

class MultibandCompressorEffect : EffectGroupBuilder("multibandCompressor") {
    val enable =
        bool(
            ViperParams.PARAM_MULTIBAND_COMPRESSOR_ENABLE,
            "enable",
            false,
            { it.multibandCompressor.enable },
            { copy(multibandCompressor = multibandCompressor.copy(enable = it)) },
        )
    val bandEnables =
        boolList(
            ViperParams.PARAM_MULTIBAND_COMPRESSOR_BAND_ENABLE,
            "bandEnables",
            listOf(true, true, true, true, true),
            { it.multibandCompressor.bandEnables },
            { copy(multibandCompressor = multibandCompressor.copy(bandEnables = it)) },
        )
    val crossovers =
        intList(
            ViperParams.PARAM_MULTIBAND_COMPRESSOR_CROSSOVER_FREQUENCY,
            "crossovers",
            listOf(120, 500, 4000, 8000),
            { it.multibandCompressor.crossovers },
            { copy(multibandCompressor = multibandCompressor.copy(crossovers = it)) },
            range = 30..16000,
        )
    val thresholds =
        floatList(
            ViperParams.PARAM_MULTIBAND_COMPRESSOR_BAND_THRESHOLD,
            "thresholds",
            List(5) { compressorDbToRaw(-18.0f) },
            { it.multibandCompressor.thresholds },
            { copy(multibandCompressor = multibandCompressor.copy(thresholds = it)) },
            range = compressorDbToRaw(-48.0f)..0.0f,
        )
    val ratios =
        floatList(
            ViperParams.PARAM_MULTIBAND_COMPRESSOR_BAND_RATIO,
            "ratios",
            List(5) { compressorRatioToRaw(0.5f) },
            { it.multibandCompressor.ratios },
            { copy(multibandCompressor = multibandCompressor.copy(ratios = it)) },
            range = -2.0f..0.0f,
        )
    val gains =
        floatList(
            ViperParams.PARAM_MULTIBAND_COMPRESSOR_BAND_GAIN,
            "gains",
            listOf(0.0f, 0.0f, 0.0f, 0.0f, 0.0f),
            { it.multibandCompressor.gains },
            { copy(multibandCompressor = multibandCompressor.copy(gains = it)) },
            range = 0.0f..compressorDbToRaw(24.0f),
        )
    val knees =
        floatList(
            ViperParams.PARAM_MULTIBAND_COMPRESSOR_BAND_KNEE,
            "knees",
            listOf(0.0f, 0.0f, 0.0f, 0.0f, 0.0f),
            { it.multibandCompressor.knees },
            { copy(multibandCompressor = multibandCompressor.copy(knees = it)) },
            range = 0.0f..compressorDbToRaw(12.0f),
        )
    val kneeMultis =
        floatList(
            ViperParams.PARAM_MULTIBAND_COMPRESSOR_BAND_KNEE_MULTI,
            "kneeMultis",
            listOf(0.0f, 0.0f, 0.0f, 0.0f, 0.0f),
            { it.multibandCompressor.kneeMultis },
            { copy(multibandCompressor = multibandCompressor.copy(kneeMultis = it)) },
            range = 0.0f..4.0f,
        )
    val attacks =
        floatList(
            ViperParams.PARAM_MULTIBAND_COMPRESSOR_BAND_ATTACK,
            "attacks",
            List(5) { compressorMsToSeconds(1.0f) },
            { it.multibandCompressor.attacks },
            { copy(multibandCompressor = multibandCompressor.copy(attacks = it)) },
            range = 0.001f..0.100f,
        )
    val maxAttacks =
        floatList(
            ViperParams.PARAM_MULTIBAND_COMPRESSOR_BAND_MAX_ATTACK,
            "maxAttacks",
            List(5) { compressorMsToSeconds(44.0f) },
            { it.multibandCompressor.maxAttacks },
            { copy(multibandCompressor = multibandCompressor.copy(maxAttacks = it)) },
            range = 0.001f..0.100f,
        )
    val releases =
        floatList(
            ViperParams.PARAM_MULTIBAND_COMPRESSOR_BAND_RELEASE,
            "releases",
            List(5) { compressorMsToSeconds(100.0f) },
            { it.multibandCompressor.releases },
            { copy(multibandCompressor = multibandCompressor.copy(releases = it)) },
            range = 0.005f..0.500f,
        )
    val maxReleases =
        floatList(
            ViperParams.PARAM_MULTIBAND_COMPRESSOR_BAND_MAX_RELEASE,
            "maxReleases",
            List(5) { compressorMsToSeconds(200.0f) },
            { it.multibandCompressor.maxReleases },
            { copy(multibandCompressor = multibandCompressor.copy(maxReleases = it)) },
            range = 0.005f..0.500f,
        )
    val crests =
        floatList(
            ViperParams.PARAM_MULTIBAND_COMPRESSOR_BAND_CREST,
            "crests",
            List(5) { compressorMsToSeconds(100.0f) },
            { it.multibandCompressor.crests },
            { copy(multibandCompressor = multibandCompressor.copy(crests = it)) },
            range = 0.005f..0.300f,
        )
    val adapts =
        floatList(
            ViperParams.PARAM_MULTIBAND_COMPRESSOR_BAND_ADAPT,
            "adapts",
            List(5) { compressorAdaptAmountToSeconds(0.5f) },
            { it.multibandCompressor.adapts },
            { copy(multibandCompressor = multibandCompressor.copy(adapts = it)) },
            range = 1.0f..16.0f,
        )
    val kneeAutos =
        boolList(
            ViperParams.PARAM_MULTIBAND_COMPRESSOR_BAND_KNEE_AUTO,
            "kneeAutos",
            listOf(true, true, true, true, true),
            { it.multibandCompressor.kneeAutos },
            { copy(multibandCompressor = multibandCompressor.copy(kneeAutos = it)) },
        )
    val gainAutos =
        boolList(
            ViperParams.PARAM_MULTIBAND_COMPRESSOR_BAND_GAIN_AUTO,
            "gainAutos",
            listOf(true, true, true, true, true),
            { it.multibandCompressor.gainAutos },
            { copy(multibandCompressor = multibandCompressor.copy(gainAutos = it)) },
        )
    val attackAutos =
        boolList(
            ViperParams.PARAM_MULTIBAND_COMPRESSOR_BAND_ATTACK_AUTO,
            "attackAutos",
            listOf(true, true, true, true, true),
            { it.multibandCompressor.attackAutos },
            { copy(multibandCompressor = multibandCompressor.copy(attackAutos = it)) },
        )
    val releaseAutos =
        boolList(
            ViperParams.PARAM_MULTIBAND_COMPRESSOR_BAND_RELEASE_AUTO,
            "releaseAutos",
            listOf(true, true, true, true, true),
            { it.multibandCompressor.releaseAutos },
            { copy(multibandCompressor = multibandCompressor.copy(releaseAutos = it)) },
        )
    val noClips =
        boolList(
            ViperParams.PARAM_MULTIBAND_COMPRESSOR_BAND_NO_CLIP,
            "noClips",
            listOf(true, true, true, true, true),
            { it.multibandCompressor.noClips },
            { copy(multibandCompressor = multibandCompressor.copy(noClips = it)) },
        )
}

class DdcEffect : EffectGroupBuilder("ddc") {
    val enable =
        bool(
            ViperParams.PARAM_DDC_ENABLE,
            "enable",
            false,
            { it.ddc.enable },
            { copy(ddc = ddc.copy(enable = it)) },
        )
    val device =
        string(
            -1,
            "device",
            "",
            { it.ddc.device },
            { copy(ddc = ddc.copy(device = it)) },
        )
}

class SpectrumExtensionEffect : EffectGroupBuilder("spectrumExtension") {
    val enable =
        bool(
            ViperParams.PARAM_SPECTRUM_EXTENSION_ENABLE,
            "enable",
            false,
            { it.spectrumExtension.enable },
            { copy(spectrumExtension = spectrumExtension.copy(enable = it)) },
        )
    val strength =
        int(
            ViperParams.PARAM_SPECTRUM_EXTENSION_STRENGTH,
            "strength",
            7600,
            { it.spectrumExtension.strength },
            { copy(spectrumExtension = spectrumExtension.copy(strength = it)) },
            range = 2200..8200,
        )
    val exciter =
        float(
            ViperParams.PARAM_SPECTRUM_EXTENSION_EXCITER,
            "exciter",
            0.0f,
            { it.spectrumExtension.exciter },
            { copy(spectrumExtension = spectrumExtension.copy(exciter = it)) },
            range = 0.0f..6.0f,
        )
}

class EqualizerEffect : EffectGroupBuilder("equalizer") {
    val enable =
        bool(
            ViperParams.PARAM_EQUALIZER_ENABLE,
            "enable",
            false,
            { it.eq.enable },
            { copy(eq = eq.copy(enable = it)) },
        )
    val bandCount =
        int(
            ViperParams.PARAM_EQUALIZER_BAND_COUNT,
            "bandCount",
            10,
            { it.eq.bandCount },
            { copy(eq = eq.copy(bandCount = it)) },
        )
    val bands =
        doubleList(
            ViperParams.PARAM_EQUALIZER_BAND_LEVELS,
            "bands",
            List(10) { 0.0 },
            { it.eq.bands },
            { copy(eq = eq.copy(bands = it)) },
            range = -12.0..12.0,
        )
    val presetId =
        nullableLong(
            "presetId",
            { it.eq.presetId },
            { copy(eq = eq.copy(presetId = it)) },
        )
}

class DynamicEqEffect : EffectGroupBuilder("dynamicEq") {
    val enable =
        bool(
            ViperParams.PARAM_DYNAMIC_EQ_ENABLE,
            "enable",
            false,
            { it.dynamicEq.enable },
            { copy(dynamicEq = dynamicEq.copy(enable = it)) },
        )
    val bandCount =
        int(
            ViperParams.PARAM_DYNAMIC_EQ_BAND_COUNT,
            "bandCount",
            3,
            { it.dynamicEq.bandCount },
            { copy(dynamicEq = dynamicEq.copy(bandCount = it)) },
        )
    val freqs =
        intList(
            ViperParams.PARAM_DYNAMIC_EQ_BAND_FREQUENCY,
            "freqs",
            listOf(60, 150, 400),
            { it.dynamicEq.freqs },
            { copy(dynamicEq = dynamicEq.copy(freqs = it)) },
            range = 20..20000,
        )
    val qs =
        floatList(
            ViperParams.PARAM_DYNAMIC_EQ_BAND_Q,
            "qs",
            listOf(1.0f, 1.0f, 1.5f),
            { it.dynamicEq.qs },
            { copy(dynamicEq = dynamicEq.copy(qs = it)) },
            range = 0.5f..8.0f,
        )
    val gains =
        floatList(
            ViperParams.PARAM_DYNAMIC_EQ_BAND_GAIN,
            "gains",
            listOf(0.0f, 0.0f, 0.0f),
            { it.dynamicEq.gains },
            { copy(dynamicEq = dynamicEq.copy(gains = it)) },
            range = -12.0f..12.0f,
        )
    val thresholds =
        floatList(
            ViperParams.PARAM_DYNAMIC_EQ_BAND_THRESHOLD,
            "thresholds",
            listOf(-20.0f, -20.0f, -20.0f),
            { it.dynamicEq.thresholds },
            { copy(dynamicEq = dynamicEq.copy(thresholds = it)) },
            range = -80.0f..0.0f,
        )
    val attacks =
        floatList(
            ViperParams.PARAM_DYNAMIC_EQ_BAND_ATTACK,
            "attacks",
            listOf(10.0f, 10.0f, 10.0f),
            { it.dynamicEq.attacks },
            { copy(dynamicEq = dynamicEq.copy(attacks = it)) },
            range = 1.0f..100.0f,
        )
    val releases =
        floatList(
            ViperParams.PARAM_DYNAMIC_EQ_BAND_RELEASE,
            "releases",
            listOf(100.0f, 100.0f, 100.0f),
            { it.dynamicEq.releases },
            { copy(dynamicEq = dynamicEq.copy(releases = it)) },
            range = 10.0f..500.0f,
        )
    val filterTypes =
        intList(
            ViperParams.PARAM_DYNAMIC_EQ_BAND_FILTER_TYPE,
            "filterTypes",
            listOf(0, 0, 0),
            { it.dynamicEq.filterTypes },
            { copy(dynamicEq = dynamicEq.copy(filterTypes = it)) },
        )
}

class ConvolverEffect : EffectGroupBuilder("convolver") {
    val enable =
        bool(
            ViperParams.PARAM_CONVOLVER_ENABLE,
            "enable",
            false,
            { it.convolver.enable },
            { copy(convolver = convolver.copy(enable = it)) },
        )
    val kernelFile =
        string(
            -1,
            "kernelFile",
            "",
            { it.convolver.kernelFile },
            { copy(convolver = convolver.copy(kernelFile = it)) },
        )
    val crossChannel =
        float(
            ViperParams.PARAM_CONVOLVER_CROSS_CHANNEL,
            "crossChannel",
            0.0f,
            { it.convolver.crossChannel },
            { copy(convolver = convolver.copy(crossChannel = it)) },
            range = 0.0f..1.0f,
        )
}

class FieldSurroundEffect : EffectGroupBuilder("fieldSurround") {
    val enable =
        bool(
            ViperParams.PARAM_FIELD_SURROUND_ENABLE,
            "enable",
            false,
            { it.fieldSurround.enable },
            { copy(fieldSurround = fieldSurround.copy(enable = it)) },
        )
    val widening =
        float(
            ViperParams.PARAM_FIELD_SURROUND_WIDENING,
            "widening",
            0.0f,
            { it.fieldSurround.widening },
            { copy(fieldSurround = fieldSurround.copy(widening = it)) },
            range = 0.0f..8.0f,
        )
    val midImage =
        float(
            ViperParams.PARAM_FIELD_SURROUND_MID_IMAGE,
            "midImage",
            1.5f,
            { it.fieldSurround.midImage },
            { copy(fieldSurround = fieldSurround.copy(midImage = it)) },
            range = 1.0f..2.0f,
        )
    val depth =
        int(
            ViperParams.PARAM_FIELD_SURROUND_DEPTH,
            "depth",
            200,
            { it.fieldSurround.depth },
            { copy(fieldSurround = fieldSurround.copy(depth = it)) },
            range = 200..950,
        )
}

class DiffSurroundEffect : EffectGroupBuilder("diffSurround") {
    val enable =
        bool(
            ViperParams.PARAM_DIFF_SURROUND_ENABLE,
            "enable",
            false,
            { it.diffSurround.enable },
            { copy(diffSurround = diffSurround.copy(enable = it)) },
        )
    val delay =
        float(
            ViperParams.PARAM_DIFF_SURROUND_DELAY,
            "delay",
            5.0f,
            { it.diffSurround.delay },
            { copy(diffSurround = diffSurround.copy(delay = it)) },
            range = 1.0f..20.0f,
        )
    val reverse =
        bool(
            ViperParams.PARAM_DIFF_SURROUND_REVERSE,
            "reverse",
            false,
            { it.diffSurround.reverse },
            { copy(diffSurround = diffSurround.copy(reverse = it)) },
        )
    val wetDryMix =
        float(
            ViperParams.PARAM_DIFF_SURROUND_WET_DRY_MIX,
            "wetDryMix",
            1.0f,
            { it.diffSurround.wetDryMix },
            { copy(diffSurround = diffSurround.copy(wetDryMix = it)) },
            range = 0.0f..1.0f,
        )
    val lpCutoff =
        int(
            ViperParams.PARAM_DIFF_SURROUND_LP_CUTOFF,
            "lpCutoff",
            0,
            { it.diffSurround.lpCutoff },
            { copy(diffSurround = diffSurround.copy(lpCutoff = it)) },
            range = 0..20000,
        )
}

class StereoImagerEffect : EffectGroupBuilder("stereoImager") {
    val enable =
        bool(
            ViperParams.PARAM_STEREO_IMAGER_ENABLE,
            "enable",
            false,
            { it.stereoImager.enable },
            { copy(stereoImager = stereoImager.copy(enable = it)) },
        )
    val lowWidth =
        float(
            ViperParams.PARAM_STEREO_IMAGER_LOW_WIDTH,
            "lowWidth",
            1.0f,
            { it.stereoImager.lowWidth },
            { copy(stereoImager = stereoImager.copy(lowWidth = it)) },
            range = 0.0f..2.0f,
        )
    val midWidth =
        float(
            ViperParams.PARAM_STEREO_IMAGER_MID_WIDTH,
            "midWidth",
            1.0f,
            { it.stereoImager.midWidth },
            { copy(stereoImager = stereoImager.copy(midWidth = it)) },
            range = 0.0f..2.0f,
        )
    val highWidth =
        float(
            ViperParams.PARAM_STEREO_IMAGER_HIGH_WIDTH,
            "highWidth",
            1.0f,
            { it.stereoImager.highWidth },
            { copy(stereoImager = stereoImager.copy(highWidth = it)) },
            range = 0.0f..2.0f,
        )
    val lowCrossover =
        int(
            ViperParams.PARAM_STEREO_IMAGER_LOW_CROSSOVER,
            "lowCrossover",
            200,
            { it.stereoImager.lowCrossover },
            { copy(stereoImager = stereoImager.copy(lowCrossover = it)) },
            range = 80..400,
        )
    val highCrossover =
        int(
            ViperParams.PARAM_STEREO_IMAGER_HIGH_CROSSOVER,
            "highCrossover",
            4000,
            { it.stereoImager.highCrossover },
            { copy(stereoImager = stereoImager.copy(highCrossover = it)) },
            range = 2000..8000,
        )
}

class HeadphoneSurroundEffect : EffectGroupBuilder("headphoneSurround") {
    val enable =
        bool(
            ViperParams.PARAM_HEADPHONE_SURROUND_ENABLE,
            "enable",
            false,
            { it.headphoneSurround.enable },
            { copy(headphoneSurround = headphoneSurround.copy(enable = it)) },
        )
    val quality =
        int(
            ViperParams.PARAM_HEADPHONE_SURROUND_QUALITY,
            "quality",
            0,
            { it.headphoneSurround.quality },
            { copy(headphoneSurround = headphoneSurround.copy(quality = it)) },
            range = 0..4,
        )
}

class ReverbEffect : EffectGroupBuilder("reverb") {
    val enable =
        bool(
            ViperParams.PARAM_REVERB_ENABLE,
            "enable",
            false,
            { it.reverb.enable },
            { copy(reverb = reverb.copy(enable = it)) },
        )
    val roomSize =
        float(
            ViperParams.PARAM_REVERB_ROOM_SIZE,
            "roomSize",
            0.0f,
            { it.reverb.roomSize },
            { copy(reverb = reverb.copy(roomSize = it)) },
            range = 0.0f..1.0f,
        )
    val width =
        float(
            ViperParams.PARAM_REVERB_WIDTH,
            "width",
            0.0f,
            { it.reverb.width },
            { copy(reverb = reverb.copy(width = it)) },
            range = 0.0f..1.0f,
        )
    val damp =
        float(
            ViperParams.PARAM_REVERB_DAMP,
            "damp",
            0.5f,
            { it.reverb.damp },
            { copy(reverb = reverb.copy(damp = it)) },
            range = 0.0f..1.0f,
        )
    val wet =
        float(
            ViperParams.PARAM_REVERB_WET,
            "wet",
            0.0f,
            { it.reverb.wet },
            { copy(reverb = reverb.copy(wet = it)) },
            range = 0.0f..1.0f,
        )
    val dry =
        float(
            ViperParams.PARAM_REVERB_DRY,
            "dry",
            1.0f,
            { it.reverb.dry },
            { copy(reverb = reverb.copy(dry = it)) },
            range = 0.0f..1.0f,
        )
}

class DynamicSystemEffect : EffectGroupBuilder("dynamicSystem") {
    val enable =
        bool(
            ViperParams.PARAM_DYNAMIC_SYSTEM_ENABLE,
            "enable",
            false,
            { it.dynamicSystem.enable },
            { copy(dynamicSystem = dynamicSystem.copy(enable = it)) },
        )
    val presetId =
        nullableLong(
            "presetId",
            { it.dynamicSystem.presetId },
            { copy(dynamicSystem = dynamicSystem.copy(presetId = it)) },
        )
    val device =
        int(
            -1,
            "device",
            0,
            { it.dynamicSystem.device },
            { copy(dynamicSystem = dynamicSystem.copy(device = it)) },
        )
    val strength =
        float(
            ViperParams.PARAM_DYNAMIC_SYSTEM_STRENGTH,
            "strength",
            0.5f,
            { it.dynamicSystem.strength },
            { copy(dynamicSystem = dynamicSystem.copy(strength = it)) },
            range = 0.0f..1.0f,
        )
    val xLow =
        int(
            ViperParams.PARAM_DYNAMIC_SYSTEM_X_LOW,
            "xLow",
            100,
            { it.dynamicSystem.xLow },
            { copy(dynamicSystem = dynamicSystem.copy(xLow = it)) },
            range = 0..2400,
        )
    val xHigh =
        int(
            ViperParams.PARAM_DYNAMIC_SYSTEM_X_HIGH,
            "xHigh",
            5600,
            { it.dynamicSystem.xHigh },
            { copy(dynamicSystem = dynamicSystem.copy(xHigh = it)) },
            range = 0..12000,
        )
    val yLow =
        int(
            ViperParams.PARAM_DYNAMIC_SYSTEM_Y_LOW,
            "yLow",
            40,
            { it.dynamicSystem.yLow },
            { copy(dynamicSystem = dynamicSystem.copy(yLow = it)) },
            range = 0..200,
        )
    val yHigh =
        int(
            ViperParams.PARAM_DYNAMIC_SYSTEM_Y_HIGH,
            "yHigh",
            80,
            { it.dynamicSystem.yHigh },
            { copy(dynamicSystem = dynamicSystem.copy(yHigh = it)) },
            range = 0..300,
        )
    val sideGainLow =
        float(
            ViperParams.PARAM_DYNAMIC_SYSTEM_SIDE_GAIN_LOW,
            "sideGainLow",
            0.5f,
            { it.dynamicSystem.sideGainLow },
            { copy(dynamicSystem = dynamicSystem.copy(sideGainLow = it)) },
            range = 0.0f..1.0f,
        )
    val sideGainHigh =
        float(
            ViperParams.PARAM_DYNAMIC_SYSTEM_SIDE_GAIN_HIGH,
            "sideGainHigh",
            0.5f,
            { it.dynamicSystem.sideGainHigh },
            { copy(dynamicSystem = dynamicSystem.copy(sideGainHigh = it)) },
            range = 0.0f..1.0f,
        )
}

class PsychoacousticBassEffect : EffectGroupBuilder("psychoacousticBass") {
    val enable =
        bool(
            ViperParams.PARAM_PSYCHOACOUSTIC_BASS_ENABLE,
            "enable",
            false,
            { it.psychoacousticBass.enable },
            { copy(psychoacousticBass = psychoacousticBass.copy(enable = it)) },
        )
    val cutoff =
        int(
            ViperParams.PARAM_PSYCHOACOUSTIC_BASS_CUTOFF,
            "cutoff",
            80,
            { it.psychoacousticBass.cutoff },
            { copy(psychoacousticBass = psychoacousticBass.copy(cutoff = it)) },
            range = 60..150,
        )
    val intensity =
        float(
            ViperParams.PARAM_PSYCHOACOUSTIC_BASS_INTENSITY,
            "intensity",
            0.5f,
            { it.psychoacousticBass.intensity },
            { copy(psychoacousticBass = psychoacousticBass.copy(intensity = it)) },
            range = 0.0f..1.0f,
        )
    val harmonicOrder =
        int(
            ViperParams.PARAM_PSYCHOACOUSTIC_BASS_HARMONIC_ORDER,
            "harmonicOrder",
            3,
            { it.psychoacousticBass.harmonicOrder },
            { copy(psychoacousticBass = psychoacousticBass.copy(harmonicOrder = it)) },
            range = 2..5,
        )
    val originalLevel =
        float(
            ViperParams.PARAM_PSYCHOACOUSTIC_BASS_ORIGINAL_LEVEL,
            "originalLevel",
            1.0f,
            { it.psychoacousticBass.originalLevel },
            { copy(psychoacousticBass = psychoacousticBass.copy(originalLevel = it)) },
            range = 0.0f..1.0f,
        )
}

class BassEffect : EffectGroupBuilder("bass") {
    val enable =
        bool(
            ViperParams.PARAM_BASS_ENABLE,
            "enable",
            false,
            { it.bass.enable },
            { copy(bass = bass.copy(enable = it)) },
        )
    val mode =
        int(
            ViperParams.PARAM_BASS_MODE,
            "mode",
            0,
            { it.bass.mode },
            { copy(bass = bass.copy(mode = it)) },
        )
    val frequency =
        int(
            ViperParams.PARAM_BASS_FREQUENCY,
            "frequency",
            60,
            { it.bass.frequency },
            { copy(bass = bass.copy(frequency = it)) },
            range = 15..150,
        )
    val gain =
        float(
            ViperParams.PARAM_BASS_GAIN,
            "gain",
            0.5f,
            { it.bass.gain },
            { copy(bass = bass.copy(gain = it)) },
            range = 0.5f..10.0f,
        )
    val antiPop =
        bool(
            ViperParams.PARAM_BASS_ANTI_POP,
            "antiPop",
            false,
            { it.bass.antiPop },
            { copy(bass = bass.copy(antiPop = it)) },
        )
}

class BassMonoEffect : EffectGroupBuilder("bassMono") {
    val enable =
        bool(
            ViperParams.PARAM_BASS_MONO_ENABLE,
            "enable",
            false,
            { it.bassMono.enable },
            { copy(bassMono = bassMono.copy(enable = it)) },
        )
    val mode =
        int(
            ViperParams.PARAM_BASS_MONO_MODE,
            "mode",
            0,
            { it.bassMono.mode },
            { copy(bassMono = bassMono.copy(mode = it)) },
        )
    val frequency =
        int(
            ViperParams.PARAM_BASS_MONO_FREQUENCY,
            "frequency",
            60,
            { it.bassMono.frequency },
            { copy(bassMono = bassMono.copy(frequency = it)) },
            range = 15..150,
        )
    val gain =
        float(
            ViperParams.PARAM_BASS_MONO_GAIN,
            "gain",
            0.5f,
            { it.bassMono.gain },
            { copy(bassMono = bassMono.copy(gain = it)) },
            range = 0.5f..10.0f,
        )
    val antiPop =
        bool(
            ViperParams.PARAM_BASS_MONO_ANTI_POP,
            "antiPop",
            false,
            { it.bassMono.antiPop },
            { copy(bassMono = bassMono.copy(antiPop = it)) },
        )
}

class ClarityEffect : EffectGroupBuilder("clarity") {
    val enable =
        bool(
            ViperParams.PARAM_CLARITY_ENABLE,
            "enable",
            false,
            { it.clarity.enable },
            { copy(clarity = clarity.copy(enable = it)) },
        )
    val mode =
        int(
            ViperParams.PARAM_CLARITY_MODE,
            "mode",
            0,
            { it.clarity.mode },
            { copy(clarity = clarity.copy(mode = it)) },
        )
    val gain =
        float(
            ViperParams.PARAM_CLARITY_GAIN,
            "gain",
            0.5f,
            { it.clarity.gain },
            { copy(clarity = clarity.copy(gain = it)) },
            range = 0.0f..4.5f,
        )
}

class CureEffect : EffectGroupBuilder("cure") {
    val enable =
        bool(
            ViperParams.PARAM_CURE_ENABLE,
            "enable",
            false,
            { it.cure.enable },
            { copy(cure = cure.copy(enable = it)) },
        )
    val crossfeedPreset =
        int(
            ViperParams.PARAM_CURE_CROSSFEED_PRESET,
            "crossfeedPreset",
            0,
            { it.cure.crossfeedPreset },
            { copy(cure = cure.copy(crossfeedPreset = it)) },
        )
}

class TubeSimulatorEffect : EffectGroupBuilder("tubeSimulator") {
    val enable =
        bool(
            ViperParams.PARAM_TUBE_SIMULATOR_ENABLE,
            "enable",
            false,
            { it.tubeSimulator.enable },
            { copy(tubeSimulator = tubeSimulator.copy(enable = it)) },
        )
    val model =
        int(
            ViperParams.PARAM_TUBE_SIMULATOR_MODEL,
            "model",
            0,
            { it.tubeSimulator.model },
            { copy(tubeSimulator = tubeSimulator.copy(model = it)) },
            range = 0..2,
        )
    val drive =
        float(
            ViperParams.PARAM_TUBE_SIMULATOR_DRIVE,
            "drive",
            0.5f,
            { it.tubeSimulator.drive },
            { copy(tubeSimulator = tubeSimulator.copy(drive = it)) },
            range = 0.0f..1.0f,
        )
    val mix =
        float(
            ViperParams.PARAM_TUBE_SIMULATOR_MIX,
            "mix",
            0.3f,
            { it.tubeSimulator.mix },
            { copy(tubeSimulator = tubeSimulator.copy(mix = it)) },
            range = 0.0f..1.0f,
        )
}

class AnalogXEffect : EffectGroupBuilder("analogX") {
    val enable =
        bool(
            ViperParams.PARAM_ANALOG_X_ENABLE,
            "enable",
            false,
            { it.analogX.enable },
            { copy(analogX = analogX.copy(enable = it)) },
        )
    val mode =
        int(
            ViperParams.PARAM_ANALOG_X_MODE,
            "mode",
            0,
            { it.analogX.mode },
            { copy(analogX = analogX.copy(mode = it)) },
        )
}

class SpeakerCorrectionEffect : EffectGroupBuilder("speakerCorrection") {
    val enable =
        bool(
            ViperParams.PARAM_SPEAKER_CORRECTION_ENABLE,
            "enable",
            false,
            { it.speakerCorrection.enable },
            { copy(speakerCorrection = speakerCorrection.copy(enable = it)) },
        )
}

object Effects {
    val masterEnable: BoolPref =
        BoolPref(
            effectKey = "",
            paramId = -1,
            jsonKey = "masterEnable",
            defaultValue = false,
            get = { it.masterEnable },
            set = { copy(masterEnable = it) },
            prefKeyOverride = ViperRepository.PREF_MASTER_ENABLE,
        )

    val masterLimiter = MasterLimiterEffect()
    val playbackGainControl = PlaybackGainControlEffect()
    val lufs = LufsEffect()
    val fetCompressor = FetCompressorEffect()
    val multibandCompressor = MultibandCompressorEffect()
    val ddc = DdcEffect()
    val spectrumExtension = SpectrumExtensionEffect()
    val equalizer = EqualizerEffect()
    val dynamicEq = DynamicEqEffect()
    val convolver = ConvolverEffect()
    val fieldSurround = FieldSurroundEffect()
    val diffSurround = DiffSurroundEffect()
    val stereoImager = StereoImagerEffect()
    val headphoneSurround = HeadphoneSurroundEffect()
    val reverb = ReverbEffect()
    val dynamicSystem = DynamicSystemEffect()
    val psychoacousticBass = PsychoacousticBassEffect()
    val bass = BassEffect()
    val bassMono = BassMonoEffect()
    val clarity = ClarityEffect()
    val cure = CureEffect()
    val tubeSimulator = TubeSimulatorEffect()
    val analogX = AnalogXEffect()
    val speakerCorrection = SpeakerCorrectionEffect()
}

val EFFECT_GROUPS: List<EffectGroup> =
    listOf(
        Effects.masterLimiter,
        Effects.playbackGainControl,
        Effects.lufs,
        Effects.fetCompressor,
        Effects.multibandCompressor,
        Effects.ddc,
        Effects.spectrumExtension,
        Effects.equalizer,
        Effects.dynamicEq,
        Effects.convolver,
        Effects.fieldSurround,
        Effects.diffSurround,
        Effects.stereoImager,
        Effects.headphoneSurround,
        Effects.reverb,
        Effects.dynamicSystem,
        Effects.psychoacousticBass,
        Effects.bass,
        Effects.bassMono,
        Effects.clarity,
        Effects.cure,
        Effects.tubeSimulator,
        Effects.analogX,
        Effects.speakerCorrection,
    ).map { it.toGroup() }
