package com.soltini.app.ui.theme

import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Screen background for the active theme. Classic: the original brush, unchanged.
 * Blue Glass: icy blue gradient with soft white/cyan "cloud" glows like the reference design.
 * Pure gradients (no bitmaps), so it is cheap and scales to any screen size.
 */
@Composable
fun Modifier.myraBackground(): Modifier {
    val t = LocalMyraTokens.current
    if (t.mode != MyraThemeMode.BLUE_GLASS) return this.background(t.backgroundBrush)
    return this
        .background(t.backgroundBrush)
        .drawBehind {
            val w = size.width
            val h = size.height
            fun glow(c: Color, cx: Float, cy: Float, r: Float) =
                drawRect(Brush.radialGradient(listOf(c, Color.Transparent), center = Offset(w * cx, h * cy), radius = r))
            glow(Color(0x9900E5FF), 0.12f, 0.04f, w * 0.90f)   // vivid electric cyan flare, top-left
            glow(Color(0xD9FFFFFF), 0.88f, 0.22f, w * 0.80f)   // pure diamond white wisp, right
            glow(Color(0xB3FFFFFF), 0.18f, 0.58f, w * 0.85f)   // soft white glass refraction, mid-left
            glow(Color(0x882563EB), 0.88f, 0.92f, w * 0.90f)   // rich royal sapphire glow, bottom-right
            glow(Color(0x5538BDF8), 0.50f, 0.98f, w * 0.70f)   // sky blue grounding mist, bottom-center
        }
}
