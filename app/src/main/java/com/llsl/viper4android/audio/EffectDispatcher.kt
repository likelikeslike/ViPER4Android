package com.llsl.viper4android.audio


import com.llsl.viper4android.data.repository.ViperRepository
import com.llsl.viper4android.ui.screens.main.MainUiState
import com.llsl.viper4android.utils.FileLogger
import kotlinx.coroutines.flow.first
import java.util.Locale

object EffectDispatcher {

    val OUTPUT_VOLUME_VALUES = intArrayOf(
        1, 5, 10, 20, 30, 40, 50, 60, 70, 80, 90, 100,
        110, 120, 130, 140, 150, 160, 170, 180, 190, 200
    )
    val OUTPUT_DB_VALUES = intArrayOf(30, 50, 70, 80, 90, 100)
    val PLAYBACK_GAIN_RATIO_VALUES = intArrayOf(50, 100, 300)
    val MULTI_FACTOR_VALUES = intArrayOf(
        100, 200, 300, 400, 500, 600, 700, 800, 900, 1000, 3000
    )
    val VSE_BARK_VALUES = intArrayOf(
        2200, 2800, 3400, 4000, 4600, 5200, 5800, 6400, 7000, 7600, 8200
    )
    val DIFF_SURROUND_DELAY_VALUES = IntArray(20) { (it + 1) * 100 }
    val FIELD_SURROUND_WIDENING_VALUES = intArrayOf(0, 100, 200, 300, 400, 500, 600, 700, 800)

    val BASS_GAIN_DB_LABELS = arrayOf(
        "3.5", "6.0", "8.0", "10.0", "11.0", "12.0",
        "13.0", "14.0", "14.8", "15.6", "16.3", "17.0"
    )
    val CLARITY_GAIN_DB_LABELS = arrayOf(
        "0.0", "3.5", "6.0", "8.0", "10.0", "11.0",
        "12.0", "13.0", "14.0", "14.8"
    )

    val EQ_PRESETS = listOf(
        "4.5;4.5;3.5;1.2;1.0;0.5;1.4;1.75;3.5;2.5;",
        "6.0;4.0;2.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;",
        "-6.0;-4.0;-2.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;",
        "0.0;0.0;0.0;0.0;0.0;0.0;-3.0;-3.0;-3.0;-5.0;",
        "3.0;2.0;1.0;0.5;0.5;0.0;-1.0;-2.0;-3.0;-3.5;",
        "0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;",
        "3.0;6.0;4.0;1.0;-1.0;-0.5;1.0;1.5;2.5;3.0;",
        "4.0;3.0;1.0;0.0;-0.5;0.0;1.5;2.5;3.5;4.0;",
        "3.0;2.0;1.5;1.0;0.5;-0.5;-1.5;-2.0;-3.0;-3.5;",
        "0.0;0.0;0.0;0.0;0.0;1.0;2.0;3.0;4.0;5.0;",
        "0.0;0.0;0.0;0.0;0.0;-1.0;-2.0;-3.0;-4.0;-5.0;",
        "-1.0;-0.5;0.0;1.5;3.0;3.0;2.0;1.0;0.0;-1.0;"
    )

    val EQ_PRESETS_15 = listOf(
        "4.5;4.5;4.5;4.0;2.5;1.0;1.0;1.0;0.5;1.0;1.5;2.0;3.0;3.0;2.5;",
        "6.0;5.5;4.0;2.5;1.5;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;",
        "-6.0;-5.5;-4.0;-2.5;-1.5;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;",
        "0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;-2.0;-3.0;-3.0;-3.0;-3.5;-5.0;",
        "3.0;2.5;2.0;1.5;1.0;0.5;0.5;0.5;0.0;-0.5;-1.5;-2.0;-2.5;-3.0;-3.5;",
        "0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;",
        "3.0;4.0;6.0;4.5;3.0;1.0;-0.5;-1.0;-0.5;0.5;1.0;1.5;2.0;2.5;3.0;",
        "4.0;3.5;3.0;1.5;0.5;0.0;-0.5;-0.5;0.0;1.0;2.0;2.5;3.0;3.5;4.0;",
        "3.0;2.5;2.0;1.5;1.5;1.0;0.5;0.0;-0.5;-1.0;-1.5;-2.0;-2.5;-3.0;-3.5;",
        "0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.5;1.0;1.5;2.5;3.0;3.5;4.5;5.0;",
        "0.0;0.0;0.0;0.0;0.0;0.0;0.0;-0.5;-1.0;-1.5;-2.5;-3.0;-3.5;-4.5;-5.0;",
        "-1.0;-1.0;-0.5;0.0;0.5;1.5;2.5;3.0;3.0;2.5;1.5;1.0;0.5;-0.5;-1.0;"
    )

    val EQ_PRESETS_25 = listOf(
        "4.5;4.5;4.5;4.5;4.0;4.0;3.5;2.5;1.0;1.0;1.0;1.0;0.5;0.5;1.0;1.0;1.5;1.5;2.0;2.5;3.5;3.0;3.0;2.5;2.5;",
        "6.0;6.0;5.5;4.5;3.5;2.5;2.0;1.5;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;",
        "-6.0;-6.0;-5.5;-4.5;-3.5;-2.5;-2.0;-1.5;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;",
        "0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;-1.0;-2.0;-3.0;-3.0;-3.0;-3.0;-3.0;-3.5;-4.5;-5.0;-5.0;",
        "3.0;3.0;2.5;2.5;1.5;1.5;1.0;1.0;0.5;0.5;0.5;0.5;0.0;0.0;-0.5;-0.5;-1.5;-1.5;-2.0;-2.5;-3.0;-3.0;-3.5;-3.5;-3.5;",
        "0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;",
        "3.0;3.0;4.0;5.0;5.5;4.5;4.0;3.0;1.0;0.5;-0.5;-1.0;-0.5;-0.5;0.0;0.5;1.0;1.5;1.5;2.0;2.5;2.5;3.0;3.0;3.0;",
        "4.0;4.0;3.5;3.5;2.5;1.5;1.0;0.5;0.0;0.0;-0.5;-0.5;0.0;0.0;0.5;1.0;2.0;2.0;2.5;3.0;3.5;3.5;4.0;4.0;4.0;",
        "3.0;3.0;2.5;2.5;2.0;1.5;1.5;1.5;1.0;1.0;0.5;0.5;0.0;-0.5;-1.0;-1.0;-1.5;-2.0;-2.0;-2.5;-3.0;-3.0;-3.5;-3.5;-3.5;",
        "0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.5;1.0;1.5;1.5;2.5;2.5;3.0;3.5;4.0;4.5;4.5;5.0;5.0;",
        "0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;-0.5;-1.0;-1.5;-1.5;-2.5;-2.5;-3.0;-3.5;-4.0;-4.5;-4.5;-5.0;-5.0;",
        "-1.0;-1.0;-1.0;-0.5;-0.5;0.0;0.0;0.5;1.5;2.0;2.5;3.0;3.0;3.0;2.5;2.5;1.5;1.5;1.0;0.5;0.0;-0.5;-0.5;-1.0;-1.0;"
    )

    val EQ_PRESETS_31 = listOf(
        "4.5;4.5;4.5;4.5;4.5;4.5;4.0;4.0;3.5;2.5;2.0;1.0;1.0;1.0;1.0;1.0;0.5;0.5;1.0;1.0;1.5;1.5;1.5;2.0;2.5;3.0;3.5;3.0;3.0;2.5;2.5;",
        "6.0;6.0;6.0;5.5;4.5;4.0;3.5;2.5;2.0;1.5;0.5;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;",
        "-6.0;-6.0;-6.0;-5.5;-4.5;-4.0;-3.5;-2.5;-2.0;-1.5;-0.5;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;",
        "0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;-1.0;-2.0;-3.0;-3.0;-3.0;-3.0;-3.0;-3.0;-3.0;-3.5;-4.5;-5.0;-5.0;",
        "3.0;3.0;3.0;2.5;2.5;2.0;1.5;1.5;1.0;1.0;0.5;0.5;0.5;0.5;0.5;0.5;0.0;0.0;-0.5;-0.5;-1.0;-1.5;-1.5;-2.0;-2.5;-2.5;-3.0;-3.0;-3.5;-3.5;-3.5;",
        "0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;",
        "3.0;3.0;3.0;4.0;5.0;6.0;5.5;4.5;4.0;3.0;2.0;1.0;0.5;-0.5;-1.0;-1.0;-0.5;-0.5;0.0;0.5;1.0;1.0;1.5;1.5;2.0;2.0;2.5;2.5;3.0;3.0;3.0;",
        "4.0;4.0;4.0;3.5;3.5;3.0;2.5;1.5;1.0;0.5;0.5;0.0;0.0;-0.5;-0.5;-0.5;0.0;0.0;0.5;1.0;1.5;2.0;2.0;2.5;3.0;3.0;3.5;3.5;4.0;4.0;4.0;",
        "3.0;3.0;3.0;2.5;2.5;2.0;2.0;1.5;1.5;1.5;1.0;1.0;1.0;0.5;0.5;0.0;0.0;-0.5;-1.0;-1.0;-1.5;-1.5;-2.0;-2.0;-2.5;-2.5;-3.0;-3.0;-3.5;-3.5;-3.5;",
        "0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.5;0.5;1.0;1.5;1.5;2.0;2.5;2.5;3.0;3.5;3.5;4.0;4.5;4.5;5.0;5.0;",
        "0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;-0.5;-0.5;-1.0;-1.5;-1.5;-2.0;-2.5;-2.5;-3.0;-3.5;-3.5;-4.0;-4.5;-4.5;-5.0;-5.0;",
        "-1.0;-1.0;-1.0;-1.0;-0.5;-0.5;-0.5;0.0;0.0;0.5;1.0;1.5;2.0;2.5;3.0;3.0;3.0;3.0;2.5;2.5;2.0;1.5;1.5;1.0;0.5;0.5;0.0;-0.5;-0.5;-1.0;-1.0;"
    )

