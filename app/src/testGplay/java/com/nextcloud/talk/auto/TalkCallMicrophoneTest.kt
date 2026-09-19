/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.auto

import android.app.Application
import com.nextcloud.talk.activities.CallActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.CALLS_REAL_METHODS
import org.mockito.kotlin.doNothing
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.webrtc.AudioTrack

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class TalkCallMicrophoneTest {
    @Test
    fun `Telecom unmute before audio setup waits for the track`() {
        val activity = mock<CallActivity>(defaultAnswer = CALLS_REAL_METHODS)
        doNothing().whenever(activity).onMicrophoneClick()
        activity.applyTelecomMicrophoneMute(false)
        verify(activity, never()).onMicrophoneClick()
        val pending = CallActivity::class.java.getDeclaredField("pendingTelecomMute").apply { isAccessible = true }
        assertEquals(false, pending.get(activity))

        CallActivity::class.java.getDeclaredField("localAudioTrack").apply { isAccessible = true }
            .set(activity, mock<AudioTrack>())
        activity.applyTelecomMicrophoneMute(pending.get(activity) as Boolean)
        verify(activity).onMicrophoneClick()
        assertNull(pending.get(activity))
    }
}
