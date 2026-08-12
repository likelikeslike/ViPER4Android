package com.llsl.viper4android.ui.components

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight

/**
 * Renders a plain string with minimal inline markup:
 *  - `\n` → line break
 *  - `**text**` → bold span
 *
 * Designed for use in help dialogs where strings.xml cannot carry
 * HTML but structured formatting is desired.
 */
@Composable
fun RichText(
    text: String,
    modifier: Modifier = Modifier,
) {
    val annotated = remember(text) {
        buildAnnotatedString {
            val bold = SpanStyle(fontWeight = FontWeight.Bold)
            var i = 0
            while (i < text.length) {
                if (text.startsWith("**", i)) {
                    val end = text.indexOf("**", i + 2)
                    if (end != -1) {
                        pushStyle(bold)
                        append(text.substring(i + 2, end))
                        pop()
                        i = end + 2
                        continue
                    }
                }
                if (text[i] == '\\' && i + 1 < text.length && text[i + 1] == 'n') {
                    append('\n')
                    i += 2
                    continue
                }
                append(text[i])
                i++
            }
        }
    }

    Text(
        text = annotated,
        modifier = modifier,
        style = LocalTextStyle.current,
    )
}
