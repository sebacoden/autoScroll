package com.freelanzer.autoscroller.ui.eula

import com.freelanzer.autoscroller.testsupport.FakeSettingsRepository
import com.freelanzer.autoscroller.testsupport.MainDispatcherRule
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Tests del [EulaViewModel] contra un [FakeSettingsRepository] en memoria: persiste el
 * flag y avisa al caller. Determinista, sin DataStore.
 */
class EulaViewModelTest {

    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeSettingsRepository()
    private lateinit var viewModel: EulaViewModel

    @Before
    fun setUp() {
        viewModel = EulaViewModel(repository)
    }

    @Test
    fun `acceptEula persists flag and invokes callback`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            var callbackInvoked = false
            viewModel.acceptEula { callbackInvoked = true }

            assertThat(repository.eulaAcceptedFlow.value).isTrue()
            assertThat(callbackInvoked).isTrue()
        }

    @Test
    fun `flag is false before acceptEula is called`() {
        assertThat(repository.eulaAcceptedFlow.value).isFalse()
    }
}