    val DYNAMIC_SYSTEM_DEVICES = listOf(
        "140;6200;40;60;10;80",
        "180;5800;55;80;10;70",
        "300;5600;60;105;10;50",
        "600;5400;60;105;10;20",
        "100;5600;40;80;50;50",
        "1200;6200;40;80;0;20",
        "1000;6200;40;80;0;10",
        "800;6200;40;80;10;0",
        "400;6200;40;80;10;0",
        "1200;6200;50;90;15;10",
        "1000;6200;50;90;30;10",
        "1100;6200;60;100;20;0",
        "1200;6200;50;100;10;50",
        "1200;6200;60;100;0;30",
        "1200;6200;40;80;0;30",
        "1000;6200;60;100;0;0",
        "1000;6200;60;120;0;0",
        "1000;6200;80;140;0;0",
        "800;6200;80;140;0;0",
        "0;0;0;0;0;0",
        "180;5400;40;60;50;0",
        "1200;6000;40;60;0;80",
        "140;5400;40;60;0;0"
    )

    val DYNAMIC_SYSTEM_DEVICE_NAMES = listOf(
        "Extreme Headphone (v2)", "High-End Headphone (v2)",
        "Common Headphone (v2)", "Low-End Headphone (v2)",
        "Common Earphone (v2)", "Extreme Headphone (v1)",
        "High-End Headphone (v1)", "Common Headphone (v1)",
        "Common Earphone (v1)", "Apple Earphone",
        "Monster Earphone", "Motorola Earphone",
        "Philips Earphone", "SHP2000",
        "SHP9000", "Unknown Type I",
        "Unknown Type II", "Unknown Type III",
        "Unknown Type IV", "Unknown Type V",
        "pittvandewitt flavor #1", "pittvandewitt flavor #2",
        "pittvandewitt flavor #3"
    )

    val EQ_BAND_LABELS_10 =
        listOf("31Hz", "62Hz", "125Hz", "250Hz", "500Hz", "1kHz", "2kHz", "4kHz", "8kHz", "16kHz")
    val EQ_BAND_LABELS_15 = listOf(
        "25Hz",
        "40Hz",
        "63Hz",
        "100Hz",
        "160Hz",
        "250Hz",
        "400Hz",
        "630Hz",
        "1kHz",
        "1.6kHz",
        "2.5kHz",
        "4kHz",
        "6.3kHz",
        "10kHz",
        "16kHz"
    )
    val EQ_BAND_LABELS_25 = listOf(
        "20Hz",
        "31Hz",
        "40Hz",
        "50Hz",
        "80Hz",
        "100Hz",
        "125Hz",
        "160Hz",
        "250Hz",
        "315Hz",
        "400Hz",
        "500Hz",
        "800Hz",
        "1kHz",
        "1.25kHz",
        "1.6kHz",
        "2.5kHz",
        "3.15kHz",
        "4kHz",
        "5kHz",
        "8kHz",
        "10kHz",
        "12.5kHz",
        "16kHz",
        "20kHz"
    )
    val EQ_BAND_LABELS_31 = listOf(
        "20Hz",
        "25Hz",
        "31Hz",
        "40Hz",
        "50Hz",
        "63Hz",
        "80Hz",
        "100Hz",
        "125Hz",
        "160Hz",
        "200Hz",
        "250Hz",
        "315Hz",
        "400Hz",
        "500Hz",
        "630Hz",
        "800Hz",
        "1kHz",
        "1.25kHz",
        "1.6kHz",
        "2kHz",
        "2.5kHz",
        "3.15kHz",
        "4kHz",
        "5kHz",
        "6.3kHz",
        "8kHz",
        "10kHz",
        "12.5kHz",
        "16kHz",
        "20kHz"
    )

    fun eqBandLabelsForCount(count: Int): List<String> = when (count) {
        15 -> EQ_BAND_LABELS_15; 25 -> EQ_BAND_LABELS_25; 31 -> EQ_BAND_LABELS_31; else -> EQ_BAND_LABELS_10
    }

    private fun ensureBandCount(rawBands: String, expectedCount: Int): String {
        val actualCount = rawBands.split(";").count { it.isNotBlank() }
        return if (actualCount != expectedCount) {
            List(expectedCount) { 0f }.joinToString(";") {
                String.format(
                    Locale.US,
                    "%.1f",
                    it
                )
            } + ";"
        } else {
            rawBands
        }
    }

    val EQ_GRAPH_LABELS_10 = listOf("31", "62", "125", "250", "500", "1k", "2k", "4k", "8k", "16k")
    val EQ_GRAPH_LABELS_15 = listOf(
        "25",
        "40",
        "63",
        "100",
        "160",
        "250",
        "400",
        "630",
        "1k",
        "1.6k",
        "2.5k",
        "4k",
        "6.3k",
        "10k",
        "16k"
    )
    val EQ_GRAPH_LABELS_25 = listOf(
        "20",
        "31",
        "40",
        "50",
        "80",
        "100",
        "125",
        "160",
        "250",
        "315",
        "400",
        "500",
        "800",
        "1k",
        "1.25k",
        "1.6k",
        "2.5k",
        "3.15k",
        "4k",
        "5k",
        "8k",
        "10k",
        "12.5k",
        "16k",
        "20k"
    )
    val EQ_GRAPH_LABELS_31 = listOf(
        "20",
        "25",
        "31",
        "40",
        "50",
        "63",
        "80",
        "100",
        "125",
        "160",
        "200",
        "250",
        "315",
        "400",
        "500",
        "630",
        "800",
        "1k",
        "1.25k",
        "1.6k",
        "2k",
        "2.5k",
        "3.15k",
        "4k",
        "5k",
        "6.3k",
        "8k",
        "10k",
        "12.5k",
        "16k",
        "20k"
    )

    fun eqGraphLabelsForCount(count: Int): List<String> = when (count) {
        15 -> EQ_GRAPH_LABELS_15; 25 -> EQ_GRAPH_LABELS_25; 31 -> EQ_GRAPH_LABELS_31; else -> EQ_GRAPH_LABELS_10
    }

    fun dispatchFullState(effect: ViperEffect, state: MainUiState, masterEnabled: Boolean) {
        val mode = if (state.fxType == ViperParams.FX_TYPE_HEADPHONE) "Headphone" else "Speaker"
        FileLogger.d(
            "Dispatch",
            "Dispatch: fullState mode=$mode master=${if (masterEnabled) "ON" else "OFF"}"
        )
        effect.setParameter(ViperParams.PARAM_SET_UPDATE_STATUS, if (masterEnabled) 1 else 0)
        effect.setParameter(ViperParams.PARAM_FX_TYPE_SWITCH, state.fxType)
        if (state.fxType == ViperParams.FX_TYPE_HEADPHONE) {
            dispatchHeadphoneState(effect, state)
        } else {
            dispatchSpeakerState(effect, state)
        }
    }

