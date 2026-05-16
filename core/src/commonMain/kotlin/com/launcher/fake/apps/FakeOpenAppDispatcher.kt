package com.launcher.fake.apps

import com.launcher.api.apps.OpenAppDispatcher
import com.launcher.api.apps.OpenAppResult
import com.launcher.api.result.Outcome

/**
 * Recording fake for [OpenAppDispatcher] (spec 009 Phase 4). Default
 * behaviour: every call returns [OpenAppResult.Launched] and records the
 * package name. Tests override [scriptedResponses] to simulate fallback
 * chain — first matching scripted response wins.
 */
class FakeOpenAppDispatcher(
    private val scriptedResponses: Map<String, OpenAppResult> = emptyMap(),
    private val defaultResponse: OpenAppResult = OpenAppResult.Launched,
) : OpenAppDispatcher {

    private val _launchHistory = mutableListOf<String>()
    val launchHistory: List<String> get() = _launchHistory.toList()

    override suspend fun openApp(packageName: String): Outcome<OpenAppResult, Throwable> {
        _launchHistory.add(packageName)
        val response = scriptedResponses[packageName] ?: defaultResponse
        return Outcome.Success(response)
    }
}
