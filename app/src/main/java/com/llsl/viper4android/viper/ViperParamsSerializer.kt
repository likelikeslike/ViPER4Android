package com.llsl.viper4android.viper

import com.llsl.viper4android.effect.AnalogXState
import com.llsl.viper4android.effect.BassMonoState
import com.llsl.viper4android.effect.BassState
import com.llsl.viper4android.effect.ClarityState
import com.llsl.viper4android.effect.ConvolverState
import com.llsl.viper4android.effect.CureState
import com.llsl.viper4android.effect.DdcState
import com.llsl.viper4android.effect.DiffSurroundState
import com.llsl.viper4android.effect.DynamicEqState
import com.llsl.viper4android.effect.DynamicSystemState
import com.llsl.viper4android.effect.EffectState
import com.llsl.viper4android.effect.EqState
import com.llsl.viper4android.effect.FetCompressorState
import com.llsl.viper4android.effect.FieldSurroundState
import com.llsl.viper4android.effect.HeadphoneSurroundState
import com.llsl.viper4android.effect.LufsState
import com.llsl.viper4android.effect.MultibandCompressorState
import com.llsl.viper4android.effect.OutputState
import com.llsl.viper4android.effect.ParamRaw
import com.llsl.viper4android.effect.PlaybackGainControlState
import com.llsl.viper4android.effect.PsychoacousticBassState
import com.llsl.viper4android.effect.ReverbState
import com.llsl.viper4android.effect.SpeakerCorrectionState
import com.llsl.viper4android.effect.SpectrumExtensionState
import com.llsl.viper4android.effect.StereoImagerState
import com.llsl.viper4android.effect.TubeSimulatorState
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Serialize an EffectState to the byte layout of viper::ViPERParams as defined in ViperParamsLayout.kt.
 */
object ViperParamsSerializer {
    /**
     * Serialize the entire ViPERParams struct into [buf] starting at [offset].
     */
    fun write(
        buf: ByteBuffer,
        offset: Int,
        state: EffectState,
    ) {
        require(buf.order() == ByteOrder.LITTLE_ENDIAN) {
            "ByteBuffer must be little-endian to match C++ struct layout"
        }
        for (i in 0 until ViperParamsLayout.SIZE) {
            buf.put(offset + i, 0)
        }

        writeMasterLimiter(
            buf,
            offset + ViperParamsLayout.MASTER_LIMITER,
            state.out,
        )
        writePlaybackGainControl(
            buf,
            offset + ViperParamsLayout.PLAYBACK_GAIN_CONTROL,
            state.playbackGainControl,
        )
        writeLufs(buf, offset + ViperParamsLayout.LUFS, state.lufs)
        writeFetCompressor(
            buf,
            offset + ViperParamsLayout.FET_COMPRESSOR,
            state.fetCompressor,
        )
        writeBass(buf, offset + ViperParamsLayout.BASS, state.bass)
        writeBassMono(buf, offset + ViperParamsLayout.BASS_MONO, state.bassMono)
        writePsychoacousticBass(
            buf,
            offset + ViperParamsLayout.PSYCHOACOUSTIC_BASS,
            state.psychoacousticBass,
        )
        writeSpectrumExtension(
            buf,
            offset + ViperParamsLayout.SPECTRUM_EXTENSION,
            state.spectrumExtension,
        )
        writeEqualizer(buf, offset + ViperParamsLayout.EQUALIZER, state.eq)
        writeConvolver(buf, offset + ViperParamsLayout.CONVOLVER, state.convolver)
        writeDdc(buf, offset + ViperParamsLayout.DDC, state.ddc)
        writeFieldSurround(
            buf,
            offset + ViperParamsLayout.FIELD_SURROUND,
            state.fieldSurround,
        )
        writeDiffSurround(
            buf,
            offset + ViperParamsLayout.DIFF_SURROUND,
            state.diffSurround,
        )
        writeStereoImager(
            buf,
            offset + ViperParamsLayout.STEREO_IMAGER,
            state.stereoImager,
        )
        writeHeadphoneSurround(
            buf,
            offset + ViperParamsLayout.HEADPHONE_SURROUND,
            state.headphoneSurround,
        )
        writeReverb(buf, offset + ViperParamsLayout.REVERB, state.reverb)
        writeDynamicSystem(
            buf,
            offset + ViperParamsLayout.DYNAMIC_SYSTEM,
            state.dynamicSystem,
        )
        writeClarity(buf, offset + ViperParamsLayout.CLARITY, state.clarity)
        writeCure(buf, offset + ViperParamsLayout.CURE, state.cure)
        writeTubeSimulator(
            buf,
            offset + ViperParamsLayout.TUBE_SIMULATOR,
            state.tubeSimulator,
        )
        writeAnalogX(buf, offset + ViperParamsLayout.ANALOG_X, state.analogX)
        writeSpeakerCorrection(
            buf,
            offset + ViperParamsLayout.SPEAKER_CORRECTION,
            state.speakerCorrection,
        )
        writeMultibandCompressor(
            buf,
            offset + ViperParamsLayout.MULTIBAND_COMPRESSOR,
            state.multibandCompressor,
        )
        writeDynamicEq(
            buf,
            offset + ViperParamsLayout.DYNAMIC_EQ,
            state.dynamicEq,
        )
    }

