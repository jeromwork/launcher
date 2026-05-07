package com.launcher.ui.navigation

import com.arkivanov.decompose.ComponentContext

/**
 * AddFlowWizard placeholder component (spec 003 §Phase 6 mock).
 * Real flow creation arrives in spec 005 (action-architecture-v2).
 */
class AddFlowWizardComponent(
    componentContext: ComponentContext,
    val onBack: () -> Unit,
    val onDone: () -> Unit,
) : ComponentContext by componentContext

/**
 * AddSlotWizard placeholder component (spec 003 §Phase 6 mock).
 * Real slot creation arrives in spec 005 (action-architecture-v2).
 */
class AddSlotWizardComponent(
    componentContext: ComponentContext,
    val flowId: String,
    val onBack: () -> Unit,
    val onDone: () -> Unit,
) : ComponentContext by componentContext

/**
 * AdminDevices placeholder component (spec 003 §Phase 7 mock).
 * Real paired-device list arrives in spec 009 (admin-mode-flows).
 */
class AdminDevicesComponent(
    componentContext: ComponentContext,
    val onBack: () -> Unit,
) : ComponentContext by componentContext
