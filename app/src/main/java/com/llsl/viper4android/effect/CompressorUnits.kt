package com.llsl.viper4android.effect

import kotlin.math.ln
import kotlin.math.pow

private val LogGainPerDb = (ln(10.0) / 20.0).toFloat()

fun compressorDbToRaw(db: Float): Float = db * LogGainPerDb

fun compressorRawToDb(raw: Float): Float = raw / LogGainPerDb

fun compressorMsToSeconds(ms: Float): Float = ms / 1000.0f

fun compressorSecondsToMs(seconds: Float): Float = seconds * 1000.0f

fun compressorRatioToRaw(ratio: Float): Float = -ratio

fun compressorRawToRatio(raw: Float): Float = -raw

fun compressorAdaptAmountToSeconds(amount: Float): Float = 4.0.pow(amount.toDouble()).toFloat()
