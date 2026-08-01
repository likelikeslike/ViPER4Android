package com.llsl.viper4android.ui.components

import android.graphics.Color.argb
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.llsl.viper4android.R
import com.llsl.viper4android.data.model.EqPreset
import com.llsl.viper4android.viper.ViperDispatcher
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

private const val DB_MIN = -12f
private const val DB_MAX = 12f
private val DB_GRID_LINES = listOf(-12f, -6f, 0f, 6f, 12f)

@Composable
fun EqCurveGraph(
    bands: List<Float>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    bandCount: Int = 10,
) {
    val freqLabels = ViperDispatcher.eqGraphLabelsForCount(bandCount)
    val primary = MaterialTheme.colorScheme.primary
    val surfaceDark = MaterialTheme.colorScheme.surfaceContainerHighest
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val density = LocalDensity.current

    val graphModifier =
        if (modifier == Modifier) {
            Modifier
                .fillMaxWidth()
                .height(180.dp)
        } else {
            modifier.fillMaxWidth()
        }

    Surface(
        modifier =
            graphModifier
                .clip(RoundedCornerShape(12.dp))
                .clickable { onClick() },
        color = surfaceDark,
        shape = RoundedCornerShape(12.dp),
    ) {
        Canvas(modifier = Modifier.fillMaxWidth()) {
            val paddingLeft = 28.dp.toPx()
            val paddingRight = 16.dp.toPx()
            val paddingTop = 24.dp.toPx()
            val paddingBottom = 28.dp.toPx()

            val graphWidth = size.width - paddingLeft - paddingRight
            val graphHeight = size.height - paddingTop - paddingBottom

            val gridPaint =
                Paint().apply {
                    color = argb(25, 255, 255, 255)
                    strokeWidth = 1f
                    style = Paint.Style.STROKE
                }

            val freqTextPaint =
                Paint().apply {
                    color = onSurfaceVariant.hashCode()
                    textSize = with(density) { (if (bandCount > 15) 7 else 9).dp.toPx() }
                    textAlign = Paint.Align.CENTER
                    isAntiAlias = true
                    typeface = Typeface.DEFAULT
                }

            val dbLabelPaint =
                Paint().apply {
                    color = argb(120, 255, 255, 255)
                    textSize = with(density) { 8.dp.toPx() }
                    textAlign = Paint.Align.RIGHT
                    isAntiAlias = true
                    typeface = Typeface.DEFAULT
                }

            val valuePaint =
                Paint().apply {
                    color = primary.hashCode()
                    textSize = with(density) { 8.dp.toPx() }
                    textAlign = Paint.Align.CENTER
                    isAntiAlias = true
                    typeface = Typeface.DEFAULT_BOLD
                }

            for (db in DB_GRID_LINES) {
                val y = paddingTop + graphHeight * (1f - (db - DB_MIN) / (DB_MAX - DB_MIN))
                drawContext.canvas.nativeCanvas.drawLine(
                    paddingLeft,
                    y,
                    size.width - paddingRight,
                    y,
                    gridPaint,
                )
                val label =
                    when {
                        db > 0 -> "+${db.toInt()}"
                        db == 0f -> "0"
                        else -> "${db.toInt()}"
                    }
                drawContext.canvas.nativeCanvas.drawText(
                    label,
                    paddingLeft - 4.dp.toPx(),
                    y + with(density) { 3.dp.toPx() },
                    dbLabelPaint,
                )
            }

            if (bands.size < bandCount) return@Canvas

            val points =
                bands.take(bandCount).mapIndexed { i, db ->
                    val x = paddingLeft + graphWidth * i / (bandCount - 1).toFloat()
                    val y =
                        paddingTop + graphHeight * (
                            1f - (
                                db.coerceIn(
                                    DB_MIN,
                                    DB_MAX,
                                ) - DB_MIN
                            ) / (DB_MAX - DB_MIN)
                        )
                    Offset(x, y)
                }

            val curvePath = buildSplinePath(points)

            val fillPath =
                Path().apply {
                    addPath(curvePath)
                    lineTo(points.last().x, paddingTop + graphHeight)
                    lineTo(points.first().x, paddingTop + graphHeight)
                    close()
                }

            drawPath(
                path = fillPath,
                brush =
                    Brush.verticalGradient(
                        colors = listOf(primary.copy(alpha = 0.35f), Color.Transparent),
                        startY = paddingTop,
                        endY = paddingTop + graphHeight,
                    ),
            )

            drawPath(
                path = curvePath,
                color = primary,
                style = Stroke(width = 2.dp.toPx()),
            )

            val labelStep =
                when (bandCount) {
                    31 -> 5
                    25 -> 4
                    15 -> 2
                    else -> 1
                }
            val showValues = bandCount <= 15

            points.forEachIndexed { i, pt ->
                drawCircle(
                    color = primary,
                    radius = (if (bandCount > 15) 2 else 3).dp.toPx(),
                    center = pt,
                )

                if (i % labelStep == 0) {
                    drawContext.canvas.nativeCanvas.drawText(
                        freqLabels.getOrElse(i) { "" },
                        pt.x,
                        paddingTop + graphHeight + with(density) { 14.dp.toPx() },
                        freqTextPaint,
                    )
                }

                if (showValues) {
                    val valText = "%.1f".format(bands[i])
                    drawContext.canvas.nativeCanvas.drawText(
                        valText,
                        pt.x,
                        pt.y - with(density) { 6.dp.toPx() },
                        valuePaint,
                    )
                }
            }
        }
    }
}

