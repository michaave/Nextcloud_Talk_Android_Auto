/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.auto

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.media.AudioFocusRequest
import android.media.AudioManager
import com.nextcloud.talk.call.TalkCallInterop
import com.nextcloud.talk.utils.power.PowerManagerUtils
import com.nextcloud.talk.webrtc.WebRtcAudioManager
import com.nextcloud.talk.webrtc.WebRtcBluetoothManager
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mockConstruction
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class TalkAudioOwnershipTest {
    private val audio = mock<AudioManager>()
    private val context = object : ContextWrapper(RuntimeEnvironment.getApplication()) {
        override fun getSystemService(name: String): Any? =
            if (name == Context.AUDIO_SERVICE) audio else super.getSystemService(name)
    }

    @After
    fun clearAudioOwner() {
        TalkCallInterop.clearTelecomAudioState(context, CALL_KEY)
    }

    @Test
    fun `Telecom call never acquires legacy focus or changes system mode`() {
        withAudioManager { manager, bluetooth ->
            TalkCallInterop.beginTelecomAudioManagement(context, CALL_KEY)
            manager.start { _, _ -> }
            manager.stop()

            verify(audio, never()).requestAudioFocus(any<AudioFocusRequest>())
            verify(audio, never()).mode = any()
            verify(bluetooth, never()).start()
        }
    }

    @Test
    fun `failed Telecom registration restores legacy routing for the ongoing call`() {
        withAudioManager { manager, bluetooth ->
            TalkCallInterop.beginTelecomAudioManagement(context, CALL_KEY)
            manager.start { _, _ -> }
            TalkCallInterop.clearTelecomAudioState(context, CALL_KEY)
            manager.updateAudioDeviceState()

            verify(audio).requestAudioFocus(any<AudioFocusRequest>())
            verify(audio).mode = AudioManager.MODE_IN_COMMUNICATION
            verify(bluetooth).start()
            manager.stop()
            verify(audio).abandonAudioFocusRequest(any())
        }
    }

    private fun withAudioManager(test: (WebRtcAudioManager, WebRtcBluetoothManager) -> Unit) {
        whenever(audio.getDevices(any())).thenReturn(emptyArray())
        mockConstruction(PowerManagerUtils::class.java).use {
            mockConstruction(WebRtcBluetoothManager::class.java).use { bluetooth ->
                val manager = WebRtcAudioManager.create(context, false)
                test(manager, bluetooth.constructed().single())
            }
        }
    }

    companion object {
        private const val CALL_KEY = "1@audio-test"
    }
}
