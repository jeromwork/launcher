package com.launcher.app.edit

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.view.accessibility.AccessibilityManager
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.launcher.app.R

/**
 * Per FR-012a (TalkBack drag alternative): when screen-reader explore-by-touch
 * is enabled, drag-and-drop becomes inaccessible. This DropdownMenu offers
 * an equivalent action set (Move up/down/left/right, Delete) so screen-reader
 * users have a primary path для tile reordering и deletion.
 *
 * Mainstream users see this menu as **secondary** affordance (also available
 * via long-press) — drag-and-drop остаётся primary path для них.
 *
 * Usage:
 * ```
 * var open by remember { mutableStateOf(false) }
 * Box(modifier = Modifier.combinedClickable(
 *     onClick = {...},
 *     onLongClick = { open = true },
 * )) {
 *     TileCard(...)
 *     TileContextMenu(
 *         expanded = open,
 *         onDismiss = { open = false },
 *         onMoveUp = {...}, onMoveDown = {...},
 *         onMoveLeft = {...}, onMoveRight = {...},
 *         onDelete = {...},
 *     )
 * }
 * ```
 */
@Composable
fun TileContextMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onMoveLeft: () -> Unit,
    onMoveRight: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        modifier = modifier.testTag("f014_tile_context_menu"),
    ) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.f014_context_menu_move_up)) },
            onClick = { onDismiss(); onMoveUp() },
            modifier = Modifier.testTag("f014_ctx_move_up"),
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.f014_context_menu_move_down)) },
            onClick = { onDismiss(); onMoveDown() },
            modifier = Modifier.testTag("f014_ctx_move_down"),
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.f014_context_menu_move_left)) },
            onClick = { onDismiss(); onMoveLeft() },
            modifier = Modifier.testTag("f014_ctx_move_left"),
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.f014_context_menu_move_right)) },
            onClick = { onDismiss(); onMoveRight() },
            modifier = Modifier.testTag("f014_ctx_move_right"),
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.f014_context_menu_delete)) },
            onClick = { onDismiss(); onDelete() },
            modifier = Modifier.testTag("f014_ctx_delete"),
        )
    }
}

/**
 * Per FR-012a — returns `true` when TalkBack / Switch Access / other
 * accessibility service with touch exploration is enabled. UI layer uses
 * this hint to prefer [TileContextMenu] over drag-and-drop как primary
 * affordance.
 *
 * Note: drag-and-drop остаётся доступен for both paths — this just sets
 * which one is presented first / hinted в content descriptions.
 */
fun Context.isTouchExplorationEnabled(): Boolean {
    val mgr = getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
        ?: return false
    if (!mgr.isEnabled) return false
    val services = mgr.getEnabledAccessibilityServiceList(
        AccessibilityServiceInfo.FEEDBACK_SPOKEN,
    )
    return mgr.isTouchExplorationEnabled || !services.isNullOrEmpty()
}