    fun dispatchHeadphoneState(effect: ViperEffect, state: MainUiState) {
        FileLogger.d(
            "Dispatch",
            "Dispatch: headphone outputVol=${state.outputVolume} pan=${state.channelPan} limiter=${state.limiter}"
        )
        effect.setParameter(
            ViperParams.PARAM_HP_OUTPUT_VOLUME,
            OUTPUT_VOLUME_VALUES.getOrElse(state.outputVolume) { 100 })
        effect.setParameter(ViperParams.PARAM_HP_CHANNEL_PAN, state.channelPan)
        effect.setParameter(
            ViperParams.PARAM_HP_LIMITER,
            OUTPUT_DB_VALUES.getOrElse(state.limiter) { 100 })

        effect.setParameter(ViperParams.PARAM_HP_AGC_ENABLE, if (state.agcEnabled) 1 else 0)
        FileLogger.d("Dispatch", "AGC: ${if (state.agcEnabled) "ON" else "OFF"}")
        effect.setParameter(
            ViperParams.PARAM_HP_AGC_RATIO,
            PLAYBACK_GAIN_RATIO_VALUES.getOrElse(state.agcStrength) { 50 })
        effect.setParameter(
            ViperParams.PARAM_HP_AGC_MAX_SCALER,
            MULTI_FACTOR_VALUES.getOrElse(state.agcMaxGain) { 100 })
        effect.setParameter(
            ViperParams.PARAM_HP_AGC_VOLUME,
            OUTPUT_DB_VALUES.getOrElse(state.agcOutputThreshold) { 100 })

        FileLogger.d("Dispatch", "FET: ${if (state.fetEnabled) "ON" else "OFF"}")
        effect.setParameter(
            ViperParams.PARAM_HP_FET_COMPRESSOR_ENABLE,
            if (state.fetEnabled) 100 else 0
        )
        effect.setParameter(ViperParams.PARAM_HP_FET_COMPRESSOR_THRESHOLD, state.fetThreshold)
        effect.setParameter(ViperParams.PARAM_HP_FET_COMPRESSOR_RATIO, state.fetRatio)
        effect.setParameter(
            ViperParams.PARAM_HP_FET_COMPRESSOR_AUTO_KNEE,
            if (state.fetAutoKnee) 100 else 0
        )
        effect.setParameter(ViperParams.PARAM_HP_FET_COMPRESSOR_KNEE, state.fetKnee)
        effect.setParameter(ViperParams.PARAM_HP_FET_COMPRESSOR_KNEE_MULTI, state.fetKneeMulti)
        effect.setParameter(
            ViperParams.PARAM_HP_FET_COMPRESSOR_AUTO_GAIN,
            if (state.fetAutoGain) 100 else 0
        )
        effect.setParameter(ViperParams.PARAM_HP_FET_COMPRESSOR_GAIN, state.fetGain)
        effect.setParameter(
            ViperParams.PARAM_HP_FET_COMPRESSOR_AUTO_ATTACK,
            if (state.fetAutoAttack) 100 else 0
        )
        effect.setParameter(ViperParams.PARAM_HP_FET_COMPRESSOR_ATTACK, state.fetAttack)
        effect.setParameter(ViperParams.PARAM_HP_FET_COMPRESSOR_MAX_ATTACK, state.fetMaxAttack)
        effect.setParameter(
            ViperParams.PARAM_HP_FET_COMPRESSOR_AUTO_RELEASE,
            if (state.fetAutoRelease) 100 else 0
        )
        effect.setParameter(ViperParams.PARAM_HP_FET_COMPRESSOR_RELEASE, state.fetRelease)
        effect.setParameter(ViperParams.PARAM_HP_FET_COMPRESSOR_MAX_RELEASE, state.fetMaxRelease)
        effect.setParameter(ViperParams.PARAM_HP_FET_COMPRESSOR_CREST, state.fetCrest)
        effect.setParameter(ViperParams.PARAM_HP_FET_COMPRESSOR_ADAPT, state.fetAdapt)
        effect.setParameter(
            ViperParams.PARAM_HP_FET_COMPRESSOR_NO_CLIP,
            if (state.fetNoClip) 100 else 0
        )

        effect.setParameter(ViperParams.PARAM_HP_DDC_ENABLE, if (state.ddcEnabled) 1 else 0)
        FileLogger.d("Dispatch", "DDC: ${if (state.ddcEnabled) "ON" else "OFF"}")

        effect.setParameter(
            ViperParams.PARAM_HP_SPECTRUM_EXTENSION_ENABLE,
            if (state.vseEnabled) 1 else 0
        )
        FileLogger.d("Dispatch", "VSE: ${if (state.vseEnabled) "ON" else "OFF"}")
        effect.setParameter(
            ViperParams.PARAM_HP_SPECTRUM_EXTENSION_BARK,
            VSE_BARK_VALUES.getOrElse(state.vseStrength) { 7600 })
        effect.setParameter(
            ViperParams.PARAM_HP_SPECTRUM_EXTENSION_BARK_RECONSTRUCT,
            (state.vseExciter * 5.6).toInt()
        )

        effect.setParameter(ViperParams.PARAM_HP_EQ_BAND_COUNT, state.eqBandCount)
        effect.setParameter(ViperParams.PARAM_HP_EQ_ENABLE, if (state.eqEnabled) 1 else 0)
        FileLogger.d(
            "Dispatch",
            "EQ: ${if (state.eqEnabled) "ON" else "OFF"} bands=${state.eqBandCount}"
        )
        dispatchEqBands(effect, ViperParams.PARAM_HP_EQ_BAND_LEVEL, state.eqBands)

        effect.setParameter(
            ViperParams.PARAM_HP_CONVOLVER_ENABLE,
            if (state.convolverEnabled) 1 else 0
        )
        FileLogger.d("Dispatch", "Convolver: ${if (state.convolverEnabled) "ON" else "OFF"}")
        effect.setParameter(
            ViperParams.PARAM_HP_CONVOLVER_CROSS_CHANNEL,
            state.convolverCrossChannel
        )

        effect.setParameter(
            ViperParams.PARAM_HP_FIELD_SURROUND_ENABLE,
            if (state.fieldSurroundEnabled) 1 else 0
        )
        FileLogger.d(
            "Dispatch",
            "FieldSurround: ${if (state.fieldSurroundEnabled) "ON" else "OFF"}"
        )
        effect.setParameter(
            ViperParams.PARAM_HP_FIELD_SURROUND_WIDENING,
            FIELD_SURROUND_WIDENING_VALUES.getOrElse(state.fieldSurroundWidening) { 0 })
        effect.setParameter(
            ViperParams.PARAM_HP_FIELD_SURROUND_MID_IMAGE,
            state.fieldSurroundMidImage * 10 + 100
        )
        effect.setParameter(
            ViperParams.PARAM_HP_FIELD_SURROUND_DEPTH,
            state.fieldSurroundDepth * 75 + 200
        )

        effect.setParameter(
            ViperParams.PARAM_HP_DIFF_SURROUND_ENABLE,
            if (state.diffSurroundEnabled) 1 else 0
        )
        FileLogger.d("Dispatch", "DiffSurround: ${if (state.diffSurroundEnabled) "ON" else "OFF"}")
        effect.setParameter(
            ViperParams.PARAM_HP_DIFF_SURROUND_DELAY,
            DIFF_SURROUND_DELAY_VALUES.getOrElse(state.diffSurroundDelay) { 500 })

        effect.setParameter(
            ViperParams.PARAM_HP_HEADPHONE_SURROUND_ENABLE,
            if (state.vheEnabled) 1 else 0
        )
        FileLogger.d("Dispatch", "VHE: ${if (state.vheEnabled) "ON" else "OFF"}")
        effect.setParameter(ViperParams.PARAM_HP_HEADPHONE_SURROUND_STRENGTH, state.vheQuality)

        effect.setParameter(ViperParams.PARAM_HP_REVERB_ENABLE, if (state.reverbEnabled) 1 else 0)
        FileLogger.d("Dispatch", "Reverb: ${if (state.reverbEnabled) "ON" else "OFF"}")
        effect.setParameter(ViperParams.PARAM_HP_REVERB_ROOM_SIZE, state.reverbRoomSize * 10)
        effect.setParameter(ViperParams.PARAM_HP_REVERB_ROOM_WIDTH, state.reverbWidth * 10)
        effect.setParameter(ViperParams.PARAM_HP_REVERB_ROOM_DAMPENING, state.reverbDampening)
        effect.setParameter(ViperParams.PARAM_HP_REVERB_ROOM_WET_SIGNAL, state.reverbWet)
        effect.setParameter(ViperParams.PARAM_HP_REVERB_ROOM_DRY_SIGNAL, state.reverbDry)

        dispatchDynamicSystem(
            effect,
            state.dynamicSystemEnabled,
            state.dynamicSystemDevice,
            state.dynamicSystemStrength,
            ViperParams.PARAM_HP_DYNAMIC_SYSTEM_ENABLE,
            ViperParams.PARAM_HP_DYNAMIC_SYSTEM_STRENGTH,
            ViperParams.PARAM_HP_DYNAMIC_SYSTEM_X_COEFFICIENTS,
            ViperParams.PARAM_HP_DYNAMIC_SYSTEM_Y_COEFFICIENTS,
            ViperParams.PARAM_HP_DYNAMIC_SYSTEM_SIDE_GAIN
        )

        effect.setParameter(
            ViperParams.PARAM_HP_TUBE_SIMULATOR_ENABLE,
            if (state.tubeSimulatorEnabled) 1 else 0
        )
        FileLogger.d(
            "Dispatch",
            "TubeSimulator: ${if (state.tubeSimulatorEnabled) "ON" else "OFF"}"
        )

        effect.setParameter(ViperParams.PARAM_HP_BASS_ENABLE, if (state.bassEnabled) 1 else 0)
        FileLogger.d("Dispatch", "Bass: ${if (state.bassEnabled) "ON" else "OFF"}")
        effect.setParameter(ViperParams.PARAM_HP_BASS_MODE, state.bassMode)
        effect.setParameter(ViperParams.PARAM_HP_BASS_FREQUENCY, state.bassFrequency + 15)
        effect.setParameter(ViperParams.PARAM_HP_BASS_GAIN, state.bassGain * 50 + 50)

        effect.setParameter(ViperParams.PARAM_HP_CLARITY_ENABLE, if (state.clarityEnabled) 1 else 0)
        FileLogger.d("Dispatch", "Clarity: ${if (state.clarityEnabled) "ON" else "OFF"}")
        effect.setParameter(ViperParams.PARAM_HP_CLARITY_MODE, state.clarityMode)
        effect.setParameter(ViperParams.PARAM_HP_CLARITY_GAIN, state.clarityGain * 50)

        effect.setParameter(ViperParams.PARAM_HP_CURE_ENABLE, if (state.cureEnabled) 1 else 0)
        FileLogger.d("Dispatch", "Cure: ${if (state.cureEnabled) "ON" else "OFF"}")
        effect.setParameter(ViperParams.PARAM_HP_CURE_STRENGTH, state.cureStrength)

        effect.setParameter(ViperParams.PARAM_HP_ANALOGX_ENABLE, if (state.analogxEnabled) 1 else 0)
        FileLogger.d("Dispatch", "AnalogX: ${if (state.analogxEnabled) "ON" else "OFF"}")
        effect.setParameter(ViperParams.PARAM_HP_ANALOGX_MODE, state.analogxMode)
    }

