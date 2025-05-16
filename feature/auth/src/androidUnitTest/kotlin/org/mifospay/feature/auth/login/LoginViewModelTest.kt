/*
 * Copyright 2025 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/mobile-wallet/blob/master/LICENSE.md
 */
package org.mifospay.feature.auth.login

import androidx.lifecycle.SavedStateHandle
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.mifospay.core.common.DataState
import org.mifospay.core.domain.LoginUseCase
import org.mifospay.core.model.user.UserInfo
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class MainDispatcherRule : TestWatcher() {
    val testDispatcher = StandardTestDispatcher()

    override fun starting(description: Description) {
        Dispatchers.setMain(testDispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var loginUseCase: LoginUseCase
    private lateinit var savedStateHandle: SavedStateHandle
    private lateinit var viewModel: LoginViewModel

    @Before
    fun setup() {
        loginUseCase = mockk()
        savedStateHandle = SavedStateHandle()
        viewModel = LoginViewModel(
            loginUseCase = loginUseCase,
            savedStateHandle = savedStateHandle,
        )
    }

    @Test
    fun `initial state has empty username and password`() = runTest {
        assertEquals("", viewModel.state.username)
        assertEquals("", viewModel.state.password)
        assertFalse(viewModel.state.isPasswordVisible)
        assertEquals(null, viewModel.state.dialogState)
    }

    @Test
    fun `username change updates state`() = runTest {
        viewModel.handleAction(LoginAction.UsernameChanged("testuser"))
        assertEquals("testuser", viewModel.state.username)
    }

    @Test
    fun `password change updates state`() = runTest {
        viewModel.handleAction(LoginAction.PasswordChanged("testpass"))
        assertEquals("testpass", viewModel.state.password)
    }

    @Test
    fun `toggle password visibility updates state`() = runTest {
        assertFalse(viewModel.state.isPasswordVisible)
        viewModel.handleAction(LoginAction.TogglePasswordVisibility)
        assertTrue(viewModel.state.isPasswordVisible)
        viewModel.handleAction(LoginAction.TogglePasswordVisibility)
        assertFalse(viewModel.state.isPasswordVisible)
    }

    @Test
    fun `successful login navigates to passcode screen`() = runTest {
        // Create a list to collect events
        val collectedEvents = mutableListOf<LoginEvent>()

        // Create a job to collect events
        val job = launch(UnconfinedTestDispatcher()) {
            viewModel.eventFlow.collect {
                collectedEvents.add(it)
            }
        }

        // Given
        val userInfo = mockk<UserInfo>()
        coEvery { loginUseCase(any(), any()) } returns DataState.Success(userInfo)

        // When
        viewModel.handleAction(LoginAction.UsernameChanged("testuser"))
        viewModel.handleAction(LoginAction.PasswordChanged("testpass"))
        viewModel.handleAction(LoginAction.LoginClicked)

        // Advance the dispatcher to complete all coroutines - call it on the test scope
        advanceUntilIdle() // This is directly available in the runTest scope

        // Then
        assertTrue(collectedEvents.any { it is LoginEvent.NavigateToPasscodeScreen })

        // Cleanup
        job.cancel()
    }

    @Test
    fun `failed login shows error dialog`() = runTest {
        // Given
        val errorMessage = "Invalid credentials"
        coEvery { loginUseCase(any(), any()) } returns DataState.Error(Exception(errorMessage))

        // When
        viewModel.handleAction(LoginAction.UsernameChanged("testuser"))
        viewModel.handleAction(LoginAction.PasswordChanged("testpass"))
        viewModel.handleAction(LoginAction.LoginClicked)

        // Advance the dispatcher to complete all coroutines - call it on the test scope
        advanceUntilIdle() // This is directly available in the runTest scope

        // Then
        assertIs<LoginState.DialogState.Error>(viewModel.state.dialogState)
        assertEquals(errorMessage, (viewModel.state.dialogState as LoginState.DialogState.Error).message)
    }
}
