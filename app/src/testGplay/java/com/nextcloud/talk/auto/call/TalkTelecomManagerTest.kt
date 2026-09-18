/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.auto.call

import android.app.Application
import android.os.Bundle
import android.os.ParcelUuid
import android.telecom.DisconnectCause
import androidx.car.app.connection.CarConnection
import androidx.core.telecom.CallAttributesCompat
import androidx.core.telecom.CallControlResult
import androidx.core.telecom.CallControlScope
import androidx.core.telecom.CallEndpointCompat
import androidx.core.telecom.extensions.CallsManagerExtensions
import androidx.core.telecom.extensions.ExtensionInitializationScope
import androidx.lifecycle.MutableLiveData
import com.nextcloud.talk.call.TalkCallInterop
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_NOTIFICATION_TIMESTAMP
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_ROOM_TOKEN
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class TalkTelecomManagerTest {
    private val context get() = RuntimeEnvironment.getApplication()

    @After
    fun clearAudioOwner() {
        TalkCallInterop.clearTelecomAudioState(context, CALL_KEY)
    }

    @Test
    fun `call ended during registration is disconnected without activation`() =
        runTest {
            val fixture = fixture()
            fixture.manager.onCallStarted(CALL_KEY, extras(), "Caller", false, false)
            runCurrent()
            fixture.manager.onCallEnded(CALL_KEY)
            fixture.backend.sessions.single().ready.complete(Unit)
            runCurrent()

            val control = fixture.backend.sessions.single().control
            verify(control).disconnect(any())
            verify(control, never()).setActive()
            assertFalse(TalkCallInterop.isTelecomAudioManaged())
        }

    @Test
    fun `old call completion cannot remove replacement for same room`() =
        runTest {
            val fixture = fixture()
            fixture.manager.onIncomingCall(CALL_KEY, extras(), "Caller", false)
            runCurrent()
            fixture.manager.onCallEnded(CALL_KEY)
            fixture.manager.onIncomingCall(CALL_KEY, extras(2), "Caller", false)
            runCurrent()
            fixture.backend.sessions.first().ready.complete(Unit)
            runCurrent()
            fixture.manager.onCallStarted(CALL_KEY, extras(2), "Caller", true, false)
            runCurrent()

            assertEquals(2, fixture.backend.sessions.size)
            fixture.backend.sessions.last().ready.complete(Unit)
            runCurrent()
            verify(fixture.backend.sessions.last().control).answer(CallAttributesCompat.CALL_TYPE_AUDIO_CALL)
        }

    @Test
    fun `ring timeout dismisses only the matching unanswered notification`() =
        runTest {
            val fixture = fixture()
            fixture.manager.onIncomingCall(CALL_KEY, extras(), "Caller", false)
            runCurrent()
            fixture.backend.sessions.single().ready.complete(Unit)
            runCurrent()
            val control = fixture.backend.sessions.single().control

            fixture.manager.onIncomingCallDismissed(CALL_KEY, 2)
            runCurrent()
            verify(control, never()).disconnect(any())
            fixture.manager.onIncomingCallDismissed(CALL_KEY, 1)
            runCurrent()
            verify(control).disconnect(any())
        }

    @Test
    fun `removing answered notification does not disconnect active call`() =
        runTest {
            val fixture = fixture()
            fixture.manager.onIncomingCall(CALL_KEY, extras(), "Caller", false)
            runCurrent()
            fixture.manager.onCallStarted(CALL_KEY, extras(), "Caller", true, false)
            fixture.backend.sessions.single().ready.complete(Unit)
            runCurrent()
            fixture.manager.onIncomingCallDismissed(CALL_KEY, 1)
            runCurrent()
            verify(fixture.backend.sessions.single().control, never()).disconnect(any())
        }

    @Test
    fun `projection arriving after endpoints routes active call once`() =
        runTest {
            val fixture = fixture()
            fixture.manager.onCallStarted(CALL_KEY, extras(), "Caller", false, false)
            runCurrent()
            val session = fixture.backend.sessions.single()
            val bluetooth = endpoint("Van", CallEndpointCompat.TYPE_BLUETOOTH)
            session.endpoints.value = listOf(bluetooth)
            session.ready.complete(Unit)
            runCurrent()
            verify(session.control, never()).requestEndpointChange(any())

            fixture.connection.value = CarConnection.CONNECTION_TYPE_PROJECTION
            runCurrent()
            fixture.manager.onCallActive(CALL_KEY)
            runCurrent()
            verify(session.control).requestEndpointChange(bluetooth)
        }

    @Test
    fun `finished call releases collectors and can be called again`() =
        runTest {
            val fixture = fixture()
            fixture.manager.onCallStarted(CALL_KEY, extras(), "Caller", false, false)
            runCurrent()
            val session = fixture.backend.sessions.single()
            session.ready.complete(Unit)
            runCurrent()
            assertTrue(session.endpoints.subscriptionCount.value > 0)
            session.ended.complete(Unit)
            runCurrent()
            assertEquals(0, session.endpoints.subscriptionCount.value)
            assertFalse(TalkCallInterop.isTelecomAudioManaged())
            fixture.manager.onCallStarted(CALL_KEY, extras(), "Caller", false, false)
            runCurrent()
            assertEquals(2, fixture.backend.sessions.size)
        }

    private fun TestScope.fixture(): Fixture {
        val backend = FakeCalls()
        val connection = MutableLiveData(CarConnection.CONNECTION_TYPE_NOT_CONNECTED)
        val manager = TalkTelecomManager(context, mock(), backgroundScope, connection, backend)
        return Fixture(manager, backend, connection)
    }

    private fun extras(notificationId: Int = 1) =
        Bundle().apply {
            putString(KEY_ROOM_TOKEN, "room")
            putInt(KEY_NOTIFICATION_TIMESTAMP, notificationId)
        }

    private data class Fixture(
        val manager: TalkTelecomManager,
        val backend: FakeCalls,
        val connection: MutableLiveData<Int>
    )

    private class Session {
        val ready = CompletableDeferred<Unit>()
        val ended = CompletableDeferred<Unit>()
        val endpoints = MutableStateFlow(emptyList<CallEndpointCompat>())
        val control = mock<CallControlScope>()
    }

    private class FakeCalls : CallsManagerExtensions {
        val sessions = mutableListOf<Session>()

        override suspend fun addCallWithExtensions(
            callAttributes: CallAttributesCompat,
            onAnswer: suspend (Int) -> Unit,
            onDisconnect: suspend (DisconnectCause) -> Unit,
            onSetActive: suspend () -> Unit,
            onSetInactive: suspend () -> Unit,
            init: suspend ExtensionInitializationScope.() -> Unit
        ) = coroutineScope {
            val session = Session()
            sessions.add(session)
            val extensions = mock<ExtensionInitializationScope>()
            var onCall: suspend CallControlScope.() -> Unit = {}
            whenever(extensions.addParticipantExtension(any(), anyOrNull())).thenReturn(mock())
            doAnswer {
                onCall = it.getArgument(0)
                null
            }.whenever(extensions).onCall(any())
            extensions.init()
            session.ready.await()

            whenever(session.control.coroutineContext).thenReturn(coroutineContext)
            whenever(session.control.currentCallEndpoint).thenReturn(
                MutableStateFlow(endpoint("Phone", CallEndpointCompat.TYPE_EARPIECE))
            )
            whenever(session.control.availableEndpoints).thenReturn(session.endpoints)
            whenever(session.control.isMuted).thenReturn(MutableStateFlow(false))
            whenever(session.control.setActive()).thenReturn(CallControlResult.Success())
            whenever(session.control.answer(any())).thenReturn(CallControlResult.Success())
            whenever(session.control.requestEndpointChange(any())).thenReturn(CallControlResult.Success())
            whenever(session.control.disconnect(any())).thenAnswer {
                session.ended.complete(Unit)
                CallControlResult.Success()
            }
            onCall(session.control)
            session.ended.await()
            coroutineContext.cancelChildren()
        }
    }

    companion object {
        private const val CALL_KEY = "1@room"

        private fun endpoint(name: String, type: Int) = CallEndpointCompat(name, type, ParcelUuid(UUID.randomUUID()))
    }
}
