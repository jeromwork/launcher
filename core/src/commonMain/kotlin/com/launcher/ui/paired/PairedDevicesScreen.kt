package com.launcher.ui.paired

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Spec 010 T082 — paired devices screen с двумя секциями (FR-029).
 *
 *  - «Кто помогает мне» — items с role = [PairedDeviceItem.Section.HelpsMe].
 *  - «Кому я помогаю» — items с role = [PairedDeviceItem.Section.IHelp].
 *
 * If both lists are empty, renders empty-state (T088 / FR-033) with the
 * «Показать QR» CTA — host wires this to the спек-007 QR flow.
 *
 * Tap on «Прекратить помощь» fires [onUnlinkClick] with the chosen
 * [PairedDeviceItem]; the host is responsible for showing
 * [UnlinkConfirmationDialog] and gating the actual revocation. Splitting
 * confirm UI vs. list UI keeps both Composables independently testable.
 *
 * Strings are passed by the host (no `stringResource` in commonMain).
 */
@Composable
fun PairedDevicesScreen(
    helpsMe: List<PairedDeviceItem>,
    iHelp: List<PairedDeviceItem>,
    helpsMeSectionTitle: String,
    iHelpSectionTitle: String,
    unlinkButtonLabel: String,
    emptyStateBody: String,
    emptyStateActionLabel: String,
    onUnlinkClick: (PairedDeviceItem) -> Unit,
    onShowQrClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isEmpty = helpsMe.isEmpty() && iHelp.isEmpty()
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(PaddingValues(horizontal = 16.dp, vertical = 24.dp))
            .testTag("paired_devices_screen"),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (isEmpty) {
            EmptyState(
                body = emptyStateBody,
                actionLabel = emptyStateActionLabel,
                onShowQrClick = onShowQrClick,
            )
        } else {
            if (helpsMe.isNotEmpty()) {
                SectionHeader(text = helpsMeSectionTitle, testTagSuffix = "helps_me")
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(helpsMe, key = { it.linkId }) { item ->
                        PairedRow(item, unlinkButtonLabel, onUnlinkClick)
                    }
                }
                if (iHelp.isNotEmpty()) HorizontalDivider()
            }
            if (iHelp.isNotEmpty()) {
                SectionHeader(text = iHelpSectionTitle, testTagSuffix = "i_help")
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(iHelp, key = { it.linkId }) { item ->
                        PairedRow(item, unlinkButtonLabel, onUnlinkClick)
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String, testTagSuffix: String) {
    Text(
        text = text,
        fontSize = 22.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("paired_section_$testTagSuffix"),
    )
}

@Composable
private fun PairedRow(
    item: PairedDeviceItem,
    unlinkButtonLabel: String,
    onUnlinkClick: (PairedDeviceItem) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("paired_row_${item.linkId}"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = item.displayName,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.testTag("paired_row_name_${item.linkId}"),
        )
        if (item.pairedDateLabel.isNotEmpty()) {
            Text(
                text = item.pairedDateLabel,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.testTag("paired_row_date_${item.linkId}"),
            )
        }
        OutlinedButton(
            onClick = { onUnlinkClick(item) },
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 56.dp)
                .testTag("paired_row_unlink_${item.linkId}"),
        ) {
            Text(text = unlinkButtonLabel)
        }
    }
}

@Composable
private fun EmptyState(
    body: String,
    actionLabel: String,
    onShowQrClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("paired_empty_state"),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = body,
            style = MaterialTheme.typography.bodyLarge,
        )
        OutlinedButton(
            onClick = onShowQrClick,
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 56.dp)
                .testTag("paired_empty_show_qr"),
        ) {
            Text(text = actionLabel)
        }
    }
}