private fun buildSplinePath(points: List<Offset>): Path {
    val path = Path()
    val n = points.size
    if (n == 0) return path
    path.moveTo(points[0].x, points[0].y)
    if (n == 1) return path
    if (n == 2) {
        path.lineTo(points[1].x, points[1].y)
        return path
    }

    val tension = 0.3f
    val damping = 0.15f

    for (i in 0 until n - 1) {
        val prev = points[max(0, i - 1)]
        val curr = points[i]
        val next = points[i + 1]
        val afterNext = points[min(n - 1, i + 2)]

        var t1 = tension
        val isLocalMax = curr.y <= prev.y && curr.y <= next.y
        val isLocalMin = curr.y >= prev.y && curr.y >= next.y
        if (isLocalMax || isLocalMin) t1 = damping

        var t2 = tension
        val isNextLocalMax = next.y <= curr.y && next.y <= afterNext.y
        val isNextLocalMin = next.y >= curr.y && next.y >= afterNext.y
        if (isNextLocalMax || isNextLocalMin) t2 = damping

        val cp1x = curr.x + (next.x - prev.x) * t1
        val cp1y = curr.y + (next.y - prev.y) * t1
        val cp2x = next.x - (afterNext.x - curr.x) * t2
        val cp2y = next.y - (afterNext.y - curr.y) * t2

        path.cubicTo(cp1x, cp1y, cp2x, cp2y, next.x, next.y)
    }
    return path
}

@Composable
fun EqEditDialog(
    bands: List<Float>,
    onBandsChange: (List<Double>) -> Unit,
    presetId: Long?,
    presets: List<EqPreset>,
    onPresetSelect: (Long) -> Unit,
    onPresetAdd: (String) -> Unit,
    onPresetDelete: (Long) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
    bandCount: Int = 10,
) {
    val localBands =
        remember(bandCount) {
            mutableStateListOf<Float>().apply { addAll(bands.take(bandCount)) }
        }

    LaunchedEffect(bands) {
        val incoming = bands.take(bandCount)
        if (incoming != localBands.toList()) {
            localBands.clear()
            localBands.addAll(incoming)
        }
    }

    var showSaveDialog by remember { mutableStateOf(false) }
    var presetNameInput by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.section_equalizer),
                style = MaterialTheme.typography.titleMedium,
            )
        },
        text = {
            Column {
                EqCurveGraph(
                    bands = localBands.toList(),
                    onClick = {},
                    modifier = Modifier.height(160.dp),
                    bandCount = bandCount,
                )

                Spacer(modifier = Modifier.height(12.dp))

                val presetNames = presets.map { resolvePresetName(it) }
                val selectedPresetName =
                    presets.find { it.id == presetId }?.let { resolvePresetName(it) }
                        ?: stringResource(R.string.label_custom)

                LabeledDropdown(
                    label = stringResource(R.string.label_preset),
                    selectedValue = selectedPresetName,
                    options = presetNames,
                    onOptionSelected = { index, _ -> onPresetSelect(presets[index].id) },
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TextButton(onClick = { showSaveDialog = true }) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.action_save))
                    }
                    TextButton(
                        onClick = { presetId?.let { onPresetDelete(it) } },
                        enabled = presetId != null,
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.action_delete))
                    }
                    TextButton(onClick = {
                        for (i in localBands.indices) {
                            localBands[i] = 0f
                        }
                        onReset()
                    }) {
                        Icon(
                            Icons.Default.RestartAlt,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.action_reset))
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                val bandLabels = ViperDispatcher.eqBandLabelsForCount(bandCount)

                Column(
                    modifier = Modifier.heightIn(max = 260.dp).verticalScroll(rememberScrollState()),
                ) {
                    bandLabels.forEachIndexed { index, label ->
                        if (index < localBands.size) {
                            var showBandEdit by remember { mutableStateOf(false) }

                            val applyBandChange = { newVal: Float ->
                                localBands[index] = newVal.coerceIn(DB_MIN, DB_MAX)
                                val list =
                                    localBands.map { String.format(Locale.US, "%.1f", it).toDouble() }
                                onBandsChange(list)
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth().height(52.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.width(48.dp),
                                )
                                Slider(
                                    value = localBands[index],
                                    onValueChange = { applyBandChange(it) },
                                    valueRange = DB_MIN..DB_MAX,
                                    modifier = Modifier.weight(1f),
                                    colors =
                                        SliderDefaults.colors(
                                            activeTickColor = Color.Transparent,
                                            inactiveTickColor = Color.Transparent,
                                        ),
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = "${"%.1f".format(localBands[index])}dB",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.width(56.dp).clickable { showBandEdit = true },
                                    maxLines = 1,
                                )
                            }

                            if (showBandEdit) {
                                NumberInputDialog(
                                    label = label,
                                    edit =
                                        SliderEdit(
                                            displayValue = localBands[index].toDouble(),
                                            displayRange = DB_MIN.toDouble()..DB_MAX.toDouble(),
                                            decimals = 1,
                                            unit = "dB",
                                            onCommit = { applyBandChange(it.toFloat()) },
                                        ),
                                    onDismiss = { showBandEdit = false },
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.ok))
            }
        },
    )

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