    fun dispatchSpeakerState(effect: ViperEffect, state: MainUiState) {
        FileLogger.d(
            "Dispatch",
            "Dispatch: speaker outputVol=${state.spkOutputVolume} pan=${state.spkChannelPan} limiter=${state.spkLimiter}"
        )
        effect.setParameter(
            ViperParams.PARAM_SPK_OUTPUT_VOLUME,
            OUTPUT_VOLUME_VALUES.getOrElse(state.spkOutputVolume) { 100 })
        effect.setParameter(ViperParams.PARAM_SPK_CHANNEL_PAN, state.spkChannelPan)
        effect.setParameter(
            ViperParams.PARAM_SPK_LIMITER,
            OUTPUT_DB_VALUES.getOrElse(state.spkLimiter) { 100 })

        effect.setParameter(ViperParams.PARAM_SPK_AGC_ENABLE, if (state.spkAgcEnabled) 1 else 0)
        FileLogger.d("Dispatch", "AGC: ${if (state.spkAgcEnabled) "ON" else "OFF"}")
        effect.setParameter(
            ViperParams.PARAM_SPK_AGC_RATIO,
            PLAYBACK_GAIN_RATIO_VALUES.getOrElse(state.spkAgcStrength) { 50 })
        effect.setParameter(
            ViperParams.PARAM_SPK_AGC_MAX_SCALER,
            MULTI_FACTOR_VALUES.getOrElse(state.spkAgcMaxGain) { 100 })
        effect.setParameter(
            ViperParams.PARAM_SPK_AGC_VOLUME,
            OUTPUT_DB_VALUES.getOrElse(state.spkAgcOutputThreshold) { 100 })

        effect.setParameter(
            ViperParams.PARAM_SPK_FET_COMPRESSOR_ENABLE,
            if (state.spkFetEnabled) 100 else 0
        )
        FileLogger.d("Dispatch", "FET: ${if (state.spkFetEnabled) "ON" else "OFF"}")
        effect.setParameter(ViperParams.PARAM_SPK_FET_COMPRESSOR_THRESHOLD, state.spkFetThreshold)
        effect.setParameter(ViperParams.PARAM_SPK_FET_COMPRESSOR_RATIO, state.spkFetRatio)
        effect.setParameter(
            ViperParams.PARAM_SPK_FET_COMPRESSOR_AUTO_KNEE,
            if (state.spkFetAutoKnee) 100 else 0
        )
        effect.setParameter(ViperParams.PARAM_SPK_FET_COMPRESSOR_KNEE, state.spkFetKnee)
        effect.setParameter(ViperParams.PARAM_SPK_FET_COMPRESSOR_KNEE_MULTI, state.spkFetKneeMulti)
        effect.setParameter(
            ViperParams.PARAM_SPK_FET_COMPRESSOR_AUTO_GAIN,
            if (state.spkFetAutoGain) 100 else 0
        )
        effect.setParameter(ViperParams.PARAM_SPK_FET_COMPRESSOR_GAIN, state.spkFetGain)
        effect.setParameter(
            ViperParams.PARAM_SPK_FET_COMPRESSOR_AUTO_ATTACK,
            if (state.spkFetAutoAttack) 100 else 0
        )
        effect.setParameter(ViperParams.PARAM_SPK_FET_COMPRESSOR_ATTACK, state.spkFetAttack)
        effect.setParameter(ViperParams.PARAM_SPK_FET_COMPRESSOR_MAX_ATTACK, state.spkFetMaxAttack)
        effect.setParameter(
            ViperParams.PARAM_SPK_FET_COMPRESSOR_AUTO_RELEASE,
            if (state.spkFetAutoRelease) 100 else 0
        )
        effect.setParameter(ViperParams.PARAM_SPK_FET_COMPRESSOR_RELEASE, state.spkFetRelease)
        effect.setParameter(
            ViperParams.PARAM_SPK_FET_COMPRESSOR_MAX_RELEASE,
            state.spkFetMaxRelease
        )
        effect.setParameter(ViperParams.PARAM_SPK_FET_COMPRESSOR_CREST, state.spkFetCrest)
        effect.setParameter(ViperParams.PARAM_SPK_FET_COMPRESSOR_ADAPT, state.spkFetAdapt)
        effect.setParameter(
            ViperParams.PARAM_SPK_FET_COMPRESSOR_NO_CLIP,
            if (state.spkFetNoClip) 100 else 0
        )

        effect.setParameter(
            ViperParams.PARAM_SPK_CONVOLVER_ENABLE,
            if (state.spkConvolverEnabled) 1 else 0
        )
        FileLogger.d("Dispatch", "Convolver: ${if (state.spkConvolverEnabled) "ON" else "OFF"}")
        effect.setParameter(
            ViperParams.PARAM_SPK_CONVOLVER_CROSS_CHANNEL,
            state.spkConvolverCrossChannel
        )

        effect.setParameter(ViperParams.PARAM_SPK_EQ_BAND_COUNT, state.spkEqBandCount)
        effect.setParameter(ViperParams.PARAM_SPK_EQ_ENABLE, if (state.spkEqEnabled) 1 else 0)
        FileLogger.d(
            "Dispatch",
            "EQ: ${if (state.spkEqEnabled) "ON" else "OFF"} bands=${state.spkEqBandCount}"
        )
        dispatchEqBands(effect, ViperParams.PARAM_SPK_EQ_BAND_LEVEL, state.spkEqBands)

        effect.setParameter(
            ViperParams.PARAM_SPK_REVERB_ENABLE,
            if (state.spkReverbEnabled) 1 else 0
        )
        FileLogger.d("Dispatch", "Reverb: ${if (state.spkReverbEnabled) "ON" else "OFF"}")
        effect.setParameter(ViperParams.PARAM_SPK_REVERB_ROOM_SIZE, state.spkReverbRoomSize * 10)
        effect.setParameter(ViperParams.PARAM_SPK_REVERB_ROOM_WIDTH, state.spkReverbWidth * 10)
        effect.setParameter(ViperParams.PARAM_SPK_REVERB_ROOM_DAMPENING, state.spkReverbDampening)
        effect.setParameter(ViperParams.PARAM_SPK_REVERB_ROOM_WET_SIGNAL, state.spkReverbWet)
        effect.setParameter(ViperParams.PARAM_SPK_REVERB_ROOM_DRY_SIGNAL, state.spkReverbDry)

        effect.setParameter(ViperParams.PARAM_SPK_DDC_ENABLE, if (state.spkDdcEnabled) 1 else 0)
        FileLogger.d("Dispatch", "DDC: ${if (state.spkDdcEnabled) "ON" else "OFF"}")

        effect.setParameter(
            ViperParams.PARAM_SPK_SPECTRUM_EXTENSION_ENABLE,
            if (state.spkVseEnabled) 1 else 0
        )
        FileLogger.d("Dispatch", "VSE: ${if (state.spkVseEnabled) "ON" else "OFF"}")
        effect.setParameter(
            ViperParams.PARAM_SPK_SPECTRUM_EXTENSION_BARK,
            VSE_BARK_VALUES.getOrElse(state.spkVseStrength) { 7600 })
        effect.setParameter(
            ViperParams.PARAM_SPK_SPECTRUM_EXTENSION_BARK_RECONSTRUCT,
            (state.spkVseExciter * 5.6).toInt()
        )

        effect.setParameter(
            ViperParams.PARAM_SPK_FIELD_SURROUND_ENABLE,
            if (state.spkFieldSurroundEnabled) 1 else 0
        )
        FileLogger.d(
            "Dispatch",
            "FieldSurround: ${if (state.spkFieldSurroundEnabled) "ON" else "OFF"}"
        )
        effect.setParameter(
            ViperParams.PARAM_SPK_FIELD_SURROUND_WIDENING,
            FIELD_SURROUND_WIDENING_VALUES.getOrElse(state.spkFieldSurroundWidening) { 0 })
        effect.setParameter(
            ViperParams.PARAM_SPK_FIELD_SURROUND_MID_IMAGE,
            state.spkFieldSurroundMidImage * 10 + 100
        )
        effect.setParameter(
            ViperParams.PARAM_SPK_FIELD_SURROUND_DEPTH,
            state.spkFieldSurroundDepth * 75 + 200
        )

        effect.setParameter(
            ViperParams.PARAM_SPK_SPEAKER_CORRECTION_ENABLE,
            if (state.speakerOptEnabled) 1 else 0
        )
        FileLogger.d("Dispatch", "SpeakerOpt: ${if (state.speakerOptEnabled) "ON" else "OFF"}")

        effect.setParameter(
            ViperParams.PARAM_SPK_DIFF_SURROUND_ENABLE,
            if (state.spkDiffSurroundEnabled) 1 else 0
        )
        FileLogger.d(
            "Dispatch",
            "DiffSurround: ${if (state.spkDiffSurroundEnabled) "ON" else "OFF"}"
        )
        effect.setParameter(
            ViperParams.PARAM_SPK_DIFF_SURROUND_DELAY,
            DIFF_SURROUND_DELAY_VALUES.getOrElse(state.spkDiffSurroundDelay) { 500 })

        effect.setParameter(
            ViperParams.PARAM_SPK_HEADPHONE_SURROUND_ENABLE,
            if (state.spkVheEnabled) 1 else 0
        )
        FileLogger.d("Dispatch", "VHE: ${if (state.spkVheEnabled) "ON" else "OFF"}")
        effect.setParameter(ViperParams.PARAM_SPK_HEADPHONE_SURROUND_STRENGTH, state.spkVheQuality)

        dispatchDynamicSystem(
            effect,
            state.spkDynamicSystemEnabled,
            state.spkDynamicSystemDevice,
            state.spkDynamicSystemStrength,
            ViperParams.PARAM_SPK_DYNAMIC_SYSTEM_ENABLE,
            ViperParams.PARAM_SPK_DYNAMIC_SYSTEM_STRENGTH,
            ViperParams.PARAM_SPK_DYNAMIC_SYSTEM_X_COEFFICIENTS,
            ViperParams.PARAM_SPK_DYNAMIC_SYSTEM_Y_COEFFICIENTS,
            ViperParams.PARAM_SPK_DYNAMIC_SYSTEM_SIDE_GAIN
        )

        effect.setParameter(
            ViperParams.PARAM_SPK_TUBE_SIMULATOR_ENABLE,
            if (state.spkTubeSimulatorEnabled) 1 else 0
        )
        FileLogger.d(
            "Dispatch",
            "TubeSimulator: ${if (state.spkTubeSimulatorEnabled) "ON" else "OFF"}"
        )

        effect.setParameter(ViperParams.PARAM_SPK_BASS_ENABLE, if (state.spkBassEnabled) 1 else 0)
        FileLogger.d("Dispatch", "Bass: ${if (state.spkBassEnabled) "ON" else "OFF"}")
        effect.setParameter(ViperParams.PARAM_SPK_BASS_MODE, state.spkBassMode)
        effect.setParameter(ViperParams.PARAM_SPK_BASS_FREQUENCY, state.spkBassFrequency + 15)
        effect.setParameter(ViperParams.PARAM_SPK_BASS_GAIN, state.spkBassGain * 50 + 50)

        effect.setParameter(
            ViperParams.PARAM_SPK_CLARITY_ENABLE,
            if (state.spkClarityEnabled) 1 else 0
        )
        FileLogger.d("Dispatch", "Clarity: ${if (state.spkClarityEnabled) "ON" else "OFF"}")
        effect.setParameter(ViperParams.PARAM_SPK_CLARITY_MODE, state.spkClarityMode)
        effect.setParameter(ViperParams.PARAM_SPK_CLARITY_GAIN, state.spkClarityGain * 50)

        effect.setParameter(ViperParams.PARAM_SPK_CURE_ENABLE, if (state.spkCureEnabled) 1 else 0)
        FileLogger.d("Dispatch", "Cure: ${if (state.spkCureEnabled) "ON" else "OFF"}")
        effect.setParameter(ViperParams.PARAM_SPK_CURE_STRENGTH, state.spkCureStrength)

        effect.setParameter(
            ViperParams.PARAM_SPK_ANALOGX_ENABLE,
            if (state.spkAnalogxEnabled) 1 else 0
        )
        FileLogger.d("Dispatch", "AnalogX: ${if (state.spkAnalogxEnabled) "ON" else "OFF"}")
        effect.setParameter(ViperParams.PARAM_SPK_ANALOGX_MODE, state.spkAnalogxMode)
    }

