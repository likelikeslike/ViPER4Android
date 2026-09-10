package com.llsl.viper4android.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.llsl.viper4android.R
import com.llsl.viper4android.ui.components.DialogCard
import com.llsl.viper4android.ui.components.UiDimens
import com.llsl.viper4android.ui.theme.md_alert_caution
import com.llsl.viper4android.ui.theme.md_alert_important
import com.llsl.viper4android.ui.theme.md_alert_note
import com.llsl.viper4android.ui.theme.md_alert_tip
import com.llsl.viper4android.ui.theme.md_alert_warning
import com.llsl.viper4android.utils.ReleaseInfo

@Composable
fun UpdateDialog(
    release: ReleaseInfo,
    currentVersion: String,
    upToDate: Boolean,
    downloading: Boolean,
    downloadProgress: Int,
    onDownloadInstall: () -> Unit,
    onViewOnGithub: () -> Unit,
    onDismiss: () -> Unit,
) {
    val bodyColor = MaterialTheme.colorScheme.onSurface
    val headingColor = MaterialTheme.colorScheme.primary
    val codeColor = bodyColor.copy(alpha = 0.85f)
    val blocks = remember(release.body) { parseBlocks(release.body) }

    AlertDialog(
        modifier = Modifier.fillMaxWidth(0.9f),
        properties = DialogProperties(usePlatformDefaultWidth = false),
        onDismissRequest = { if (!downloading) onDismiss() },
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(if (upToDate) R.string.update_up_to_date_title else R.string.update_available_title))
                IconButton(onClick = onDismiss, enabled = !downloading) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = stringResource(R.string.action_close),
                    )
                }
            }
        },
        text = {
            Column {
                Text(
                    text = stringResource(if (upToDate) R.string.update_latest_version else R.string.update_new_version, release.name),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = stringResource(R.string.update_current_version, currentVersion),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = UiDimens.Medium),
                )
                if (blocks.isNotEmpty()) {
                    DialogCard {
                        Column(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = UiDimens.ReleaseNotesHeight)
                                    .verticalScroll(rememberScrollState())
                                    .padding(UiDimens.XLarge),
                        ) {
                            blocks.forEach { block ->
                                BlockView(block, bodyColor, headingColor, codeColor)
                            }
                        }
                    }
                }
                if (downloading) {
                    Text(
                        text = stringResource(R.string.update_downloading, downloadProgress),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = UiDimens.XLarge),
                    )
                    LinearProgressIndicator(
                        progress = { downloadProgress / 100f },
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(top = UiDimens.Compact),
                    )
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.End) {
                FilledTonalButton(
                    onClick = onViewOnGithub,
                    modifier = Modifier.weight(1f),
                    enabled = !downloading,
                ) {
                    Text(stringResource(R.string.update_view_on_github))
                }
                if (!upToDate) {
                    Spacer(modifier = Modifier.width(UiDimens.Medium))
                    Button(
                        onClick = onDownloadInstall,
                        enabled = !downloading,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.update_download_install))
                    }
                }
            }
        },
    )
}

private sealed interface MdBlock {
    data class Heading(
        val level: Int,
        val text: String,
    ) : MdBlock

    data class Bullet(
        val marker: String,
        val text: String,
    ) : MdBlock

    data class Paragraph(
        val text: String,
    ) : MdBlock

    data class Quote(
        val alert: String?,
        val children: List<MdBlock>,
    ) : MdBlock
}

@Composable
private fun BlockView(
    block: MdBlock,
    bodyColor: Color,
    headingColor: Color,
    codeColor: Color,
) {
    when (block) {
        is MdBlock.Heading -> {
            MdText(
                text = block.text,
                bodyColor = bodyColor,
                codeColor = codeColor,
                color = headingColor,
                fontWeight = FontWeight.Bold,
                fontSize = headingSize(block.level),
                modifier = Modifier.padding(top = UiDimens.Medium, bottom = UiDimens.Tiny),
            )
        }

        is MdBlock.Bullet -> {
            Row(modifier = Modifier.padding(start = UiDimens.XSmall, top = UiDimens.Hairline)) {
                Text(text = block.marker, color = bodyColor, fontSize = 14.sp)
                MdText(
                    text = block.text,
                    bodyColor = bodyColor,
                    codeColor = codeColor,
                    color = bodyColor,
                    fontSize = 14.sp,
                )
            }
        }

        is MdBlock.Paragraph -> {
            if (block.text.isNotBlank()) {
                MdText(
                    text = block.text,
                    bodyColor = bodyColor,
                    codeColor = codeColor,
                    color = bodyColor,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(top = UiDimens.Tiny),
                )
            }
        }

        is MdBlock.Quote -> {
            val accent = alertColor(block.alert, headingColor)
            Column(
                modifier =
                    Modifier
                        .padding(vertical = UiDimens.XSmall)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(UiDimens.Compact))
                        .background(accent.copy(alpha = 0.18f))
                        .padding(vertical = UiDimens.Compact, horizontal = UiDimens.ListItemGap),
            ) {
                block.alert?.let {
                    Text(
                        text = it,
                        color = accent,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                    )
                }
                block.children.forEach { child ->
                    BlockView(child, bodyColor, headingColor, codeColor)
                }
            }
        }
    }
}

