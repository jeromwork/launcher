package com.launcher.ui.wizard

import com.launcher.api.wizard.Clock
import com.launcher.api.wizard.ConfigKind
import com.launcher.api.wizard.ConfigSource
import com.launcher.api.wizard.ConfigSourceResult
import com.launcher.api.wizard.Criticality
import com.launcher.api.wizard.DiagnosticEmitter
import com.launcher.api.wizard.DiagnosticEvent
import com.launcher.api.wizard.DismissedHintsStore
import com.launcher.api.wizard.PendingStep
import com.launcher.api.wizard.SettingStatus
import com.launcher.api.wizard.StepId
import com.launcher.api.wizard.StepParams
import com.launcher.api.wizard.StepResult
import com.launcher.api.wizard.StepType
import com.launcher.api.wizard.SystemSettingPort
import com.launcher.api.wizard.UserPreferences
import com.launcher.api.wizard.UserPreferencesStore
import com.launcher.api.wizard.WizardCheckpoint
import com.launcher.api.wizard.WizardCheckpointStore
import com.launcher.api.wizard.WizardEngine
import com.launcher.api.wizard.WizardOutcome
import com.launcher.api.wizard.WizardState
import com.launcher.api.wizard.WizardStep
import com.launcher.api.wizard.data.ConfigDocument
import com.launcher.api.wizard.data.ConfigDocumentHeader
import com.launcher.api.wizard.data.ConfigDocumentRef
import com.launcher.api.wizard.data.StepEntry
import com.launcher.api.wizard.data.WireCriticality
import com.launcher.api.wizard.data.WireStepType
import com.launcher.api.wizard.data.WizardManifest
import com.launcher.api.wizard.data.WizardManifestBody
import com.launcher.api.wizard.data.toDomain
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

/**
 * F-3 WizardEngine implementation — state machine: load manifest → resume
 * if checkpoint present → traverse → checkpoint after each step → return
 * outcome.
 *
 * Per FR-002, FR-003, FR-004, FR-014b, FR-014c.
 */
