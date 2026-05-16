package com.launcher.ui.admin

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Bottom-sheet menu invoked from a tile's "···" affordance in edit mode
 * (spec 009 FR-009, FR-A11Y-004). Senior-safe alternative к drag — every
 * action available as a ≥56-dp-tap-target row с explicit labels;
 * doesn't require precise long-press + drag.
 *
 * Cross-flow move stays drag-only — power-user path; FR-A11Y-004 is met
 * since editing + reordering within a flow + delete all have a non-drag
 * channel here.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TileEditMenu(
    slotLabel: String,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(PaddingValues(bottom = 32.dp)),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            Text(
                text = slotLabel,
                fontSize = 20.sp,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
            )
            MenuRow(Icons.Filled.Edit, "Изменить", enabled = true, onClick = onEdit)
            MenuRow(Icons.Filled.KeyboardArrowUp, "Сдвинуть выше", enabled = canMoveUp, onClick = onMoveUp)
            MenuRow(Icons.Filled.ArrowDropDown, "Сдвинуть ниже", enabled = canMoveDown, onClick = onMoveDown)
            MenuRow(Icons.Filled.Delete, "Удалить", enabled = true, onClick = onDelete, danger = true)
        }
    }
}

@Composable
private fun MenuRow(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    danger: Boolean = false,
) {
    val tint = when {
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant
        danger -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurface
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = tint)
        Text(text = label, fontSize = 18.sp, color = tint)
    }
}