    fun dispatchEqBands(effect: ViperEffect, param: Int, bandsString: String) {
        val bands = bandsString.split(";").filter { it.isNotBlank() }
        for ((index, bandStr) in bands.withIndex()) {
            val level = (bandStr.toFloatOrNull() ?: 0f) * 100
            effect.setParameter(param, index, level.toInt())
        }
    }

    private fun dispatchDynamicSystem(
        effect: ViperEffect,
        enabled: Boolean,
        deviceIndex: Int,
        strength: Int,
        paramEnable: Int,
        paramStrength: Int,
        paramXCoeffs: Int,
        paramYCoeffs: Int,
        paramSideGain: Int
    ) {
        effect.setParameter(paramEnable, if (enabled) 1 else 0)
        FileLogger.d(
            "Dispatch",
            "DynamicSystem: ${if (enabled) "ON" else "OFF"} device=$deviceIndex strength=$strength"
        )
        effect.setParameter(paramStrength, strength * 20 + 100)
        val dsCoeffs = DYNAMIC_SYSTEM_DEVICES.getOrElse(deviceIndex) { "100;5600;40;80;50;50" }
        val dsParts = dsCoeffs.split(";").map { it.toIntOrNull() ?: 0 }
        if (dsParts.size >= 6) {
            effect.setParameter(paramXCoeffs, dsParts[0], dsParts[1])
            effect.setParameter(paramYCoeffs, dsParts[2], dsParts[3])
            effect.setParameter(paramSideGain, dsParts[4], dsParts[5])
        }
    }

    suspend fun loadFullStateFromPrefs(repository: ViperRepository): MainUiState {
        val headphone = loadHeadphonePrefs(repository)
        val speaker = loadSpeakerPrefs(repository)
        val spkFet = loadSpeakerFetPrefs(repository)
        return headphone.copy(
            spkMasterEnabled = speaker.spkMasterEnabled,
            spkDdcEnabled = speaker.spkDdcEnabled,
            spkDdcDevice = speaker.spkDdcDevice,
            spkVseEnabled = speaker.spkVseEnabled,
            spkVseStrength = speaker.spkVseStrength,
            spkFieldSurroundEnabled = speaker.spkFieldSurroundEnabled,
            spkFieldSurroundWidening = speaker.spkFieldSurroundWidening,
            spkFieldSurroundMidImage = speaker.spkFieldSurroundMidImage,
            spkDiffSurroundEnabled = speaker.spkDiffSurroundEnabled,
            spkDiffSurroundDelay = speaker.spkDiffSurroundDelay,
            spkVheEnabled = speaker.spkVheEnabled,
            spkVheQuality = speaker.spkVheQuality,
            spkDynamicSystemEnabled = speaker.spkDynamicSystemEnabled,
            spkDynamicSystemDevice = speaker.spkDynamicSystemDevice,
            spkDynamicSystemStrength = speaker.spkDynamicSystemStrength,
            spkTubeSimulatorEnabled = speaker.spkTubeSimulatorEnabled,
            spkBassEnabled = speaker.spkBassEnabled,
            spkBassMode = speaker.spkBassMode,
            spkBassFrequency = speaker.spkBassFrequency,
            spkBassGain = speaker.spkBassGain,
            spkClarityEnabled = speaker.spkClarityEnabled,
            spkClarityMode = speaker.spkClarityMode,
            spkClarityGain = speaker.spkClarityGain,
            spkCureEnabled = speaker.spkCureEnabled,
            spkCureStrength = speaker.spkCureStrength,
            spkAnalogxEnabled = speaker.spkAnalogxEnabled,
            spkAnalogxMode = speaker.spkAnalogxMode,
            spkChannelPan = speaker.spkChannelPan,
            speakerOptEnabled = speaker.speakerOptEnabled,
            spkConvolverEnabled = speaker.spkConvolverEnabled,
            spkConvolverKernel = speaker.spkConvolverKernel,
            spkConvolverCrossChannel = speaker.spkConvolverCrossChannel,
            spkEqBandCount = speaker.spkEqBandCount,
            spkEqEnabled = speaker.spkEqEnabled,
            spkEqPresetId = speaker.spkEqPresetId,
            spkEqBands = speaker.spkEqBands,
            spkReverbEnabled = speaker.spkReverbEnabled,
            spkReverbRoomSize = speaker.spkReverbRoomSize,
            spkReverbWidth = speaker.spkReverbWidth,
            spkReverbDampening = speaker.spkReverbDampening,
            spkReverbWet = speaker.spkReverbWet,
            spkReverbDry = speaker.spkReverbDry,
            spkAgcEnabled = speaker.spkAgcEnabled,
            spkAgcStrength = speaker.spkAgcStrength,
            spkAgcMaxGain = speaker.spkAgcMaxGain,
            spkAgcOutputThreshold = speaker.spkAgcOutputThreshold,
            spkOutputVolume = speaker.spkOutputVolume,
            spkLimiter = speaker.spkLimiter,
            spkFetEnabled = spkFet.spkFetEnabled,
            spkFetThreshold = spkFet.spkFetThreshold,
            spkFetRatio = spkFet.spkFetRatio,
            spkFetAutoKnee = spkFet.spkFetAutoKnee,
            spkFetKnee = spkFet.spkFetKnee,
            spkFetKneeMulti = spkFet.spkFetKneeMulti,
            spkFetAutoGain = spkFet.spkFetAutoGain,
            spkFetGain = spkFet.spkFetGain,
            spkFetAutoAttack = spkFet.spkFetAutoAttack,
            spkFetAttack = spkFet.spkFetAttack,
            spkFetMaxAttack = spkFet.spkFetMaxAttack,
            spkFetAutoRelease = spkFet.spkFetAutoRelease,
            spkFetRelease = spkFet.spkFetRelease,
            spkFetMaxRelease = spkFet.spkFetMaxRelease,
            spkFetCrest = spkFet.spkFetCrest,
            spkFetAdapt = spkFet.spkFetAdapt,
            spkFetNoClip = spkFet.spkFetNoClip
        )
    }

