package dev.bluehouse.enablevolte.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.bluehouse.enablevolte.ui.theme.LocalInstrument

/** Calm, solid reading surface shared by Home and the live monitor; glass is kept for what floats. */
@Composable
fun LiquidSurface(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(
        modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp),
        color = LocalInstrument.current.card,
        content = content,
    )
}

@Composable
fun LiquidPane(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    LiquidSurface(modifier) {
        Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(9.dp), content = content)
    }
}