    private fun ByteBuffer.putBool(
        off: Int,
        value: Boolean,
    ) {
        this.put(off, if (value) 1 else 0)
    }

    private fun writeMasterLimiter(
        buf: ByteBuffer,
        base: Int,
        s: OutputState,
    ) {
        val l = ViperParamsLayout.MasterLimiter
        buf.putFloat(base + l.THRESHOLD, s.limiter / 100f)
        buf.putFloat(base + l.OUTPUT_VOLUME, s.volume / 100f)
        buf.putFloat(base + l.CHANNEL_PAN, s.channelPan / 100f)
    }

    private fun writePlaybackGainControl(
        buf: ByteBuffer,
        base: Int,
        s: PlaybackGainControlState,
    ) {
        val l = ViperParamsLayout.PlaybackGainControl
        buf.putBool(base + l.ENABLE, s.enable)
        buf.putFloat(base + l.STRENGTH, s.strength / 100f)
        buf.putFloat(base + l.MAX_GAIN, s.maxGain / 100f)
        buf.putFloat(base + l.OUTPUT_THRESHOLD, s.outputThreshold / 100f)
    }

    private fun writeLufs(
        buf: ByteBuffer,
        base: Int,
        s: LufsState,
    ) {
        val l = ViperParamsLayout.Lufs
        buf.putBool(base + l.ENABLE, s.enable)
        buf.putFloat(base + l.TARGET, s.target / -10f)
        buf.putFloat(base + l.MAX_GAIN, s.maxGain / 10f)
        buf.putInt(base + l.SPEED, s.speed)
    }

    private fun writeFetCompressor(
        buf: ByteBuffer,
        base: Int,
        s: FetCompressorState,
    ) {
        val l = ViperParamsLayout.FetCompressor
        buf.putBool(base + l.ENABLE, s.enable)
        buf.putFloat(base + l.THRESHOLD, ParamRaw.fetCompressorThresholdF(s.threshold))
        buf.putFloat(base + l.RATIO, s.ratio / 100f)
        buf.putFloat(base + l.KNEE, ParamRaw.fetCompressorKneeF(s.knee))
        buf.putBool(base + l.KNEE_AUTO, s.kneeAuto)
        buf.putFloat(base + l.GAIN, ParamRaw.fetCompressorGainF(s.gain))
        buf.putBool(base + l.GAIN_AUTO, s.gainAuto)
        buf.putFloat(base + l.ATTACK, ParamRaw.fetCompressorAttackMsF(s.attack))
        buf.putBool(base + l.ATTACK_AUTO, s.attackAuto)
        buf.putFloat(base + l.RELEASE, ParamRaw.fetCompressorReleaseMsF(s.release))
        buf.putBool(base + l.RELEASE_AUTO, s.releaseAuto)
        buf.putFloat(base + l.KNEE_MULTI, s.kneeMulti / 100f)
        buf.putFloat(base + l.MAX_ATTACK, ParamRaw.fetCompressorAttackMsF(s.maxAttack))
        buf.putFloat(base + l.MAX_RELEASE, ParamRaw.fetCompressorReleaseMsF(s.maxRelease))
        buf.putFloat(base + l.CREST, s.crest / 100f)
        buf.putFloat(base + l.ADAPT, s.adapt / 100f)
        buf.putBool(base + l.NO_CLIP, s.noClip)
    }

    private fun writeBass(
        buf: ByteBuffer,
        base: Int,
        s: BassState,
    ) {
        val l = ViperParamsLayout.Bass
        buf.putBool(base + l.ENABLE, s.enable)
        buf.putInt(base + l.MODE, s.mode)
        buf.putInt(base + l.FREQUENCY, ParamRaw.bassFrequency(s.frequency))
        buf.putFloat(base + l.GAIN, s.gain / 100f)
        buf.putBool(base + l.ANTI_POP, s.antiPop)
    }

