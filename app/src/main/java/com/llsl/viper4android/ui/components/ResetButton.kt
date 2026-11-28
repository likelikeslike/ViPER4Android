package com.llsl.viper4android.ui.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.llsl.viper4android.R

@Composable
fun ResetButton(
    label: String,
    confirmation: String,
    deviceId: String,
    onReset: () -> Unit,
) {
    var showConfirmation by remember(deviceId) { mutableStateOf(false) }
    TextButton(onClick = { showConfirmation = true }, enabled = deviceId.isNotEmpty()) {
        Icon(Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.size(UiDimens.Standard))
        Spacer(modifier = Modifier.width(UiDimens.XSmall))
        Text(label)
    }
    if (showConfirmation) {
        ConfirmDialog(
            title = label,
            body = confirmation,
            confirmLabel = stringResource(R.string.action_reset),
            destructive = true,
            onConfirm = {
                showConfirmation = false
                onReset()
            },
            onDismiss = { showConfirmation = false },
        )
    }
}
