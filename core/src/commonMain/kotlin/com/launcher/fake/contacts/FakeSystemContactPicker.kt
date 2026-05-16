package com.launcher.fake.contacts

import com.launcher.api.contacts.PickError
import com.launcher.api.contacts.RawPickerContact
import com.launcher.api.contacts.SystemContactPicker
import com.launcher.api.result.Outcome

/**
 * Scriptable fake for [SystemContactPicker] (spec 009 Phase 4). Each
 * `pickContact()` call pops the next scripted response from
 * [responses]; if exhausted, returns [PickError.UserCancelled].
 */
class FakeSystemContactPicker(
    responses: List<Outcome<RawPickerContact, PickError>> = emptyList(),
) : SystemContactPicker {

    private val pending: ArrayDeque<Outcome<RawPickerContact, PickError>> =
        ArrayDeque(responses)

    override suspend fun pickContact(): Outcome<RawPickerContact, PickError> =
        if (pending.isEmpty()) Outcome.Failure(PickError.UserCancelled)
        else pending.removeFirst()
}