    private suspend fun loadHeadphonePrefs(repository: ViperRepository): MainUiState {
        val masterEnabled =
            repository.getBooleanPreference(ViperRepository.PREF_MASTER_ENABLE).first()
        val fxType =
            repository.getIntPreference(ViperRepository.PREF_FX_TYPE, ViperParams.FX_TYPE_HEADPHONE)
                .first()

        val outputVolume =
            repository.getIntPreference("${ViperParams.PARAM_HP_OUTPUT_VOLUME}", 11).first()
        val channelPan =
            repository.getIntPreference("${ViperParams.PARAM_HP_CHANNEL_PAN}", 0).first()
        val limiter = repository.getIntPreference("${ViperParams.PARAM_HP_LIMITER}", 5).first()

        val agcEnabled =
            repository.getBooleanPreference("${ViperParams.PARAM_HP_AGC_ENABLE}").first()
        val agcStrength =
            repository.getIntPreference("${ViperParams.PARAM_HP_AGC_RATIO}", 0).first()
        val agcMaxGain =
            repository.getIntPreference("${ViperParams.PARAM_HP_AGC_MAX_SCALER}", 3).first()
        val agcOutputThreshold =
            repository.getIntPreference("${ViperParams.PARAM_HP_AGC_VOLUME}", 3).first()

        val fetEnabled =
            repository.getBooleanPreference("${ViperParams.PARAM_HP_FET_COMPRESSOR_ENABLE}").first()
        val fetThreshold =
            repository.getIntPreference("${ViperParams.PARAM_HP_FET_COMPRESSOR_THRESHOLD}", 100)
                .first()
        val fetRatio =
            repository.getIntPreference("${ViperParams.PARAM_HP_FET_COMPRESSOR_RATIO}", 100).first()
        val fetAutoKnee = repository.getBooleanPreference(
            "${ViperParams.PARAM_HP_FET_COMPRESSOR_AUTO_KNEE}",
            true
        ).first()
        val fetKnee =
            repository.getIntPreference("${ViperParams.PARAM_HP_FET_COMPRESSOR_KNEE}", 0).first()
        val fetKneeMulti =
            repository.getIntPreference("${ViperParams.PARAM_HP_FET_COMPRESSOR_KNEE_MULTI}", 0)
                .first()
        val fetAutoGain = repository.getBooleanPreference(
            "${ViperParams.PARAM_HP_FET_COMPRESSOR_AUTO_GAIN}",
            true
        ).first()
        val fetGain =
            repository.getIntPreference("${ViperParams.PARAM_HP_FET_COMPRESSOR_GAIN}", 0).first()
        val fetAutoAttack = repository.getBooleanPreference(
            "${ViperParams.PARAM_HP_FET_COMPRESSOR_AUTO_ATTACK}",
            true
        ).first()
        val fetAttack =
            repository.getIntPreference("${ViperParams.PARAM_HP_FET_COMPRESSOR_ATTACK}", 20).first()
        val fetMaxAttack =
            repository.getIntPreference("${ViperParams.PARAM_HP_FET_COMPRESSOR_MAX_ATTACK}", 80)
                .first()
        val fetAutoRelease = repository.getBooleanPreference(
            "${ViperParams.PARAM_HP_FET_COMPRESSOR_AUTO_RELEASE}",
            true
        ).first()
        val fetRelease =
            repository.getIntPreference("${ViperParams.PARAM_HP_FET_COMPRESSOR_RELEASE}", 50)
                .first()
        val fetMaxRelease =
            repository.getIntPreference("${ViperParams.PARAM_HP_FET_COMPRESSOR_MAX_RELEASE}", 100)
                .first()
        val fetCrest =
            repository.getIntPreference("${ViperParams.PARAM_HP_FET_COMPRESSOR_CREST}", 100).first()
        val fetAdapt =
            repository.getIntPreference("${ViperParams.PARAM_HP_FET_COMPRESSOR_ADAPT}", 50).first()
        val fetNoClip =
            repository.getBooleanPreference("${ViperParams.PARAM_HP_FET_COMPRESSOR_NO_CLIP}", true)
                .first()

        val ddcEnabled =
            repository.getBooleanPreference("${ViperParams.PARAM_HP_DDC_ENABLE}").first()
        val ddcDevice = repository.getStringPreference("ddc_device", "").first()
        val vseEnabled =
            repository.getBooleanPreference("${ViperParams.PARAM_HP_SPECTRUM_EXTENSION_ENABLE}")
                .first()
        val vseStrength =
            repository.getIntPreference("${ViperParams.PARAM_HP_SPECTRUM_EXTENSION_BARK}", 10)
                .first()
        val vseExciter = repository.getIntPreference(
            "${ViperParams.PARAM_HP_SPECTRUM_EXTENSION_BARK_RECONSTRUCT}",
            0
        ).first()
        val eqBandCount = repository.getIntPreference("eq_band_count", 10).first()
        val eqEnabled = repository.getBooleanPreference("${ViperParams.PARAM_HP_EQ_ENABLE}").first()
        val rawEqBands = repository.getStringPreference(
            "${ViperParams.PARAM_HP_EQ_BAND_LEVEL}",
            "0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;"
        ).first()
        val eqBands = ensureBandCount(rawEqBands, eqBandCount)
        val eqPresetId = repository.getIntPreference("eq_preset_id", -1).first()
            .let { if (it < 0) null else it.toLong() }
        val convolverEnabled =
            repository.getBooleanPreference("${ViperParams.PARAM_HP_CONVOLVER_ENABLE}").first()
        val convolverCrossChannel =
            repository.getIntPreference("${ViperParams.PARAM_HP_CONVOLVER_CROSS_CHANNEL}", 0)
                .first()
        val convolverKernel = repository.getStringPreference("convolver_kernel", "").first()
        val fieldSurroundEnabled =
            repository.getBooleanPreference("${ViperParams.PARAM_HP_FIELD_SURROUND_ENABLE}").first()
        val fieldSurroundWidening =
            repository.getIntPreference("${ViperParams.PARAM_HP_FIELD_SURROUND_WIDENING}", 0)
                .first()
        val fieldSurroundMidImage =
            repository.getIntPreference("${ViperParams.PARAM_HP_FIELD_SURROUND_MID_IMAGE}", 5)
                .first()
        val fieldSurroundDepth =
            repository.getIntPreference("${ViperParams.PARAM_HP_FIELD_SURROUND_DEPTH}", 0).first()
        val diffSurroundEnabled =
            repository.getBooleanPreference("${ViperParams.PARAM_HP_DIFF_SURROUND_ENABLE}").first()
        val diffSurroundDelay =
            repository.getIntPreference("${ViperParams.PARAM_HP_DIFF_SURROUND_DELAY}", 4).first()
        val vheEnabled =
            repository.getBooleanPreference("${ViperParams.PARAM_HP_HEADPHONE_SURROUND_ENABLE}")
                .first()
        val vheQuality =
            repository.getIntPreference("${ViperParams.PARAM_HP_HEADPHONE_SURROUND_STRENGTH}", 0)
                .first()
        val reverbEnabled =
            repository.getBooleanPreference("${ViperParams.PARAM_HP_REVERB_ENABLE}").first()
        val reverbRoomSize =
            repository.getIntPreference("${ViperParams.PARAM_HP_REVERB_ROOM_SIZE}", 0).first()
        val reverbWidth =
            repository.getIntPreference("${ViperParams.PARAM_HP_REVERB_ROOM_WIDTH}", 0).first()
        val reverbDampening =
            repository.getIntPreference("${ViperParams.PARAM_HP_REVERB_ROOM_DAMPENING}", 0).first()
        val reverbWet =
            repository.getIntPreference("${ViperParams.PARAM_HP_REVERB_ROOM_WET_SIGNAL}", 0).first()
        val reverbDry =
            repository.getIntPreference("${ViperParams.PARAM_HP_REVERB_ROOM_DRY_SIGNAL}", 50)
                .first()
        val dynamicSystemEnabled =
            repository.getBooleanPreference("${ViperParams.PARAM_HP_DYNAMIC_SYSTEM_ENABLE}").first()
        val dynamicSystemDevice = repository.getIntPreference("dynamic_system_device", 0).first()
        val dynamicSystemStrength =
            repository.getIntPreference("${ViperParams.PARAM_HP_DYNAMIC_SYSTEM_STRENGTH}", 50)
                .first()
        val tubeSimulatorEnabled =
            repository.getBooleanPreference("${ViperParams.PARAM_HP_TUBE_SIMULATOR_ENABLE}").first()
        val bassEnabled =
            repository.getBooleanPreference("${ViperParams.PARAM_HP_BASS_ENABLE}").first()
        val bassMode = repository.getIntPreference("${ViperParams.PARAM_HP_BASS_MODE}", 0).first()
        val bassFrequency =
            repository.getIntPreference("${ViperParams.PARAM_HP_BASS_FREQUENCY}", 55).first()
        val bassGain = repository.getIntPreference("${ViperParams.PARAM_HP_BASS_GAIN}", 0).first()
        val clarityEnabled =
            repository.getBooleanPreference("${ViperParams.PARAM_HP_CLARITY_ENABLE}").first()
        val clarityMode =
            repository.getIntPreference("${ViperParams.PARAM_HP_CLARITY_MODE}", 0).first()
        val clarityGain =
            repository.getIntPreference("${ViperParams.PARAM_HP_CLARITY_GAIN}", 1).first()
        val cureEnabled =
            repository.getBooleanPreference("${ViperParams.PARAM_HP_CURE_ENABLE}").first()
        val cureStrength =
            repository.getIntPreference("${ViperParams.PARAM_HP_CURE_STRENGTH}", 0).first()
        val analogxEnabled =
            repository.getBooleanPreference("${ViperParams.PARAM_HP_ANALOGX_ENABLE}").first()
        val analogxMode =
            repository.getIntPreference("${ViperParams.PARAM_HP_ANALOGX_MODE}", 0).first()

        return MainUiState(
            masterEnabled = masterEnabled,
            fxType = fxType,
            outputVolume = outputVolume,
            channelPan = channelPan,
            limiter = limiter,
            agcEnabled = agcEnabled,
            agcStrength = agcStrength,
            agcMaxGain = agcMaxGain,
            agcOutputThreshold = agcOutputThreshold,
            fetEnabled = fetEnabled,
            fetThreshold = fetThreshold,
            fetRatio = fetRatio,
            fetAutoKnee = fetAutoKnee,
            fetKnee = fetKnee,
            fetKneeMulti = fetKneeMulti,
            fetAutoGain = fetAutoGain,
            fetGain = fetGain,
            fetAutoAttack = fetAutoAttack,
            fetAttack = fetAttack,
            fetMaxAttack = fetMaxAttack,
            fetAutoRelease = fetAutoRelease,
            fetRelease = fetRelease,
            fetMaxRelease = fetMaxRelease,
            fetCrest = fetCrest,
            fetAdapt = fetAdapt,
            fetNoClip = fetNoClip,
            ddcEnabled = ddcEnabled,
            ddcDevice = ddcDevice,
            vseEnabled = vseEnabled,
            vseStrength = vseStrength,
            vseExciter = vseExciter,
            eqBandCount = eqBandCount,
            eqEnabled = eqEnabled,
            eqPresetId = eqPresetId,
            eqBands = eqBands,
            convolverEnabled = convolverEnabled,
            convolverCrossChannel = convolverCrossChannel,
            fieldSurroundEnabled = fieldSurroundEnabled,
            convolverKernel = convolverKernel,
            fieldSurroundWidening = fieldSurroundWidening,
            fieldSurroundMidImage = fieldSurroundMidImage,
            fieldSurroundDepth = fieldSurroundDepth,
            diffSurroundEnabled = diffSurroundEnabled,
            diffSurroundDelay = diffSurroundDelay,
            vheEnabled = vheEnabled,
            vheQuality = vheQuality,
            reverbEnabled = reverbEnabled,
            reverbRoomSize = reverbRoomSize,
            reverbWidth = reverbWidth,
            reverbDampening = reverbDampening,
            reverbWet = reverbWet,
            reverbDry = reverbDry,
            dynamicSystemEnabled = dynamicSystemEnabled,
            dynamicSystemDevice = dynamicSystemDevice,
            dynamicSystemStrength = dynamicSystemStrength,
            tubeSimulatorEnabled = tubeSimulatorEnabled,
            bassEnabled = bassEnabled,
            bassMode = bassMode,
            bassFrequency = bassFrequency,
            bassGain = bassGain,
            clarityEnabled = clarityEnabled,
            clarityMode = clarityMode,
            clarityGain = clarityGain,
            cureEnabled = cureEnabled,
            cureStrength = cureStrength,
            analogxEnabled = analogxEnabled,
            analogxMode = analogxMode
        )
    }