private val TASK_MARKER = Regex("""^\[([ xX])] +""")
private val ALERT_MARKER = Regex("""^\[!(NOTE|TIP|IMPORTANT|WARNING|CAUTION)]\s*$""", RegexOption.IGNORE_CASE)
private val INLINE_TOKEN = Regex("""\*\*(.+?)\*\*|`([^`]+?)`|\[([^]]+?)]\(([^)]+?)\)""")

private fun headingSize(level: Int) =
    when (level) {
        1 -> 20.sp
        2 -> 17.sp
        3 -> 15.sp
        else -> 14.sp
    }

private fun alertColor(
    alert: String?,
    default: Color,
): Color =
    when (alert?.uppercase()) {
        "NOTE" -> md_alert_note
        "TIP" -> md_alert_tip
        "IMPORTANT" -> md_alert_important
        "WARNING" -> md_alert_warning
        "CAUTION" -> md_alert_caution
        else -> default
    }

private fun parseBlocks(source: String): List<MdBlock> {
    val lines = source.replace("\r\n", "\n").split("\n")
    val blocks = mutableListOf<MdBlock>()
    var i = 0
    while (i < lines.size) {
        val trimmed = lines[i].trim()
        if (trimmed.startsWith(">")) {
            val quoteLines = mutableListOf<String>()
            while (i < lines.size && lines[i].trim().startsWith(">")) {
                quoteLines.add(lines[i].trim().removePrefix(">").trimStart())
                i++
            }
            var alert: String? = null
            val contentLines = quoteLines.toMutableList()
            ALERT_MARKER.find(contentLines.firstOrNull() ?: "")?.let {
                alert = it.groupValues[1].uppercase()
                contentLines.removeAt(0)
            }
            blocks.add(MdBlock.Quote(alert, parseBlocks(contentLines.joinToString("\n"))))
        } else {
            parseLine(trimmed)?.let { blocks.add(it) }
            i++
        }
    }
    return blocks
}

private fun parseLine(trimmed: String): MdBlock? =
    when {
        trimmed.isBlank() -> {
            null
        }

        trimmed.startsWith("#") -> {
            val level = trimmed.takeWhile { it == '#' }.length
            MdBlock.Heading(level, trimmed.drop(level).trim())
        }

        trimmed.startsWith("- ") || trimmed.startsWith("* ") -> {
            val rest = trimmed.drop(2)
            val task = TASK_MARKER.find(rest)
            if (task != null) {
                val glyph = if (task.groupValues[1] != " ") "\u2611 " else "\u2610 "
                MdBlock.Bullet(glyph, rest.substring(task.value.length))
            } else {
                MdBlock.Bullet("\u2022  ", rest)
            }
        }

        else -> {
            MdBlock.Paragraph(trimmed)
        }
    }

@Composable
private fun MdText(
    text: String,
    bodyColor: Color,
    codeColor: Color,
    color: Color,
    fontSize: androidx.compose.ui.unit.TextUnit,
    modifier: Modifier = Modifier,
    fontWeight: FontWeight? = null,
) {
    Text(
        text = buildInline(text, bodyColor, codeColor),
        color = color,
        fontSize = fontSize,
        fontWeight = fontWeight,
        modifier = modifier,
    )
}

private fun buildInline(
    text: String,
    bodyColor: Color,
    codeColor: Color,
): AnnotatedString =
    buildAnnotatedString {
        var cursor = 0
        for (match in INLINE_TOKEN.findAll(text)) {
            if (match.range.first > cursor) {
                withStyle(SpanStyle(color = bodyColor)) {
                    append(text.substring(cursor, match.range.first))
                }
            }
            val bold = match.groupValues[1]
            val code = match.groupValues[2]
            val linkText = match.groupValues[3]
            when {
                bold.isNotEmpty() -> {
                    withStyle(SpanStyle(color = bodyColor, fontWeight = FontWeight.Bold)) { append(bold) }
                }

                code.isNotEmpty() -> {
                    withStyle(SpanStyle(color = codeColor, fontFamily = FontFamily.Monospace)) { append(code) }
                }

                else -> {
                    withStyle(SpanStyle(color = bodyColor)) { append(linkText) }
                }
            }
            cursor = match.range.last + 1
        }
        if (cursor < text.length) {
            withStyle(SpanStyle(color = bodyColor)) { append(text.substring(cursor)) }
        }
    }
