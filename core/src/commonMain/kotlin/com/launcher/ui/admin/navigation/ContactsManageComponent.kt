package com.launcher.ui.admin.navigation

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.essenty.lifecycle.doOnDestroy
import com.launcher.api.config.Contact
import com.launcher.api.config.ConfigEditor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Decompose component backing [com.launcher.ui.contacts.ContactsManageScreen]
 * (spec 009 FR-031a privacy minimum).
 *
 * Data source priority: pending draft if any (so deletions are immediately
 * visible); otherwise applied config. Delete is a draft mutation routed
 * through [ConfigEditor.updateDraft] — Admin then publishes via Editor
 * screen to push the deletion to Managed.
 *
 * Note (mentor stance): FR-031a is the **privacy minimum** — delete only.
 * Export (FR-031b) and full GDPR data-subject-rights flow tracked in
 * TODO-LEGAL-001 as PLAY-STORE-BLOCKER.
 */
class ContactsManageComponent(
    componentContext: ComponentContext,
    private val linkId: String,
    private val configEditor: ConfigEditor,
    val onBack: () -> Unit,
) : ComponentContext by componentContext {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _contacts = MutableStateFlow<List<Contact>>(emptyList())
    val contacts: StateFlow<List<Contact>> = _contacts.asStateFlow()

    init {
        lifecycle.doOnDestroy { scope.cancel() }
        scope.launch { loadInitial() }
        scope.launch { observeDraft() }
    }

    fun deleteContact(contact: Contact) {
        scope.launch {
            configEditor.updateDraft(linkId) { current ->
                current.copy(contacts = current.contacts.filterNot { it.id == contact.id })
            }
        }
    }

    private suspend fun loadInitial() {
        val applied = configEditor.appliedConfig(linkId)
        if (applied != null) _contacts.value = applied.contacts
    }

    private suspend fun observeDraft() {
        configEditor.pendingDraft(linkId).collectLatest { draft ->
            if (draft != null) _contacts.value = draft.contacts
        }
    }
}
