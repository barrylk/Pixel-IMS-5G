package dev.bluehouse.enablevolte.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.bluehouse.enablevolte.ui.theme.LocalInstrument

/** Quiet reading surface shared by Home and the live monitor; glass is strongest on controls. */
@Composable
fun LiquidSurface(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val dark = LocalInstrument.current.isDark
    Surface(
        modifier.fillMaxWidth(), shape = RoundedCornerShape(25.dp),
        color = if (dark) Color(0xC0182C3E) else Color(0xD9FFFFFF),
        border = BorderStroke(1.dp, if (dark) Color(0x454FA9E5) else Color(0xE6FFFFFF)),
        content = content,
    )
}

@Composable
fun LiquidPane(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    LiquidSurface(modifier) {
        Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(9.dp), content = content)
    }
}
