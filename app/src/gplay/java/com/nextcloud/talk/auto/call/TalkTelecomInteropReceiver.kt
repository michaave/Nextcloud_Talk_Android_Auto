/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Michael Avery
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.auto.call

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.nextcloud.talk.call.TalkCallInterop

/** Receives package-local Talk call lifecycle events and mirrors them into Core-Telecom. */
class TalkTelecomInteropReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val manager = TalkTelecomManager.get(context)
        val callKey = intent.getStringExtra(TalkCallInterop.EXTRA_CALL_KEY).orEmpty()

        when (intent.action) {
            TalkCallInterop.ACTION_INCOMING_CALL -> {
                manager.onIncomingCall(
                    callKey = callKey,
                    callExtras = intent.getBundleExtra(TalkCallInterop.EXTRA_CALL_EXTRAS) ?: Bundle(),
                    displayName = intent.getStringExtra(TalkCallInterop.EXTRA_DISPLAY_NAME).orEmpty(),
                    video = intent.getBooleanExtra(TalkCallInterop.EXTRA_VIDEO, false)
                )
            }

            TalkCallInterop.ACTION_CALL_STARTED -> {
                manager.onCallStarted(
                    callKey = callKey,
                    callExtras = intent.getBundleExtra(TalkCallInterop.EXTRA_CALL_EXTRAS) ?: Bundle(),
                    displayName = intent.getStringExtra(TalkCallInterop.EXTRA_DISPLAY_NAME).orEmpty(),
                    incoming = intent.getBooleanExtra(TalkCallInterop.EXTRA_INCOMING, false),
                    video = intent.getBooleanExtra(TalkCallInterop.EXTRA_VIDEO, false)
                )
            }

            TalkCallInterop.ACTION_CALL_ACTIVE -> manager.onCallActive(callKey)
            TalkCallInterop.ACTION_CALL_ENDED -> manager.onCallEnded(callKey)
        }
    }
}
