package com.launcher.cloud.fake

import com.launcher.cloud.api.ActionContext
import com.launcher.cloud.api.ActionResult
import com.launcher.cloud.api.LocalAlternative

class FakeLocalAlternative(private val result: ActionResult) : LocalAlternative {
    override suspend fun executeLocally(context: ActionContext): ActionResult = result
}
