package com.zaitxcode.opencut.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Brand colours shared by the logo, the launcher icon and the theme. */
object BrandColors {
    val Accent = Color(0xFF3A7AFE)
    val AccentDeep = Color(0xFF2B5FD9)
    val Ink = Color(0xFF0E0E10)
    val Paper = Color(0xFFF5F6FA)
}

/**
 * The play badge: a filled disc with a cut-out play triangle.
 *
 * Drawn with primitives so it stays sharp at every size and carries no binary
 * asset. It is the same shape used by the launcher icon.
 */
@Composable
fun BrandMark(
    size: Dp = 44.dp,
    disc: Color = BrandColors.Accent,
    glyph: Color = BrandColors.Ink,
    modifier: Modifier = Modifier,
) {
    Canvas(
        modifier = modifier
            .size(size)
            .clip(CircleShape),
    ) {
        drawCircle(color = disc, radius = this.size.minDimension / 2f)
        drawPlayGlyph(glyph)
    }
}

private fun DrawScope.drawPlayGlyph(color: Color) {
    val w = size.width
    val h = size.height
    val path = Path().apply {
        moveTo(w * 0.38f, h * 0.26f)
        lineTo(w * 0.38f, h * 0.74f)
        lineTo(w * 0.78f, h * 0.50f)
        close()
    }
    drawPath(path, color)
}

/** Mark plus the OpenCut wordmark. */
@Composable
fun BrandLogo(
    markSize: Dp = 44.dp,
    fontSize: Int = 26,
    tint: Color = MaterialTheme.colorScheme.onBackground,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
    ) {
        BrandMark(size = markSize)
        Spacer(Modifier.width(12.dp))
        BrandWordmark(fontSize = fontSize, tint = tint)
    }
}

/** The wordmark alone, used where the mark is already present. */
@Composable
fun BrandWordmark(
    fontSize: Int = 26,
    tint: Color = MaterialTheme.colorScheme.onBackground,
    modifier: Modifier = Modifier,
) {
    Text(
        text = "OpenCut",
        modifier = modifier,
        color = tint,
        fontSize = fontSize.sp,
        fontWeight = FontWeight.Black,
        letterSpacing = (-0.5).sp,
    )
}

/** Centred lockup used by the onboarding and splash screens. */
@Composable
fun BrandCentered(
    markSize: Dp = 72.dp,
    fontSize: Int = 34,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        BrandMark(size = markSize)
        Spacer(Modifier.width(14.dp))
        BrandWordmark(fontSize = fontSize)
    }
}

/** The "free forever" pill shown next to the logo. */
@Composable
fun FreeBadge(modifier: Modifier = Modifier) {
    Text(
        text = "FREE FOREVER",
        color = BrandColors.Accent,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        modifier = modifier
            .clip(CircleShape)
            .background(BrandColors.Accent.copy(alpha = 0.12f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}
