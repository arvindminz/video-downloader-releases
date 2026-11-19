package com.example.videodownloader

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Android APK Logo Composable matching VectorStock reference:
 *  - Green rounded square background (#7CB342)
 *  - White Android bugdroid dome head + antennae + green eyes
 *  - White pill badge with bold "APK" text
 */
@Composable
fun ApkThumbnailLogo(
    modifier: Modifier = Modifier
) {
    val androidGreen = Color(0xFF7CB342)

    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(8.dp))
            .background(androidGreen),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
        ) {
            // Bugdroid Head with Antennae & Eyes
            Canvas(modifier = Modifier.size(width = 38.dp, height = 24.dp)) {
                val headWidth = size.width * 0.75f
                val headHeight = size.height * 0.65f
                val headLeft = (size.width - headWidth) / 2f
                val headTop = size.height - headHeight

                val strokeWidthPx = 2.dp.toPx()

                // Antennae
                drawLine(
                    color = Color.White,
                    start = Offset(headLeft + headWidth * 0.22f, headTop + headHeight * 0.35f),
                    end = Offset(headLeft - 2.dp.toPx(), 2.dp.toPx()),
                    strokeWidth = strokeWidthPx,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = Color.White,
                    start = Offset(headLeft + headWidth * 0.78f, headTop + headHeight * 0.35f),
                    end = Offset(headLeft + headWidth + 2.dp.toPx(), 2.dp.toPx()),
                    strokeWidth = strokeWidthPx,
                    cap = StrokeCap.Round
                )

                // Dome Head
                drawArc(
                    color = Color.White,
                    startAngle = 180f,
                    sweepAngle = 180f,
                    useCenter = true,
                    topLeft = Offset(headLeft, headTop),
                    size = Size(headWidth, headHeight * 2f)
                )

                // Left Eye
                drawCircle(
                    color = androidGreen,
                    radius = 1.8.dp.toPx(),
                    center = Offset(headLeft + headWidth * 0.32f, headTop + headHeight * 0.5f)
                )

                // Right Eye
                drawCircle(
                    color = androidGreen,
                    radius = 1.8.dp.toPx(),
                    center = Offset(headLeft + headWidth * 0.68f, headTop + headHeight * 0.5f)
                )
            }

            Spacer(modifier = Modifier.height(2.dp))

            // White Capsule Badge with "APK"
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = Color.White,
                modifier = Modifier.padding(horizontal = 2.dp)
            ) {
                Box(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 1.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "APK",
                        color = Color.Black,
                        fontWeight = FontWeight.Black,
                        fontSize = 10.sp,
                        letterSpacing = 0.5.sp
                    )
                }
            }
        }
    }
}
