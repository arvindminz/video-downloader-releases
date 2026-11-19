package com.example.videodownloader

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun TopRightDownloadButton(
    badgeCount: Int = 0,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier.padding(end = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        IconButton(onClick = onClick, modifier = Modifier.size(36.dp)) {
            Image(
                painter = painterResource(id = R.drawable.download),
                contentDescription = "Downloads",
                modifier = Modifier.size(24.dp)
            )
        }

        if (badgeCount > 0) {
            Surface(
                shape = CircleShape,
                color = Color.Red,
                modifier = Modifier
                    .size(16.dp)
                    .align(Alignment.TopEnd)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (badgeCount > 99) "99+" else "$badgeCount",
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 9.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.wrapContentSize(Alignment.Center)
                    )
                }
            }
        }
    }
}
