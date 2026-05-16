package com.launcher.ui.navigation

/**
 * Sealed family of children produced by [RootComponent]'s child stack.
 * Each variant corresponds to one [RootConfig].
 */
sealed interface RootChild {

    data class FirstLaunch(val component: FirstLaunchComponent) : RootChild

    data class Home(val component: HomeComponent) : RootChild

    data class Settings(val component: SettingsComponent) : RootChild

    data class AddFlowWizard(val component: AddFlowWizardComponent) : RootChild

    data class AddSlotWizard(val component: AddSlotWizardComponent) : RootChild

    data class AdminDevices(val component: AdminDevicesComponent) : RootChild

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
}
