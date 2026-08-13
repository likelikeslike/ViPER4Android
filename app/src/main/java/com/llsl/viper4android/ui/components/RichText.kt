package com.llsl.viper4android.ui.components

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.fromHtml

@Composable
fun RichText(
    text: String,
    modifier: Modifier = Modifier,
) {
    val annotated = remember(text) { AnnotatedString.fromHtml(text) }

    Text(
        text = annotated,
        modifier = modifier,
        style = LocalTextStyle.current,
    )
}