    private fun writeBassMono(
        buf: ByteBuffer,
        base: Int,
        s: BassMonoState,
    ) {
        val l = ViperParamsLayout.BassMono
        buf.putBool(base + l.ENABLE, s.enable)
        buf.putInt(base + l.MODE, s.mode)
        buf.putInt(base + l.FREQUENCY, ParamRaw.bassFrequency(s.frequency))
        buf.putFloat(base + l.GAIN, s.gain / 100f)
        buf.putBool(base + l.ANTI_POP, s.antiPop)
    }

    private fun writePsychoacousticBass(
        buf: ByteBuffer,
        base: Int,
        s: PsychoacousticBassState,
    ) {
        val l = ViperParamsLayout.PsychoacousticBass
        buf.putBool(base + l.ENABLE, s.enable)
        buf.putInt(base + l.CUTOFF, s.cutoff)
        buf.putInt(base + l.INTENSITY, s.intensity)
        buf.putInt(base + l.HARMONIC_ORDER, s.harmonicOrder)
        buf.putInt(base + l.ORIGINAL_LEVEL, s.originalLevel)
    }

    private fun writeSpectrumExtension(
        buf: ByteBuffer,
        base: Int,
        s: SpectrumExtensionState,
    ) {
        val l = ViperParamsLayout.SpectrumExtension
        buf.putBool(base + l.ENABLE, s.enable)
        buf.putInt(base + l.STRENGTH, s.strength)
        buf.putFloat(base + l.EXCITER, ParamRaw.spectrumExtensionExciterF(s.exciter))
    }

    private fun writeEqualizer(
        buf: ByteBuffer,
        base: Int,
        s: EqState,
    ) {
        val l = ViperParamsLayout.Equalizer
        buf.putBool(base + l.ENABLE, s.enable)
        buf.putInt(base + l.BAND_COUNT, s.bandCount)
        val maxBands = l.BAND_LEVELS_LEN
        s.bands.forEachIndexed { index, bandDb ->
            if (index >= maxBands) return@forEachIndexed
            buf.putFloat(base + l.BAND_LEVELS + index * 4, bandDb.toFloat())
        }
    }

    private fun writeConvolver(
        buf: ByteBuffer,
        base: Int,
        s: ConvolverState,
    ) {
        val l = ViperParamsLayout.Convolver
        buf.putBool(base + l.ENABLE, s.enable)
        buf.putFloat(base + l.CROSS_CHANNEL, s.crossChannel / 100f)
    }

    private fun writeDdc(
        buf: ByteBuffer,
        base: Int,
        s: DdcState,
    ) {
        val l = ViperParamsLayout.Ddc
        buf.putBool(base + l.ENABLE, s.enable)
    }

    private fun writeFieldSurround(
        buf: ByteBuffer,
        base: Int,
        s: FieldSurroundState,
    ) {
        val l = ViperParamsLayout.FieldSurround
        buf.putBool(base + l.ENABLE, s.enable)
        buf.putFloat(base + l.WIDENING, s.widening.toFloat())
        buf.putFloat(base + l.MID_IMAGE, ParamRaw.fieldSurroundMidImage(s.midImage) / 100f)
        buf.putShort(base + l.DEPTH, ParamRaw.fieldSurroundDepth(s.depth).toShort())
    }

    private fun writeDiffSurround(
        buf: ByteBuffer,
        base: Int,
        s: DiffSurroundState,
    ) {
        val l = ViperParamsLayout.DiffSurround
        buf.putBool(base + l.ENABLE, s.enable)
        buf.putFloat(base + l.DELAY, s.delay.toFloat())
        buf.putBool(base + l.REVERSE, s.reverse)
        buf.putFloat(base + l.WET_DRY_MIX, s.wetDryMix / 100f)
        buf.putFloat(base + l.LP_CUTOFF, s.lpCutoff.toFloat())
    }

    private fun writeStereoImager(
        buf: ByteBuffer,
        base: Int,
        s: StereoImagerState,
    ) {
        val l = ViperParamsLayout.StereoImager
        buf.putBool(base + l.ENABLE, s.enable)
        buf.putFloat(base + l.LOW_WIDTH, s.lowWidth.toFloat())
        buf.putFloat(base + l.MID_WIDTH, s.midWidth.toFloat())
        buf.putFloat(base + l.HIGH_WIDTH, s.highWidth.toFloat())
        buf.putFloat(base + l.LOW_CROSSOVER, s.lowCrossover.toFloat())
        buf.putFloat(base + l.HIGH_CROSSOVER, s.highCrossover.toFloat())
    }

