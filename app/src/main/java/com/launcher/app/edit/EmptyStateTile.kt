package com.launcher.app.edit

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.launcher.app.R

/**
 * Per FR-020 + FR-020a (Q6 clarification): when the home grid is empty,
 * a large "+" tile fills the first cell. Tapping it opens the picker
 * **directly** without going through edit mode entry — single-shot add
 * для zero-state experience.
 *
 * Mainstream Niagara/Pixel pattern: zero-state provides direct affordance;
 * populated state requires explicit edit-mode entry (long-press).
 *
 * Icon size ≥72dp per FR-020.
 */
@Composable
fun EmptyStateTile(
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val description = stringResource(R.string.f014_empty_state_add)
    Card(
        modifier = modifier
            .testTag("f014_empty_state_tile")
            .semantics { contentDescription = description },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        onClick = onTap,
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = null, // a11y on parent Card
                modifier = Modifier.size(72.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
