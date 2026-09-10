package com.llsl.viper4android.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp

private val DialogCardCornerRadius = UiDimens.Standard
private val DialogButtonSpacing = UiDimens.Medium
private val DialogDividerPadding = UiDimens.Standard
private val DialogEmptyVerticalPadding = UiDimens.IconLarge
private val DialogIconActionPadding = UiDimens.Medium
private val DialogIconActionSpacing = UiDimens.XSmall
private val DialogIndicatorSize = UiDimens.Medium
private val DialogLeadingIconSize = UiDimens.IconLarge
private val DialogListMaxHeight = UiDimens.DialogListMaxHeight
private val DialogRowHorizontalPadding = UiDimens.Standard
private val DialogListItemVerticalPadding = UiDimens.Medium
private val DialogRowVerticalPadding = UiDimens.Large
private val DialogRowValueSpacing = UiDimens.Standard
private val DialogRowIconSpacing = UiDimens.Large
private val DialogSmallIconSize = UiDimens.IconMedium
private val DialogSectionStartPadding = UiDimens.Medium
private val DialogSectionTopPadding = UiDimens.Standard
private val DialogSectionBottomPadding = UiDimens.Compact

@Composable
fun DialogSectionLabel(label: String) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier =
            Modifier.padding(
                start = DialogSectionStartPadding,
                top = DialogSectionTopPadding,
                bottom = DialogSectionBottomPadding,
            ),
    )
}

@Composable
fun DialogCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(DialogCardCornerRadius),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
    ) {
        Column { content() }
    }
}

@Composable
fun DialogListCard(
    maxHeight: Dp = DialogListMaxHeight,
    content: LazyListScope.() -> Unit,
) {
    DialogCard {
        DialogList(maxHeight = maxHeight, content = content)
    }
}

@Composable
fun DialogList(
    maxHeight: Dp = DialogListMaxHeight,
    content: LazyListScope.() -> Unit,
) {
    LazyColumn(
        modifier = Modifier.heightIn(max = maxHeight),
        content = content,
    )
}

@Composable
fun DialogEmptyState(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = DialogEmptyVerticalPadding),
    )
}

@Composable
fun DialogButtonRow(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(DialogButtonSpacing),
        content = content,
    )
}

@Composable
fun DialogIconActionRow(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = DialogListItemVerticalPadding),
        horizontalArrangement = Arrangement.SpaceEvenly,
        content = content,
    )
}

@Composable
fun DialogListItemRow(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(horizontal = DialogRowHorizontalPadding, vertical = DialogListItemVerticalPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DialogRowIconSpacing),
        content = content,
    )
}

@Composable
fun RowDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = DialogDividerPadding, end = DialogDividerPadding),
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}

@Composable
fun ToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    subtitle: String? = null,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = DialogRowHorizontalPadding, vertical = DialogRowVerticalPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            subtitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(modifier = Modifier.width(DialogRowValueSpacing))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
fun NavRow(
    label: String,
    onClick: () -> Unit,
    subtitle: String? = null,
    leadingIcon: ImageVector? = null,
    statusColor: Color? = null,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = DialogRowHorizontalPadding, vertical = DialogRowVerticalPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leadingIcon?.let {
            Icon(
                imageVector = it,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(DialogLeadingIconSize),
            )
            Spacer(modifier = Modifier.width(DialogRowIconSpacing))
        }
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                statusColor?.let {
                    Canvas(modifier = Modifier.size(DialogIndicatorSize)) {
                        drawCircle(it)
                    }
                    Spacer(modifier = Modifier.width(DialogSectionBottomPadding))
                }
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f, fill = false),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            subtitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun ActionRow(
    label: String,
    subtitle: String,
    onClick: () -> Unit,
    icon: ImageVector = Icons.Default.Download,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = DialogRowHorizontalPadding, vertical = DialogRowVerticalPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(DialogSmallIconSize),
        )
        Spacer(modifier = Modifier.width(DialogRowIconSpacing))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun InfoRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(horizontal = DialogRowHorizontalPadding, vertical = DialogRowVerticalPadding),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(.6f),
        )
        Spacer(modifier = Modifier.width(DialogRowValueSpacing))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(.4f),
            textAlign = TextAlign.End,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun IndicatorInfoRow(
    label: String,
    indicatorColor: Color,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = DialogRowHorizontalPadding, vertical = DialogRowVerticalPadding),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Canvas(modifier = Modifier.size(DialogIndicatorSize)) {
            drawCircle(indicatorColor)
        }
    }
}

@Composable
fun IconActionItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier =
            modifier
                .clickable(enabled = enabled) { onClick() }
                .padding(DialogIconActionPadding),
    ) {
        Icon(icon, contentDescription = label, modifier = Modifier.size(DialogSmallIconSize), tint = tint)
        Spacer(modifier = Modifier.height(DialogIconActionSpacing))
        Text(label, style = MaterialTheme.typography.labelSmall, color = tint)
    }
}
