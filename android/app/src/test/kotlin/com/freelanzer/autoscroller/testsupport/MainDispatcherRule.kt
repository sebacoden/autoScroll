package com.freelanzer.autoscroller.testsupport

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * Reemplaza `Dispatchers.Main` por un [TestDispatcher] durante el test.
 *
 * Expone [testDispatcher] para que los tests pasen su `scheduler` a `runTest`, unificando
 * el reloj virtual del test con el de `viewModelScope`. Así los `launch`/`stateIn` del
 * ViewModel corren de forma determinista (sin flakiness de `WhileSubscribed`).
 */
class MainDispatcherRule(
    val testDispatcher: TestDispatcher = UnconfinedTestDispatcher(),
) : TestWatcher() {

    override fun starting(description: Description) {
        Dispatchers.setMain(testDispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}