    private fun writeHeadphoneSurround(
        buf: ByteBuffer,
        base: Int,
        s: HeadphoneSurroundState,
    ) {
        val l = ViperParamsLayout.HeadphoneSurround
        buf.putBool(base + l.ENABLE, s.enable)
        buf.putInt(base + l.QUALITY, s.quality)
    }

    private fun writeReverb(
        buf: ByteBuffer,
        base: Int,
        s: ReverbState,
    ) {
        val l = ViperParamsLayout.Reverb
        buf.putBool(base + l.ENABLE, s.enable)
        buf.putFloat(base + l.ROOM_SIZE, ParamRaw.reverbRoomSize(s.roomSize) / 100f)
        buf.putFloat(base + l.WIDTH, ParamRaw.reverbWidth(s.width) / 100f)
        buf.putFloat(base + l.DAMP, ParamRaw.reverbDamp(s.damp) / 100f)
        buf.putFloat(base + l.WET, s.wet / 100f)
        buf.putFloat(base + l.DRY, s.dry / 100f)
    }

    private fun writeDynamicSystem(
        buf: ByteBuffer,
        base: Int,
        s: DynamicSystemState,
    ) {
        val l = ViperParamsLayout.DynamicSystem
        buf.putBool(base + l.ENABLE, s.enable)
        buf.putInt(base + l.X_COEFF_LOW, s.xLow)
        buf.putInt(base + l.X_COEFF_HIGH, s.xHigh)
        buf.putInt(base + l.Y_COEFF_LOW, s.yLow)
        buf.putInt(base + l.Y_COEFF_HIGH, s.yHigh)
        buf.putFloat(base + l.SIDE_GAIN_LOW, s.sideGainLow / 100f)
        buf.putFloat(base + l.SIDE_GAIN_HIGH, s.sideGainHigh / 100f)
        buf.putFloat(base + l.STRENGTH, ParamRaw.dynamicSystemStrength(s.strength) / 100f)
    }

    private fun writeClarity(
        buf: ByteBuffer,
        base: Int,
        s: ClarityState,
    ) {
        val l = ViperParamsLayout.Clarity
        buf.putBool(base + l.ENABLE, s.enable)
        buf.putInt(base + l.MODE, s.mode)
        buf.putFloat(base + l.GAIN, s.gain / 100f)
    }

    private fun writeCure(
        buf: ByteBuffer,
        base: Int,
        s: CureState,
    ) {
        val l = ViperParamsLayout.Cure
        buf.putBool(base + l.ENABLE, s.enable)
        buf.putInt(base + l.CROSSFEED_PRESET, s.crossfeedPreset)
    }

    private fun writeTubeSimulator(
        buf: ByteBuffer,
        base: Int,
        s: TubeSimulatorState,
    ) {
        val l = ViperParamsLayout.TubeSimulator
        buf.putBool(base + l.ENABLE, s.enable)
    }

    private fun writeAnalogX(
        buf: ByteBuffer,
        base: Int,
        s: AnalogXState,
    ) {
        val l = ViperParamsLayout.AnalogX
        buf.putBool(base + l.ENABLE, s.enable)
        buf.putInt(base + l.MODE, s.mode)
    }

    private fun writeSpeakerCorrection(
        buf: ByteBuffer,
        base: Int,
        s: SpeakerCorrectionState,
    ) {
        val l = ViperParamsLayout.SpeakerCorrection
        buf.putBool(base + l.ENABLE, s.enable)
    }

