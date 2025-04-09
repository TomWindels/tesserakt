package dev.tesserakt.testing

import dev.tesserakt.testing.Test.Result.Companion.unbox

inline fun runTest(block: () -> Test.Result): Test.Result = runCatching(block).unbox()

inline fun testEnv(block: TestEnvironment.() -> Unit): TestEnvironment = TestEnvironment().apply(block)
