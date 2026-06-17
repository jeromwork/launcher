package com.launcher.ui.senior.primitives

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** ≥18sp, 1.5× line height. FR-034. */
@Composable
fun SeniorBodyText(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier,
        style = TextStyle(fontSize = 18.sp, lineHeight = 27.sp),
    )
}

/** ≥24sp, 1.5× line height, bold. FR-034. */
@Composable
fun SeniorTitleText(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier,
        style = TextStyle(fontSize = 24.sp, lineHeight = 36.sp, fontWeight = FontWeight.SemiBold),
    )
}