    private fun writeMultibandCompressor(
        buf: ByteBuffer,
        base: Int,
        s: MultibandCompressorState,
    ) {
        val l = ViperParamsLayout.MultibandCompressor
        val lb = ViperParamsLayout.MultibandCompressorBand
        buf.putBool(base + l.ENABLE, s.enable)

        val crossovers = s.crossovers
        val bandEnables = s.bandEnables
        val thresholds = s.thresholds
        val ratios = s.ratios
        val knees = s.knees
        val kneeAutos = s.kneeAutos
        val gains = s.gains
        val gainAutos = s.gainAutos
        val attacks = s.attacks
        val attackAutos = s.attackAutos
        val releases = s.releases
        val releaseAutos = s.releaseAutos
        val kneeMultis = s.kneeMultis
        val maxAttacks = s.maxAttacks
        val maxReleases = s.maxReleases
        val crests = s.crests
        val adapts = s.adapts
        val noClips = s.noClips

        val bandCount = minOf(bandEnables.size, l.BANDS_LEN)
        buf.putInt(base + l.BAND_COUNT, bandCount)

        crossovers.forEachIndexed { i, v ->
            if (i >= l.CROSSOVER_FREQUENCIES_LEN) return@forEachIndexed
            buf.putFloat(base + l.CROSSOVER_FREQUENCIES + i * 4, v.toFloat())
        }

        for (i in 0 until bandCount) {
            val bandBase = base + l.BANDS + i * lb.SIZE
            buf.putBool(bandBase + lb.ENABLE, bandEnables.getOrFalse(i))
            buf.putFloat(bandBase + lb.THRESHOLD, ParamRaw.fetCompressorThresholdF(thresholds.getOrZero(i)))
            buf.putFloat(bandBase + lb.RATIO, ratios.getOrZero(i) / 100f)
            buf.putFloat(bandBase + lb.KNEE, ParamRaw.fetCompressorKneeF(knees.getOrZero(i)))
            buf.putBool(bandBase + lb.KNEE_AUTO, kneeAutos.getOrFalse(i))
            buf.putFloat(bandBase + lb.GAIN, ParamRaw.fetCompressorGainF(gains.getOrZero(i)))
            buf.putBool(bandBase + lb.GAIN_AUTO, gainAutos.getOrFalse(i))
            buf.putFloat(bandBase + lb.ATTACK, ParamRaw.fetCompressorAttackMsF(attacks.getOrZero(i)))
            buf.putBool(bandBase + lb.ATTACK_AUTO, attackAutos.getOrFalse(i))
            buf.putFloat(bandBase + lb.RELEASE, ParamRaw.fetCompressorReleaseMsF(releases.getOrZero(i)))
            buf.putBool(bandBase + lb.RELEASE_AUTO, releaseAutos.getOrFalse(i))
            buf.putFloat(bandBase + lb.KNEE_MULTI, kneeMultis.getOrZero(i) / 100f)
            buf.putFloat(bandBase + lb.MAX_ATTACK, ParamRaw.fetCompressorAttackMsF(maxAttacks.getOrZero(i)))
            buf.putFloat(bandBase + lb.MAX_RELEASE, ParamRaw.fetCompressorReleaseMsF(maxReleases.getOrZero(i)))
            buf.putFloat(bandBase + lb.CREST, ParamRaw.fetCompressorReleaseMsF(crests.getOrZero(i)))
            buf.putFloat(bandBase + lb.ADAPT, adapts.getOrZero(i) / 100f)
            buf.putBool(bandBase + lb.NO_CLIP, noClips.getOrFalse(i))
        }
    }

    private fun writeDynamicEq(
        buf: ByteBuffer,
        base: Int,
        s: DynamicEqState,
    ) {
        val l = ViperParamsLayout.DynamicEq
        val lb = ViperParamsLayout.DynamicEqBand
        buf.putBool(base + l.ENABLE, s.enable)

        val freqs = s.freqs
        val qs = s.qs
        val gains = s.gains
        val thresholds = s.thresholds
        val attacks = s.attacks
        val releases = s.releases
        val filterTypes = s.filterTypes

        val bandCount = minOf(s.bandCount, l.BANDS_LEN)
        buf.putInt(base + l.BAND_COUNT, bandCount)

        for (i in 0 until bandCount) {
            val bandBase = base + l.BANDS + i * lb.SIZE
            buf.putFloat(bandBase + lb.FREQUENCY, freqs.getOrZero(i).toFloat())
            buf.putFloat(bandBase + lb.Q, qs.getOrZero(i) / 100f)
            buf.putFloat(bandBase + lb.GAIN, gains.getOrZero(i) / 10f)
            buf.putFloat(bandBase + lb.THRESHOLD, thresholds.getOrZero(i) / 10f)
            buf.putFloat(bandBase + lb.ATTACK, attacks.getOrZero(i).toFloat())
            buf.putFloat(bandBase + lb.RELEASE, releases.getOrZero(i).toFloat())
            buf.putInt(bandBase + lb.FILTER_TYPE, filterTypes.getOrZero(i))
        }
    }

    private fun List<Int>.getOrZero(i: Int): Int = if (i in indices) this[i] else 0

    private fun List<Boolean>.getOrFalse(i: Int): Boolean = if (i in indices) this[i] else false
}
