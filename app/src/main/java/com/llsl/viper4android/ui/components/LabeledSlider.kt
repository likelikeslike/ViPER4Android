package com.llsl.viper4android.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.llsl.viper4android.R
import java.util.Locale
import kotlin.math.roundToInt

data class SliderEdit(
    val displayValue: Double,
    val displayRange: ClosedFloatingPointRange<Double>,
    val decimals: Int,
    val onCommit: (Double) -> Unit,
    val unit: String = "",
)

@Composable
fun LabeledSlider(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    steps: Int = 0,
    enabled: Boolean = true,
    valueLabel: String? = null,
    edit: SliderEdit? = null,
) {
    var showEditDialog by remember { mutableStateOf(false) }
    val canEdit = enabled && edit != null

    Column(modifier = modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            val valueModifier =
                if (canEdit) {
                    Modifier.clickable { showEditDialog = true }
                } else {
                    Modifier
                }.padding(horizontal = 8.dp, vertical = 4.dp)
            Text(
                text = valueLabel ?: value.roundToInt().toString(),
                style = MaterialTheme.typography.bodyMedium,
                color =
                    if (canEdit) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                modifier = valueModifier,
            )
        }
        val hideTicks = steps == 0 || steps >= 10
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
            colors =
                if (hideTicks) {
                    SliderDefaults.colors(
                        activeTickColor = Color.Transparent,
                        inactiveTickColor = Color.Transparent,
                        disabledActiveTickColor = Color.Transparent,
                        disabledInactiveTickColor = Color.Transparent,
                    )
                } else {
                    SliderDefaults.colors()
                },
        )
    }

    if (showEditDialog && edit != null) {
        NumberInputDialog(
            label = label,
            edit = edit,
            onDismiss = { showEditDialog = false },
        )
    }
}

@Composable
fun NumberInputDialog(
    label: String,
    edit: SliderEdit,
    onDismiss: () -> Unit,
) {
    val min = edit.displayRange.start
    val max = edit.displayRange.endInclusive

    fun fmt(v: Double): String =
        if (edit.decimals <= 0) {
            v.roundToInt().toString()
        } else {
            String.format(Locale.US, "%.${edit.decimals}f", v)
        }

    var text by remember { mutableStateOf(fmt(edit.displayValue)) }
    val parsed = text.trim().toDoubleOrNull()
    val isValid = parsed != null

    val rangeHint =
        if (edit.unit.isNotEmpty()) {
            stringResource(
                R.string.dialog_value_range_unit,
                fmt(min),
                fmt(max),
                edit.unit,
            )
        } else {
            stringResource(R.string.dialog_value_range, fmt(min), fmt(max))
        }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(label) },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    isError = !isValid,
                    suffix = if (edit.unit.isNotEmpty()) ({ Text(edit.unit) }) else null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    supportingText = { Text(rangeHint) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val clamped = parsed!!.coerceIn(min, max)
                    edit.onCommit(clamped)
                    onDismiss()
                },
                enabled = isValid,
            ) { Text(stringResource(android.R.string.ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
