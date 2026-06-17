package com.launcher.ui.senior.primitives

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Senior-safe primary button. Per FR-034:
 *  - tap target ≥ 56dp (project override above WCAG 48dp baseline)
 *  - text ≥ 18sp
 *  - wrap-content height/width (no fixed sizing that clips translated text)
 *  - 16dp internal padding
 */
@Composable
fun SeniorButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        modifier = modifier.defaultMinSize(minHeight = 56.dp),
        enabled = enabled,
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
    ) {
        Text(text = text, fontSize = 18.sp, textAlign = TextAlign.Center)
    }
}

@Composable
fun SeniorSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.defaultMinSize(minHeight = 56.dp),
        enabled = enabled,
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
    ) {
        Text(text = text, fontSize = 18.sp, textAlign = TextAlign.Center)
    }
}

/**
 * Senior-safe icon button. ≥56dp square. Requires non-empty content
 * description (FR-036 ACC-3).
 */
@Composable
fun SeniorIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    require(contentDescription.isNotBlank()) {
        "SeniorIconButton requires non-empty contentDescription (FR-036 ACC-3)"
    }
    IconButton(
        onClick = onClick,
        modifier = modifier
            .defaultMinSize(minWidth = 56.dp, minHeight = 56.dp)
            .padding(8.dp)
            .semantics { this.contentDescription = contentDescription },
        enabled = enabled,
    ) {
        Icon(imageVector = icon, contentDescription = contentDescription)
    }
}
