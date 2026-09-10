package com.llsl.viper4android.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Dialog

@Composable
fun InputDialog(
    title: String,
    initialValue: String,
    confirmLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    body: String? = null,
    dismissLabel: String = "Cancel",
    placeholder: String = "",
) {
    var value by remember { mutableStateOf(initialValue) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(UiDimens.DialogCornerRadius),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = UiDimens.Compact,
        ) {
            Column(modifier = Modifier.padding(UiDimens.IconLarge)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = null)
                    }
                }
                body?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = UiDimens.Medium),
                    )
                }
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(top = UiDimens.Standard, bottom = UiDimens.IconLarge),
                    placeholder = { Text(placeholder) },
                    singleLine = true,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    FilledTonalButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(dismissLabel)
                    }
                    Spacer(modifier = Modifier.width(UiDimens.Medium))
                    Button(
                        onClick = { if (value.isNotBlank()) onConfirm(value.trim()) },
                        enabled = value.isNotBlank(),
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(confirmLabel)
                    }
                }
            }
        }
    }
}
