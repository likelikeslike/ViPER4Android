package com.llsl.viper4android.widget

import android.view.HapticFeedbackConstants
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.size

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlin.math.abs

@Composable
fun PresetWidgetPreview(
    chosen: SnapshotStateList<Long>,
    names: Map<Long, String>,
    vertical: Boolean,
    gapDp: Int,
    outerInsetDp: Int,
    cornerRadiusDp: Int,
    borderWidthDp: Int,
    textColor: Int,
    textSizeSp: Int,
    textWeight: String,
    activeTextColor: Int,
    widgetColor: Int,
    activeColor: Int,
    borderColor: Int,
    activeBorderColor: Int,
    identityMode: String,
    identityText: String,
) {
    val entries = chosen.toList()
    if (vertical) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(outerInsetDp.dp),
            verticalArrangement = Arrangement.spacedBy(gapDp.dp),
        ) {
            entries.forEachIndexed { index, id ->
                key(id) {
                    PreviewPresetButton(
                        id = id,
                        label = names[id] ?: "Missing preset",
                        chosen = chosen,
                        vertical = true,
                        gapDp = gapDp,
                        textColor = if (index == 0) activeTextColor else textColor,
                        textSizeSp = textSizeSp,
                        textWeight = textWeight,
                        backgroundColor = if (index == 0) activeColor else widgetColor,
                        borderColor = if (index == 0) activeBorderColor else borderColor,
                        cornerRadiusDp = cornerRadiusDp,
                        borderWidthDp = borderWidthDp,
                        identity = if (identityMode == "custom") identityText.trim() else "ViPER",
                        showViperIcon = identityMode != "custom",
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth().padding(outerInsetDp.dp),
            horizontalArrangement = Arrangement.spacedBy(gapDp.dp),
        ) {
            entries.forEachIndexed { index, id ->
                key(id) {
                    PreviewPresetButton(
                        id = id,
                        label = names[id] ?: "Missing preset",
                        chosen = chosen,
                        vertical = false,
                        gapDp = gapDp,
                        textColor = if (index == 0) activeTextColor else textColor,
                        textSizeSp = textSizeSp,
                        textWeight = textWeight,
                        backgroundColor = if (index == 0) activeColor else widgetColor,
                        borderColor = if (index == 0) activeBorderColor else borderColor,
                        cornerRadiusDp = cornerRadiusDp,
                        borderWidthDp = borderWidthDp,
                        identity = if (identityMode == "custom") identityText.trim() else "ViPER",
                        showViperIcon = identityMode != "custom",
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun PreviewPresetButton(
    id: Long,
    label: String,
    chosen: SnapshotStateList<Long>,
    vertical: Boolean,
    gapDp: Int,
    textColor: Int,
    textSizeSp: Int,
    textWeight: String,
    backgroundColor: Int,
    borderColor: Int,
    cornerRadiusDp: Int,
    borderWidthDp: Int,
    identity: String,
    showViperIcon: Boolean,
    modifier: Modifier,
) {
    var dimensionPx by remember { mutableIntStateOf(1) }
    var dragPx by remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    val spacingPx = with(LocalDensity.current) { gapDp.dp.toPx() }
    val view = LocalView.current

    Box(
        modifier =
            modifier
                .heightIn(min = 56.dp)
                .onSizeChanged {
                    dimensionPx = if (vertical) it.height else it.width
                }.zIndex(if (dragging) 1f else 0f)
                .graphicsLayer {
                    if (dragging) {
                        if (vertical) translationY = dragPx else translationX = dragPx
                    }
                }.pointerInput(id, vertical, gapDp) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = {
                            dragging = true
                            dragPx = 0f
                            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        },
                        onDragEnd = {
                            dragging = false
                            dragPx = 0f
                        },
                        onDragCancel = {
                            dragging = false
                            dragPx = 0f
                        },
                        onDrag = { change, delta ->
                            change.consume()
                            dragPx += if (vertical) delta.y else delta.x
                            val extent = dimensionPx.coerceAtLeast(1) + spacingPx
                            if (abs(dragPx) >= extent * 0.55f) {
                                val from = chosen.indexOf(id)
                                val step = if (dragPx > 0) 1 else -1
                                val to = from + step
                                if (from >= 0 && to in chosen.indices) {
                                    chosen.removeAt(from)
                                    chosen.add(to, id)
                                    dragPx -= step * extent
                                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                } else {
                                    dragPx = 0f
                                }
                            }
                        },
                    )
                }.border(
                    borderWidthDp.dp,
                    Color(borderColor),
                    RoundedCornerShape(cornerRadiusDp.dp),
                ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier.matchParentSize().padding(borderWidthDp.dp).background(
                Color(backgroundColor),
                RoundedCornerShape((cornerRadiusDp - borderWidthDp).coerceAtLeast(0).dp),
            ),
        )
        Text(
            label,
            modifier = Modifier.padding(vertical = 4.dp, horizontal = 4.dp),
            color = Color(textColor),
            fontSize = textSizeSp.sp,
            fontWeight = when (textWeight) {
                "bold" -> FontWeight.Bold
                else -> FontWeight.Normal
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (identity.isNotBlank()) {
            Row(
                modifier = Modifier.align(Alignment.TopStart).padding(start = 6.dp, top = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                if (showViperIcon) {
                    Icon(
                        imageVector = Icons.Default.GraphicEq,
                        contentDescription = null,
                        modifier = Modifier.size(10.dp),
                        tint = Color(textColor),
                    )
                }
                Text(
                    identity,
                    color = Color(textColor),
                    fontSize = 9.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
