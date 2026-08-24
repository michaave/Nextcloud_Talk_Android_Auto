/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Michael Avery
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.call

import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_INTERNAL_USER_ID
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_ROOM_TOKEN

/**
 * Flavor-neutral bridge between Talk's WebRTC call lifecycle and optional
 * platform call integrations such as Core-Telecom in the Google Play flavor.
 *
 * The broadcasts are package-restricted, so they never leave this app. On a
 * flavor without a platform integration receiver they are harmless no-ops.
 */
object TalkCallInterop {
    const val ACTION_INCOMING_CALL = "com.nextcloud.talk.call.action.INCOMING"
    const val ACTION_CALL_STARTED = "com.nextcloud.talk.call.action.STARTED"
    const val ACTION_CALL_ACTIVE = "com.nextcloud.talk.call.action.ACTIVE"
    const val ACTION_CALL_ENDED = "com.nextcloud.talk.call.action.ENDED"

    const val ACTION_CONTROL_DISCONNECT = "com.nextcloud.talk.call.action.CONTROL_DISCONNECT"
    const val ACTION_CONTROL_MUTE = "com.nextcloud.talk.call.action.CONTROL_MUTE"

    const val EXTRA_CALL_KEY = "talk_call_key"
    const val EXTRA_CALL_EXTRAS = "talk_call_extras"
    const val EXTRA_DISPLAY_NAME = "talk_display_name"
    const val EXTRA_INCOMING = "talk_incoming"
    const val EXTRA_VIDEO = "talk_video"
    const val EXTRA_MUTED = "talk_muted"

    fun callKey(accountId: Long, roomToken: String): String = "$accountId@$roomToken"

    fun notifyIncomingCall(
        context: Context,
        callExtras: Bundle,
        displayName: String,
        video: Boolean
    ) {
        val accountId = callExtras.getLong(KEY_INTERNAL_USER_ID, -1L)
        val roomToken = callExtras.getString(KEY_ROOM_TOKEN).orEmpty()
        if (accountId < 0L || roomToken.isBlank()) return

        send(
            context,
            Intent(ACTION_INCOMING_CALL)
                .putExtra(EXTRA_CALL_KEY, callKey(accountId, roomToken))
                .putExtra(EXTRA_CALL_EXTRAS, Bundle(callExtras))
                .putExtra(EXTRA_DISPLAY_NAME, displayName)
                .putExtra(EXTRA_INCOMING, true)
                .putExtra(EXTRA_VIDEO, video)
        )
    }

    fun notifyCallStarted(
        context: Context,
        accountId: Long,
        roomToken: String,
        displayName: String,
        incoming: Boolean,
        video: Boolean,
        callExtras: Bundle
    ) {
        if (accountId < 0L || roomToken.isBlank()) return
        send(
            context,
            Intent(ACTION_CALL_STARTED)
                .putExtra(EXTRA_CALL_KEY, callKey(accountId, roomToken))
                .putExtra(EXTRA_CALL_EXTRAS, Bundle(callExtras))
                .putExtra(EXTRA_DISPLAY_NAME, displayName)
                .putExtra(EXTRA_INCOMING, incoming)
                .putExtra(EXTRA_VIDEO, video)
        )
    }

    fun notifyCallActive(context: Context, accountId: Long, roomToken: String) {
        notifySimple(context, ACTION_CALL_ACTIVE, accountId, roomToken)
    }

    fun notifyCallEnded(context: Context, accountId: Long, roomToken: String) {
        notifySimple(context, ACTION_CALL_ENDED, accountId, roomToken)
    }

    fun requestDisconnect(context: Context, callKey: String) {
        send(context, Intent(ACTION_CONTROL_DISCONNECT).putExtra(EXTRA_CALL_KEY, callKey))
    }

    fun requestMute(context: Context, callKey: String, muted: Boolean) {
        send(
            context,
            Intent(ACTION_CONTROL_MUTE)
                .putExtra(EXTRA_CALL_KEY, callKey)
                .putExtra(EXTRA_MUTED, muted)
        )
    }

    private fun notifySimple(context: Context, action: String, accountId: Long, roomToken: String) {
        if (accountId < 0L || roomToken.isBlank()) return
        send(context, Intent(action).putExtra(EXTRA_CALL_KEY, callKey(accountId, roomToken)))
    }

    private fun send(context: Context, intent: Intent) {
        intent.setPackage(context.packageName)
        context.sendBroadcast(intent)
    }
}
