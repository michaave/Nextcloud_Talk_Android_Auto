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
    const val ACTION_CONTROL_AUDIO_ENDPOINT = "com.nextcloud.talk.call.action.CONTROL_AUDIO_ENDPOINT"
    const val ACTION_TELECOM_AUDIO_STATE_CHANGED = "com.nextcloud.talk.call.action.TELECOM_AUDIO_STATE_CHANGED"

    const val AUDIO_ROUTE_EARPIECE = "earpiece"
    const val AUDIO_ROUTE_BLUETOOTH = "bluetooth"
    const val AUDIO_ROUTE_WIRED_HEADSET = "wired_headset"
    const val AUDIO_ROUTE_SPEAKER = "speaker"
    const val AUDIO_ROUTE_EXTERNAL = "external"

    const val EXTRA_CALL_KEY = "talk_call_key"
    const val EXTRA_CALL_EXTRAS = "talk_call_extras"
    const val EXTRA_DISPLAY_NAME = "talk_display_name"
    const val EXTRA_INCOMING = "talk_incoming"
    const val EXTRA_VIDEO = "talk_video"
    const val EXTRA_MUTED = "talk_muted"
    const val EXTRA_AUDIO_ROUTE = "talk_audio_route"

    @Volatile
    private var activeTelecomCallKey: String? = null

    @Volatile
    private var telecomCurrentAudioRoute: String? = null

    @Volatile
    private var telecomAvailableAudioRoutes: Array<String> = emptyArray()

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

    @JvmStatic
    fun requestTelecomAudioRoute(context: Context, route: String) {
        val callKey = activeTelecomCallKey ?: return
        send(
            context,
            Intent(ACTION_CONTROL_AUDIO_ENDPOINT)
                .putExtra(EXTRA_CALL_KEY, callKey)
                .putExtra(EXTRA_AUDIO_ROUTE, route)
        )
    }

    fun beginTelecomAudioManagement(context: Context, callKey: String) {
        if (callKey.isBlank()) return
        activeTelecomCallKey = callKey
        telecomCurrentAudioRoute = null
        telecomAvailableAudioRoutes = emptyArray()
        send(context, Intent(ACTION_TELECOM_AUDIO_STATE_CHANGED).putExtra(EXTRA_CALL_KEY, callKey))
    }

    fun updateTelecomAudioState(
        context: Context,
        callKey: String,
        currentRoute: String?,
        availableRoutes: Array<String>
    ) {
        if (callKey.isBlank()) return
        activeTelecomCallKey = callKey
        telecomCurrentAudioRoute = currentRoute
        telecomAvailableAudioRoutes = availableRoutes.copyOf()
        send(context, Intent(ACTION_TELECOM_AUDIO_STATE_CHANGED).putExtra(EXTRA_CALL_KEY, callKey))
    }

    fun clearTelecomAudioState(context: Context, callKey: String) {
        if (callKey.isBlank() || activeTelecomCallKey != callKey) return
        activeTelecomCallKey = null
        telecomCurrentAudioRoute = null
        telecomAvailableAudioRoutes = emptyArray()
        send(context, Intent(ACTION_TELECOM_AUDIO_STATE_CHANGED).putExtra(EXTRA_CALL_KEY, callKey))
    }

    @JvmStatic
    fun isTelecomAudioManaged(): Boolean = activeTelecomCallKey != null

    @JvmStatic
    fun getTelecomCurrentAudioRoute(): String? = telecomCurrentAudioRoute

    @JvmStatic
    fun getTelecomAvailableAudioRoutes(): Array<String> = telecomAvailableAudioRoutes.copyOf()

    private fun notifySimple(context: Context, action: String, accountId: Long, roomToken: String) {
        if (accountId < 0L || roomToken.isBlank()) return
        send(context, Intent(action).putExtra(EXTRA_CALL_KEY, callKey(accountId, roomToken)))
    }

    private fun send(context: Context, intent: Intent) {
        intent.setPackage(context.packageName)
        context.sendBroadcast(intent)
    }
}
