package com.launcher.ui.navigation

/**
 * Sealed family of children produced by [RootComponent]'s child stack.
 * Each variant corresponds to one [RootConfig].
 */
sealed interface RootChild {

    data class FirstLaunch(val component: FirstLaunchComponent) : RootChild

    data class Home(val component: HomeComponent) : RootChild

    // TASK-69: Settings re-hosted as a standalone Activity — no RootChild entry.

    data class AddFlowWizard(val component: AddFlowWizardComponent) : RootChild

    data class AddSlotWizard(val component: AddSlotWizardComponent) : RootChild

    data class AdminDevices(val component: AdminDevicesComponent) : RootChild

    /** Spec 010 T100 — 7-tap admin gate (FR-022). */
    data class ChallengeGate(val component: ChallengeGateComponent) : RootChild

    // ─── Spec 009 admin-mode-flows ─────────────────────────────────────

    data class Editor(
        val component: com.launcher.ui.admin.navigation.EditorComponent,
    ) : RootChild

    data class History(
        val component: com.launcher.ui.admin.navigation.HistoryComponent,
    ) : RootChild

    data class ContactsManage(
        val component: com.launcher.ui.admin.navigation.ContactsManageComponent,
    ) : RootChild

    data class OpenAppPicker(
        val component: com.launcher.ui.admin.navigation.OpenAppPickerComponent,
    ) : RootChild

    data class PhoneHealth(
        val component: com.launcher.ui.admin.navigation.PhoneHealthComponent,
    ) : RootChild

    data class TileEdit(
        val component: com.launcher.ui.admin.navigation.TileEditComponent,
    ) : RootChild
}
