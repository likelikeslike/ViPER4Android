package com.llsl.viper4android.ui.screens.status

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.llsl.viper4android.R
import com.llsl.viper4android.ui.screens.main.DriverStatus

@Composable
fun DriverStatusDialog(
    driverStatus: DriverStatus,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.menu_driver_status)) },
        text = {
            if (!driverStatus.installed) {
                Text(
                    text = stringResource(R.string.driver_not_found),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.error
                )
            } else {
                Column {
                    StatusRow(
                        label = stringResource(R.string.driver_version_code),
                        value = driverStatus.versionCode.toString()
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    StatusRow(
                        label = stringResource(R.string.driver_version_name),
                        value = driverStatus.versionName
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    StatusRow(
                        label = stringResource(R.string.driver_architecture),
                        value = driverStatus.architecture
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    StatusRow(
                        label = stringResource(R.string.driver_streaming),
                        value = if (driverStatus.streaming)
                            stringResource(R.string.status_active)
                        else
                            stringResource(R.string.status_inactive)
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    StatusRow(
                        label = stringResource(R.string.driver_sampling_rate),
                        value = if (driverStatus.samplingRate > 0)
                            "${driverStatus.samplingRate} Hz"
                        else
                            stringResource(R.string.status_unknown)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_close))
            }
        }
    )
}

@Composable
private fun StatusRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
