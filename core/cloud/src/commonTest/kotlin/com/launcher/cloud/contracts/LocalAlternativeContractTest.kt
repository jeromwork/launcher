package com.launcher.cloud.contracts

import com.launcher.cloud.api.ActionContext
import com.launcher.cloud.api.ActionResult
import com.launcher.cloud.fake.FakeLocalAlternative
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LocalAlternativeContractTest {

    @Test
    fun no_cloud_dependency() = runTest {
        // INV-1: FakeLocalAlternative построен без ссылки на CloudAvailability —
        // конструктор это enforces на уровне типов. Тест документирует invariant.
        val fake = FakeLocalAlternative(ActionResult.Success())
        val result = fake.executeLocally(ActionContext(callerId = "test"))
        assertTrue(result is ActionResult.Success)
    }

    @Test
    fun no_exception_thrown() = runTest {
        // INV-3: возвращает Success или Failure, не бросает.
        val failureFake = FakeLocalAlternative(ActionResult.Failure(reason = "boom"))
        val result = failureFake.executeLocally(ActionContext(callerId = "test"))
        assertTrue(result is ActionResult.Failure)
        assertEquals("boom", (result as ActionResult.Failure).reason)
    }
}
