package com.launcher.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Vertical stack of in-launcher crisis banners (FR-031). Renders provided
 * [AlertBannerData] entries in given order — caller responsible for sorting
 * (Airplane above Mute per FR-031).
 *
 * Empty list → empty Column (zero height, не занимает место в HomeScreen).
 *
 * `contentPadding` defaults applied at HomeScreen level — banners сами не
 * берут padding снаружи.
 */
@Composable
fun AlertBannerStack(
    banners: List<AlertBannerData>,
    modifier: Modifier = Modifier,
) {
    if (banners.isEmpty()) return
    Column(
        modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        banners.forEach { data ->
            AlertBannerCard(
                icon = data.icon,
                iconContentDescription = data.iconContentDescription,
                text = data.text,
                actionLabel = data.actionLabel,
                onAction = data.onAction,
            )
        }
    }
}

/**
 * View model для одного banner entry. Resolved в `:app` HomeBannerHost из
 * (AlertBanner type + string resources + icon resources + onClick handler).
 */
data class AlertBannerData(
    val icon: androidx.compose.ui.graphics.painter.Painter,
    val iconContentDescription: String,
    val text: String,
    val actionLabel: String,
    val onAction: () -> Unit,
)
