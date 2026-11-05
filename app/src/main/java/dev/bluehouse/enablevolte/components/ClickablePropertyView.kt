package dev.bluehouse.enablevolte.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.bluehouse.enablevolte.R

@Composable
fun ClickablePropertyView(
    label: String,
    value: String?,
    @Suppress("UNUSED_PARAMETER") labelFontSize: TextUnit = 18.sp,
    @Suppress("UNUSED_PARAMETER") valueFontSize: TextUnit = 14.sp,
    @Suppress("UNUSED_PARAMETER") labelFontFamily: FontFamily? = null,
    valueFontFamily: FontFamily? = null,
    onClick: (() -> Unit)? = null,
) {
    // The Clear Glass row: a short value sits on the right before the chevron, a long
    // one goes under the title so it never squeezes the label.
    val shown = value ?: stringResource(R.string.unknown)
    val short = shown.length <= 22 && valueFontFamily == null
    ListRow(
        title = label,
        subtitle = if (short) null else shown,
        onClick = onClick,
    ) {
        if (short && shown.isNotBlank()) RowValue(shown)
        if (onClick != null) Chevron()
    }
}

@Preview
@Composable
fun ClickablePropertyViewPreview() {
    val value by remember { mutableStateOf("dolor sit amet") }
    ClickablePropertyView("Lorem Ipsum", value) {}
}
