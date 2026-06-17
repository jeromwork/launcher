package com.launcher.api.wizard.fakes

import com.launcher.api.localization.LocaleProvider
import com.launcher.api.localization.StringResolver
import com.launcher.api.wizard.AnimationPreferenceProvider
import com.launcher.api.wizard.ApplyResult
import com.launcher.api.wizard.Clock
import com.launcher.api.wizard.ConfigKind
import com.launcher.api.wizard.ConfigSource
import com.launcher.api.wizard.ConfigSourceResult
import com.launcher.api.wizard.ConfigSummary
import com.launcher.api.wizard.DiagnosticEmitter
import com.launcher.api.wizard.DiagnosticEvent
import com.launcher.api.wizard.DismissedHintsState
import com.launcher.api.wizard.DismissedHintsStore
import com.launcher.api.wizard.PermissionRequestPort
import com.launcher.api.wizard.PermissionResult
import com.launcher.api.wizard.SettingStatus
import com.launcher.api.wizard.SystemSettingPort
import com.launcher.api.wizard.UserPreferences
import com.launcher.api.wizard.UserPreferencesStore
import com.launcher.api.wizard.WizardCheckpoint
import com.launcher.api.wizard.WizardCheckpointStore
import com.launcher.api.wizard.data.ConfigDocument
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class InMemoryCheckpointStore : WizardCheckpointStore {
    private val map = mutableMapOf<String, WizardCheckpoint>()
    override suspend fun load(manifestId: String): WizardCheckpoint? = map[manifestId]
    override suspend fun save(checkpoint: WizardCheckpoint) { map[checkpoint.manifestId] = checkpoint }
    override suspend fun clear(manifestId: String) { map.remove(manifestId) }
}

class InMemoryDismissedHintsStore : DismissedHintsStore {
    private val set = mutableSetOf<String>()
    override suspend fun isDismissed(hintId: String): Boolean = hintId in set
    override suspend fun markDismissed(hintId: String) { set += hintId }
    override suspend fun clear(hintId: String) { set -= hintId }
    override suspend fun current(): DismissedHintsState = DismissedHintsState(set.toSet())
}

class InMemoryUserPreferencesStore(
    initial: UserPreferences = UserPreferences(),
) : UserPreferencesStore {
    private val flow = MutableStateFlow(initial)
    override suspend fun save(prefs: UserPreferences) { flow.value = prefs }
    override fun observe(): Flow<UserPreferences> = flow.asStateFlow()
    override suspend fun current(): UserPreferences = flow.value
    override suspend fun markWizardCompleted(appFamilyId: String) {
        flow.value = flow.value.copy(
            wizardCompletedAppFamilies = flow.value.wizardCompletedAppFamilies + appFamilyId,
        )
    }
    override suspend fun isWizardCompleted(appFamilyId: String): Boolean =
        appFamilyId in flow.value.wizardCompletedAppFamilies
}

class FakeConfigSource(
    private val summaries: Map<ConfigKind, List<ConfigSummary>> = emptyMap(),
    private val documents: Map<Pair<ConfigKind, String>, ConfigDocument> = emptyMap(),
) : ConfigSource {
    override suspend fun list(kind: ConfigKind): List<ConfigSummary> = summaries[kind].orEmpty()
    override suspend fun load(kind: ConfigKind, id: String): ConfigSourceResult {
        val doc = documents[kind to id] ?: return ConfigSourceResult.NotFound(id)
        return ConfigSourceResult.Success(doc)
    }
}

class FakeSystemSettingAdapter(
    private val initialStatuses: Map<String, SettingStatus> = emptyMap(),
    private val applyOutcomes: Map<String, ApplyResult> = emptyMap(),
) : SystemSettingPort {
    private val statuses = initialStatuses.toMutableMap()
    override suspend fun status(settingId: String): SettingStatus =
        statuses[settingId] ?: SettingStatus.NotApplied
    override suspend fun applyOrPrompt(settingId: String): ApplyResult {
        val result = applyOutcomes[settingId] ?: ApplyResult.PromptShown
        if (result == ApplyResult.Applied) statuses[settingId] = SettingStatus.Applied
        return result
    }
}

class FakeLocaleProvider(private val tag: String = "en") : LocaleProvider {
    override fun currentLocaleTag(): String = tag
}

class FakeStringResolver(
    private val tag: String = "en",
    private val table: Map<String, String> = emptyMap(),
) : StringResolver {
    override fun resolve(key: String, args: Map<String, Any>): String {
        val template = table[key] ?: return key
        return interpolate(template, args)
    }
    override fun resolvePlural(key: String, count: Int, args: Map<String, Any>): String {
        val template = table[key] ?: return key
        return interpolate(template, args + ("count" to count))
    }
    override fun currentLocaleTag(): String = tag

    private fun interpolate(template: String, args: Map<String, Any>): String {
        var out = template
        for ((k, v) in args) {
            out = out.replace("{$k}", v.toString())
        }
        return out
    }
}

class FakeClock(private var fixed: Long = 0L) : Clock {
    override fun nowEpochMillis(): Long = fixed
    fun advance(deltaMillis: Long) { fixed += deltaMillis }
}

class FakeAnimationPreferenceProvider(private var scale: Float = 1f) : AnimationPreferenceProvider {
    override fun durationScale(): Float = scale
    fun set(newScale: Float) { scale = newScale }
}

class RecordingDiagnosticEmitter : DiagnosticEmitter {
    private val events = mutableListOf<DiagnosticEvent>()
    override fun emit(event: DiagnosticEvent) { events += event }
    fun snapshot(): List<DiagnosticEvent> = events.toList()
    fun clear() { events.clear() }
}

class FakePermissionRequestPort(
    private val outcomes: Map<String, PermissionResult> = emptyMap(),
    private val granted: Set<String> = emptySet(),
    private val permDenied: Set<String> = emptySet(),
) : PermissionRequestPort {
    override suspend fun request(permission: String): PermissionResult =
        outcomes[permission] ?: PermissionResult.Denied
    override fun isGranted(permission: String): Boolean = permission in granted
    override fun isPermanentlyDenied(permission: String): Boolean = permission in permDenied
}
