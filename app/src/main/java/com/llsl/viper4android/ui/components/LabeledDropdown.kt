package com.llsl.viper4android.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.llsl.viper4android.R

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun LabeledDropdown(
    label: String,
    selectedValue: String,
    options: List<String>,
    onOptionSelected: (Int, String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onDeleteOption: ((Int, String) -> Unit)? = null,
    isOptionDeletable: (Int, String) -> Boolean = { _, _ -> onDeleteOption != null },
) {
    var expanded by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<Pair<Int, String>?>(null) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (enabled) expanded = it },
        modifier =
            modifier
                .fillMaxWidth()
                .padding(vertical = UiDimens.XSmall),
    ) {
        OutlinedTextField(
            value = selectedValue,
            onValueChange = {},
            readOnly = true,
            label = { Text(label, style = MaterialTheme.typography.bodySmall) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier =
                Modifier
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                    .fillMaxWidth(),
            enabled = enabled,
            singleLine = true,
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEachIndexed { index, option ->
                val canDelete = onDeleteOption != null && isOptionDeletable(index, option)
                if (canDelete) {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = UiDimens.SwitchSlotHeight)
                                .combinedClickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = ripple(),
                                    onClick = {
                                        onOptionSelected(index, option)
                                        expanded = false
                                    },
                                    onLongClick = {
                                        deleteTarget = index to option
                                        expanded = false
                                    },
                                ).padding(horizontal = UiDimens.Standard),
                        contentAlignment = Alignment.CenterStart,
                    ) { Text(option, style = MaterialTheme.typography.bodyLarge) }
                } else {
                    DropdownMenuItem(
                        text = { Text(option) },
                        onClick = {
                            onOptionSelected(index, option)
                            expanded = false
                        },
                    )
                }
            }
        }
    }

    deleteTarget?.let { (index, name) ->
        ConfirmDialog(
            title = stringResource(R.string.delete_file_title),
            body = stringResource(R.string.delete_file_message, name),
            confirmLabel = stringResource(R.string.action_delete),
            destructive = true,
            onConfirm = {
                onDeleteOption?.invoke(index, name)
                deleteTarget = null
            },
            onDismiss = { deleteTarget = null },
            dismissLabel = stringResource(R.string.action_cancel),
        )
    }
}
