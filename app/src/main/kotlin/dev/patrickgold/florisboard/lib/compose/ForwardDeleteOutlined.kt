package dev.patrickgold.florisboard.lib.compose

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.graphics.vector.group
import androidx.compose.ui.unit.dp

/**
 * Outlined "forward delete" icon: the outlined Backspace icon mirrored horizontally, so that it matches the style of
 * the backspace key (the stock ForwardDelete icon is a filled glyph).
 */
val ForwardDeleteOutlined: ImageVector by lazy {
    ImageVector.Builder(
        name = "ForwardDeleteOutlined",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        group(pivotX = 12f, pivotY = 12f, scaleX = -1f) {
            addPath(
                pathData = addPathNodes(
                    "M22,3L7,3c-0.69,0 -1.23,0.35 -1.59,0.88L0,12l5.41,8.11c0.36,0.53 0.9,0.89 1.59,0.89h15" +
                        "c1.1,0 2,-0.9 2,-2L24,5c0,-1.1 -0.9,-2 -2,-2zM22,19L7.07,19L2.4,12l4.66,-7L22,5v14z" +
                        "M19,15.59L17.59,17 14,13.41 10.41,17 9,15.59 12.59,12 9,8.41 10.41,7 14,10.59 " +
                        "17.59,7 19,8.41 15.41,12z",
                ),
                fill = SolidColor(Color.Black),
            )
        }
    }.build()
}
