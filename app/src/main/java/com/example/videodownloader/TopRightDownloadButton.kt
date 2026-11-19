package com.example.videodownloader

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Top-Right Download Button matching Mary Akveo Flat Line SVG design:
 *  - Line: (12, 3) to (12, 21)
 *  - Polyline: (19, 14) -> (12, 21) -> (5, 14)
 *  - Displays active/completed download badge count.
 */
@Composable
fun TopRightDownloadButton(
    badgeCount: Int = 0,
    onClick: () -> Unit
) {
    Box(modifier = Modifier.padding(end = 4.dp)) {
        IconButton(onClick = onClick, modifier = Modifier.size(36.dp)) {
            Canvas(modifier = Modifier.size(22.dp)) {
                val strokeWidthPx = 2.2.dp.toPx()

                // Vertical Line: x1=12, y1=3, x2=12, y2=21
                drawLine(
                    color = Color.Red,
                    start = Offset(size.width * 0.5f, size.height * 0.125f),
                    end = Offset(size.width * 0.5f, size.height * 0.875f),
                    strokeWidth = strokeWidthPx,
                    cap = StrokeCap.Round
                )

                // Downward Polyline Arrowhead: (19, 14) -> (12, 21) -> (5, 14)
                val path = Path().apply {
                    moveTo(size.width * 0.7917f, size.height * 0.5833f)
                    lineTo(size.width * 0.5f, size.height * 0.875f)
                    lineTo(size.width * 0.2083f, size.height * 0.5833f)
                }

                drawPath(
                    path = path,
                    color = Color.Red,
                    style = Stroke(
                        width = strokeWidthPx,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
            }
        }

        if (badgeCount > 0) {
            Surface(
                shape = CircleShape,
                color = Color.Red,
                modifier = Modifier
                    .size(16.dp)
                    .align(Alignment.TopEnd)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = if (badgeCount > 99) "99+" else "$badgeCount",
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
