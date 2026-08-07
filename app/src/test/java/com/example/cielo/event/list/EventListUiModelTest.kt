package com.example.cielo.event.list

import app.cash.turbine.test
import com.example.cielo.MainDispatcherRule
import com.example.cielo.event.GetEventsUseCase
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test

class EventListUiModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `loads the events on start`() = runTest {
        val uiModel = EventListUiModel(GetEventsUseCase())

        uiModel.state.test {
            assertEquals(true, awaitItem().isLoading)

            val loaded = awaitItem()
            assertFalse(loaded.isLoading)
            assertEquals(6, loaded.events.size)
            assertEquals("Festival de Verão", loaded.events.first().name)
            assertEquals(null, loaded.errorMessage)
        }
    }

    @Test
    fun `emits a navigation action when an event is clicked`() = runTest {
        val uiModel = EventListUiModel(GetEventsUseCase())

        uiModel.actions.test {
            uiModel.onEventClick("evt-2")

            assertEquals(EventListUiAction.NavigateToCheckout("evt-2"), awaitItem())
        }
    }
}