    private suspend fun loadSpeakerPrefs(repository: ViperRepository): MainUiState {
        val spkMasterEnabled = repository.getBooleanPreference("spk_master_enable").first()
        val spkDdcEnabled =
            repository.getBooleanPreference("spk_${ViperParams.PARAM_SPK_DDC_ENABLE}").first()
        val spkDdcDevice = repository.getStringPreference("spk_ddc_device", "").first()
        val spkVseEnabled =
            repository.getBooleanPreference("spk_${ViperParams.PARAM_SPK_SPECTRUM_EXTENSION_ENABLE}")
                .first()
        val spkVseStrength =
            repository.getIntPreference("spk_${ViperParams.PARAM_SPK_SPECTRUM_EXTENSION_BARK}", 10)
                .first()
        val spkVseExciter = repository.getIntPreference(
            "spk_${ViperParams.PARAM_SPK_SPECTRUM_EXTENSION_BARK_RECONSTRUCT}",
            0
        ).first()
        val spkFieldSurroundEnabled =
            repository.getBooleanPreference("spk_${ViperParams.PARAM_SPK_FIELD_SURROUND_ENABLE}")
                .first()
        val spkFieldSurroundWidening =
            repository.getIntPreference("spk_${ViperParams.PARAM_SPK_FIELD_SURROUND_WIDENING}", 0)
                .first()
        val spkFieldSurroundMidImage =
            repository.getIntPreference("spk_${ViperParams.PARAM_SPK_FIELD_SURROUND_MID_IMAGE}", 5)
                .first()
        val spkFieldSurroundDepth =
            repository.getIntPreference("spk_${ViperParams.PARAM_SPK_FIELD_SURROUND_DEPTH}", 0)
                .first()
        val spkDiffSurroundEnabled =
            repository.getBooleanPreference("spk_${ViperParams.PARAM_SPK_DIFF_SURROUND_ENABLE}")
                .first()
        val spkDiffSurroundDelay =
            repository.getIntPreference("spk_${ViperParams.PARAM_SPK_DIFF_SURROUND_DELAY}", 4)
                .first()
        val spkVheEnabled =
            repository.getBooleanPreference("spk_${ViperParams.PARAM_SPK_HEADPHONE_SURROUND_ENABLE}")
                .first()
        val spkVheQuality = repository.getIntPreference(
            "spk_${ViperParams.PARAM_SPK_HEADPHONE_SURROUND_STRENGTH}",
            0
        ).first()
        val spkDynamicSystemEnabled =
            repository.getBooleanPreference("spk_${ViperParams.PARAM_SPK_DYNAMIC_SYSTEM_ENABLE}")
                .first()
        val spkDynamicSystemDevice =
            repository.getIntPreference("spk_dynamic_system_device", 0).first()
        val spkDynamicSystemStrength =
            repository.getIntPreference("spk_${ViperParams.PARAM_SPK_DYNAMIC_SYSTEM_STRENGTH}", 50)
                .first()
        val spkTubeSimulatorEnabled =
            repository.getBooleanPreference("spk_${ViperParams.PARAM_SPK_TUBE_SIMULATOR_ENABLE}")
                .first()
        val spkBassEnabled =
            repository.getBooleanPreference("spk_${ViperParams.PARAM_SPK_BASS_ENABLE}").first()
        val spkBassMode =
            repository.getIntPreference("spk_${ViperParams.PARAM_SPK_BASS_MODE}", 0).first()
        val spkBassFrequency =
            repository.getIntPreference("spk_${ViperParams.PARAM_SPK_BASS_FREQUENCY}", 55).first()
        val spkBassGain =
            repository.getIntPreference("spk_${ViperParams.PARAM_SPK_BASS_GAIN}", 0).first()
        val spkClarityEnabled =
            repository.getBooleanPreference("spk_${ViperParams.PARAM_SPK_CLARITY_ENABLE}").first()
        val spkClarityMode =
            repository.getIntPreference("spk_${ViperParams.PARAM_SPK_CLARITY_MODE}", 0).first()
        val spkClarityGain =
            repository.getIntPreference("spk_${ViperParams.PARAM_SPK_CLARITY_GAIN}", 1).first()
        val spkCureEnabled =
            repository.getBooleanPreference("spk_${ViperParams.PARAM_SPK_CURE_ENABLE}").first()
        val spkCureStrength =
            repository.getIntPreference("spk_${ViperParams.PARAM_SPK_CURE_STRENGTH}", 0).first()
        val spkAnalogxEnabled =
            repository.getBooleanPreference("spk_${ViperParams.PARAM_SPK_ANALOGX_ENABLE}").first()
        val spkAnalogxMode =
            repository.getIntPreference("spk_${ViperParams.PARAM_SPK_ANALOGX_MODE}", 0).first()
        val spkChannelPan =
            repository.getIntPreference("spk_${ViperParams.PARAM_SPK_CHANNEL_PAN}", 0).first()

        val speakerOptEnabled =
            repository.getBooleanPreference("speaker_optimization_enable").first()
        val spkConvolverEnabled =
            repository.getBooleanPreference("${ViperParams.PARAM_SPK_CONVOLVER_ENABLE}").first()
        val spkConvolverCrossChannel =
            repository.getIntPreference("${ViperParams.PARAM_SPK_CONVOLVER_CROSS_CHANNEL}", 0)
                .first()
        val spkConvolverKernel = repository.getStringPreference("spk_convolver_kernel", "").first()
        val spkEqBandCount = repository.getIntPreference("spk_eq_band_count", 10).first()
        val spkEqEnabled =
            repository.getBooleanPreference("${ViperParams.PARAM_SPK_EQ_ENABLE}").first()
        val spkEqPresetId = repository.getIntPreference("spk_eq_preset_id", -1).first()
            .let { if (it < 0) null else it.toLong() }
        val rawSpkEqBands = repository.getStringPreference(
            "${ViperParams.PARAM_SPK_EQ_BAND_LEVEL}",
            "0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;"
        ).first()
        val spkEqBands = ensureBandCount(rawSpkEqBands, spkEqBandCount)
        val spkReverbEnabled =
            repository.getBooleanPreference("${ViperParams.PARAM_SPK_REVERB_ENABLE}").first()
        val spkReverbRoomSize =
            repository.getIntPreference("${ViperParams.PARAM_SPK_REVERB_ROOM_SIZE}", 0).first()
        val spkReverbWidth =
            repository.getIntPreference("${ViperParams.PARAM_SPK_REVERB_ROOM_WIDTH}", 0).first()
        val spkReverbDampening =
            repository.getIntPreference("${ViperParams.PARAM_SPK_REVERB_ROOM_DAMPENING}", 0).first()
        val spkReverbWet =
            repository.getIntPreference("${ViperParams.PARAM_SPK_REVERB_ROOM_WET_SIGNAL}", 0)
                .first()
        val spkReverbDry =
            repository.getIntPreference("${ViperParams.PARAM_SPK_REVERB_ROOM_DRY_SIGNAL}", 50)
                .first()
        val spkAgcEnabled =
            repository.getBooleanPreference("${ViperParams.PARAM_SPK_AGC_ENABLE}").first()
        val spkAgcStrength =
            repository.getIntPreference("${ViperParams.PARAM_SPK_AGC_RATIO}", 0).first()
        val spkAgcMaxGain =
            repository.getIntPreference("${ViperParams.PARAM_SPK_AGC_MAX_SCALER}", 3).first()
        val spkAgcOutputThreshold =
            repository.getIntPreference("${ViperParams.PARAM_SPK_AGC_VOLUME}", 3).first()
        val spkOutputVolume =
            repository.getIntPreference("${ViperParams.PARAM_SPK_OUTPUT_VOLUME}", 11).first()
        val spkLimiter = repository.getIntPreference("${ViperParams.PARAM_SPK_LIMITER}", 5).first()

        return MainUiState(
            spkMasterEnabled = spkMasterEnabled,
            spkDdcEnabled = spkDdcEnabled,
            spkDdcDevice = spkDdcDevice,
            spkVseEnabled = spkVseEnabled,
            spkVseStrength = spkVseStrength,
            spkVseExciter = spkVseExciter,
            spkFieldSurroundEnabled = spkFieldSurroundEnabled,
            spkFieldSurroundWidening = spkFieldSurroundWidening,
            spkFieldSurroundMidImage = spkFieldSurroundMidImage,
            spkFieldSurroundDepth = spkFieldSurroundDepth,
            spkDiffSurroundEnabled = spkDiffSurroundEnabled,
            spkDiffSurroundDelay = spkDiffSurroundDelay,
            spkVheEnabled = spkVheEnabled,
            spkVheQuality = spkVheQuality,
            spkDynamicSystemEnabled = spkDynamicSystemEnabled,
            spkDynamicSystemDevice = spkDynamicSystemDevice,
            spkDynamicSystemStrength = spkDynamicSystemStrength,
            spkTubeSimulatorEnabled = spkTubeSimulatorEnabled,
            spkBassEnabled = spkBassEnabled,
            spkBassMode = spkBassMode,
            spkBassFrequency = spkBassFrequency,
            spkBassGain = spkBassGain,
            spkClarityEnabled = spkClarityEnabled,
            spkClarityMode = spkClarityMode,
            spkClarityGain = spkClarityGain,
            spkCureEnabled = spkCureEnabled,
            spkCureStrength = spkCureStrength,
            spkAnalogxEnabled = spkAnalogxEnabled,
            spkAnalogxMode = spkAnalogxMode,
            spkChannelPan = spkChannelPan,
            speakerOptEnabled = speakerOptEnabled,
            spkConvolverEnabled = spkConvolverEnabled,
            spkConvolverKernel = spkConvolverKernel,
            spkConvolverCrossChannel = spkConvolverCrossChannel,
            spkEqBandCount = spkEqBandCount,
            spkEqEnabled = spkEqEnabled,
            spkEqPresetId = spkEqPresetId,
            spkEqBands = spkEqBands,
            spkReverbEnabled = spkReverbEnabled,
            spkReverbRoomSize = spkReverbRoomSize,
            spkReverbWidth = spkReverbWidth,
            spkReverbDampening = spkReverbDampening,
            spkReverbWet = spkReverbWet,
            spkReverbDry = spkReverbDry,
            spkAgcEnabled = spkAgcEnabled,
            spkAgcStrength = spkAgcStrength,
            spkAgcMaxGain = spkAgcMaxGain,
            spkAgcOutputThreshold = spkAgcOutputThreshold,
            spkOutputVolume = spkOutputVolume,
            spkLimiter = spkLimiter
        )
    }