class WizardEngineImpl(
    private val steps: Map<StepType, WizardStep>,
    private val checkpointStore: WizardCheckpointStore,
    private val userPreferencesStore: UserPreferencesStore,
    private val configSource: ConfigSource,
    private val clock: Clock,
    private val diagnostics: DiagnosticEmitter,
    private val systemSettingPort: SystemSettingPort,
    private val dismissedHintsStore: DismissedHintsStore,
) : WizardEngine {

    private val _state = MutableStateFlow<WizardState>(WizardState.Idle)

    override fun currentState(): StateFlow<WizardState> = _state.asStateFlow()

    override suspend fun computePending(manifest: WizardManifest): List<StepEntry> {
        val ordered = orderedSteps(manifest)
        if (ordered.isEmpty()) return emptyList()
        val prefs = userPreferencesStore.current()
        return ordered.filter { entry ->
            when (entry.stepType.toDomain()) {
                StepType.SystemSetting -> systemSettingPort.status(entry.refId) != SettingStatus.Applied
                StepType.UIChoice -> !prefs.hasValueFor(entry.refId)
                StepType.TutorialHint -> !dismissedHintsStore.isDismissed(entry.refId)
                is StepType.Custom -> true
            }
        }
    }

    override suspend fun run(manifest: WizardManifest): WizardOutcome {
        diagnostics.emit(DiagnosticEvent.WizardStarted(manifest.id))

        // Config-check master pre-flight: when nothing is pending, finish
        // immediately without traversal (Article VII §14, FR-014, SC-003).
        // Skipped when resuming from a checkpoint — partial answers must
        // still be replayed even if downstream state has since converged.
        val resumed = checkpointStore.load(manifest.id)
        if (resumed == null && computePending(manifest).isEmpty()) {
            return finishCompleted(manifest, emptyMap())
        }

        val ordered = orderedSteps(manifest)
        if (ordered.isEmpty()) {
            return finishCompleted(manifest, emptyMap())
        }

        val startIndex = resumed
            ?.takeIf { it.schemaVersion == 1 && it.currentStepIndex in ordered.indices }
            ?.currentStepIndex
            ?: 0
        val answers = resumed
            ?.takeIf { it.schemaVersion == 1 }
            ?.answers
            ?.toMutableMap()
            ?: mutableMapOf()

        var index = startIndex
        while (index < ordered.size) {
            val entry = ordered[index]
            val stepType = entry.stepType.toDomain()
            val step = steps[stepType]
                ?: return WizardOutcome.Failed("no handler for stepType $stepType")

            _state.value = WizardState.Running(
                currentStepIndex = index,
                totalSteps = ordered.size,
                currentStep = step,
                answers = answers.toMap(),
            )

            val result = step.execute(
                StepParams(
                    refId = entry.refId,
                    params = entry.params,
                    stepIndex = index,
                    totalSteps = ordered.size,
                ),
            )

            when (result) {
                is StepResult.AnswerCaptured -> {
                    answers[entry.refId] = result.answer
                    diagnostics.emit(
                        DiagnosticEvent.WizardStepCompleted(index, stepType::class.simpleName.orEmpty()),
                    )
                    checkpointStore.save(
                        WizardCheckpoint(
                            schemaVersion = 1,
                            manifestId = manifest.id,
                            currentStepIndex = index + 1,
                            answers = answers.toMap(),
                        ),
                    )
                    index += 1
                }

                is StepResult.Skipped -> {
                    if (!entry.canSkip && (entry.criticality?.toDomain() ?: Criticality.Required) == Criticality.Required) {
                        return WizardOutcome.Failed("required step ${entry.refId} cannot be skipped")
                    }
                    diagnostics.emit(
                        DiagnosticEvent.WizardStepCompleted(index, stepType::class.simpleName.orEmpty()),
                    )
                    checkpointStore.save(
                        WizardCheckpoint(
                            schemaVersion = 1,
                            manifestId = manifest.id,
                            currentStepIndex = index + 1,
                            answers = answers.toMap(),
                        ),
                    )
                    index += 1
                }

                is StepResult.Cancelled -> {
                    diagnostics.emit(DiagnosticEvent.WizardCancelled(index))
                    _state.value = WizardState.Idle
                    return WizardOutcome.Cancelled
                }

                is StepResult.BackRequested -> {
                    val target = result.toStepIndex.coerceAtLeast(0)
                    index = target
                }
            }
        }

        return finishCompleted(manifest, answers.toMap())
    }

    override suspend fun diffPending(
        savedCompletedManifest: WizardManifest?,
        currentManifest: WizardManifest,
    ): List<PendingStep> {
        val saved = savedCompletedManifest?.let { orderedSteps(it) }.orEmpty()
        val savedKey: Set<Pair<WireStepType, String>> =
            saved.map { it.stepType to it.refId }.toSet()
        val current = orderedSteps(currentManifest)
        return current
            .filter { (it.stepType to it.refId) !in savedKey }
            .map { entry ->
                PendingStep(
                    stepEntry = entry,
                    criticality = entry.criticality?.toDomain() ?: Criticality.Required,
                )
            }
    }

    private suspend fun finishCompleted(
        manifest: WizardManifest,
        answers: Map<StepId, JsonElement>,
    ): WizardOutcome {
        val outcome = WizardOutcome.Completed(
            initialConfig = ConfigDocumentRef(
                tileSetId = (answers["tileSet"] as? JsonPrimitive)?.content,
                screenLayoutId = (answers["screenLayout"] as? JsonPrimitive)?.content,
                answers = answers,
            ),
            userPreferences = nextPreferences(answers),
        )
        userPreferencesStore.save(outcome.userPreferences)
        userPreferencesStore.markWizardCompleted(manifest.appFamilyId)
        checkpointStore.clear(manifest.id)
        diagnostics.emit(DiagnosticEvent.WizardCompleted(manifest.id))
        _state.value = WizardState.Completed(outcome)
        return outcome
    }

    private suspend fun nextPreferences(answers: Map<StepId, JsonElement>): UserPreferences {
        val current = userPreferencesStore.current()
        val theme = (answers["theme"] as? JsonPrimitive)?.content
            ?.let { runCatching { com.launcher.api.wizard.ThemeChoice.valueOf(it.replaceFirstChar(Char::titlecase)) }.getOrNull() }
            ?: current.theme
        val fontScale = (answers["fontScale"] as? JsonPrimitive)?.content?.toFloatOrNull() ?: current.fontScale
        val language = (answers["language"] as? JsonPrimitive)?.content ?: current.languageOverride
        return current.copy(
            theme = theme,
            fontScale = fontScale,
            languageOverride = language,
        )
    }

    /**
     * Orders steps. If `autoOrder = true`, generates the order from both pools
     * (Required first, Optional after). Otherwise uses the explicit
     * `steps[]`.
     */
    private suspend fun orderedSteps(manifest: WizardManifest): List<StepEntry> {
        if (!manifest.autoOrder) {
            return manifest.steps.orEmpty()
        }
        return generateAutoOrder(manifest)
    }

    private suspend fun generateAutoOrder(manifest: WizardManifest): List<StepEntry> {
        val uiEntries = loadUIOptions()
        val systemEntries = loadSystemSettings()
        val combined = (uiEntries + systemEntries)
        val required = combined.filter { it.criticality == WireCriticality.Required }
        val optional = combined.filter { it.criticality == WireCriticality.Optional }
        return required + optional
    }

    private suspend fun loadUIOptions(): List<StepEntry> {
        val summaries = configSource.list(ConfigKind.UICustomizationPool)
        return summaries.flatMap { summary ->
            val result = configSource.load(ConfigKind.UICustomizationPool, summary.id)
            if (result is ConfigSourceResult.Success && result.document is ConfigDocument.UICustomizationPoolDoc) {
                result.document.body.options.map { opt ->
                    StepEntry(
                        stepType = WireStepType.UIChoice,
                        refId = opt.id,
                        params = emptyMap(),
                        canSkip = opt.criticality == WireCriticality.Optional,
                        criticality = opt.criticality,
                    )
                }
            } else emptyList()
        }
    }

    private suspend fun loadSystemSettings(): List<StepEntry> {
        val summaries = configSource.list(ConfigKind.SystemSettingsPool)
        return summaries.flatMap { summary ->
            val result = configSource.load(ConfigKind.SystemSettingsPool, summary.id)
            if (result is ConfigSourceResult.Success && result.document is ConfigDocument.SystemSettingsPoolDoc) {
                result.document.body.settings.map { setting ->
                    StepEntry(
                        stepType = WireStepType.SystemSetting,
                        refId = setting.id,
                        params = emptyMap(),
                        canSkip = setting.canSkip,
                        criticality = setting.criticality,
                    )
                }
            } else emptyList()
        }
    }
}

/** Convenience constructor that wraps body in a default header. */
fun standaloneManifest(
    id: String = "test-manifest",
    appFamilyId: String = "test",
    steps: List<StepEntry>,
): WizardManifest = WizardManifest(
    header = ConfigDocumentHeader(
        schemaVersion = 1,
        id = id,
        name = "$id.name",
        description = "$id.desc",
        deviceClass = listOf("android-phone"),
    ),
    body = WizardManifestBody(
        appFamilyId = appFamilyId,
        autoOrder = false,
        steps = steps,
    ),
)