    private suspend fun loadSpeakerFetPrefs(repository: ViperRepository): MainUiState {
        val spkFetEnabled =
            repository.getBooleanPreference("${ViperParams.PARAM_SPK_FET_COMPRESSOR_ENABLE}")
                .first()
        val spkFetThreshold =
            repository.getIntPreference("${ViperParams.PARAM_SPK_FET_COMPRESSOR_THRESHOLD}", 100)
                .first()
        val spkFetRatio =
            repository.getIntPreference("${ViperParams.PARAM_SPK_FET_COMPRESSOR_RATIO}", 100)
                .first()
        val spkFetAutoKnee = repository.getBooleanPreference(
            "${ViperParams.PARAM_SPK_FET_COMPRESSOR_AUTO_KNEE}",
            true
        ).first()
        val spkFetKnee =
            repository.getIntPreference("${ViperParams.PARAM_SPK_FET_COMPRESSOR_KNEE}", 0).first()
        val spkFetKneeMulti =
            repository.getIntPreference("${ViperParams.PARAM_SPK_FET_COMPRESSOR_KNEE_MULTI}", 0)
                .first()
        val spkFetAutoGain = repository.getBooleanPreference(
            "${ViperParams.PARAM_SPK_FET_COMPRESSOR_AUTO_GAIN}",
            true
        ).first()
        val spkFetGain =
            repository.getIntPreference("${ViperParams.PARAM_SPK_FET_COMPRESSOR_GAIN}", 0).first()
        val spkFetAutoAttack = repository.getBooleanPreference(
            "${ViperParams.PARAM_SPK_FET_COMPRESSOR_AUTO_ATTACK}",
            true
        ).first()
        val spkFetAttack =
            repository.getIntPreference("${ViperParams.PARAM_SPK_FET_COMPRESSOR_ATTACK}", 20)
                .first()
        val spkFetMaxAttack =
            repository.getIntPreference("${ViperParams.PARAM_SPK_FET_COMPRESSOR_MAX_ATTACK}", 80)
                .first()
        val spkFetAutoRelease = repository.getBooleanPreference(
            "${ViperParams.PARAM_SPK_FET_COMPRESSOR_AUTO_RELEASE}",
            true
        ).first()
        val spkFetRelease =
            repository.getIntPreference("${ViperParams.PARAM_SPK_FET_COMPRESSOR_RELEASE}", 50)
                .first()
        val spkFetMaxRelease =
            repository.getIntPreference("${ViperParams.PARAM_SPK_FET_COMPRESSOR_MAX_RELEASE}", 100)
                .first()
        val spkFetCrest =
            repository.getIntPreference("${ViperParams.PARAM_SPK_FET_COMPRESSOR_CREST}", 100)
                .first()
        val spkFetAdapt =
            repository.getIntPreference("${ViperParams.PARAM_SPK_FET_COMPRESSOR_ADAPT}", 50).first()
        val spkFetNoClip =
            repository.getBooleanPreference("${ViperParams.PARAM_SPK_FET_COMPRESSOR_NO_CLIP}", true)
                .first()

        return MainUiState(
            spkFetEnabled = spkFetEnabled,
            spkFetThreshold = spkFetThreshold,
            spkFetRatio = spkFetRatio,
            spkFetAutoKnee = spkFetAutoKnee,
            spkFetKnee = spkFetKnee,
            spkFetKneeMulti = spkFetKneeMulti,
            spkFetAutoGain = spkFetAutoGain,
            spkFetGain = spkFetGain,
            spkFetAutoAttack = spkFetAutoAttack,
            spkFetAttack = spkFetAttack,
            spkFetMaxAttack = spkFetMaxAttack,
            spkFetAutoRelease = spkFetAutoRelease,
            spkFetRelease = spkFetRelease,
            spkFetMaxRelease = spkFetMaxRelease,
            spkFetCrest = spkFetCrest,
            spkFetAdapt = spkFetAdapt,
            spkFetNoClip = spkFetNoClip
        )
    }
}
