/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Michael Avery
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.auto.call

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.telecom.DisconnectCause
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.core.telecom.CallAttributesCompat
import androidx.core.telecom.CallControlScope
import androidx.core.telecom.CallsManager
import com.nextcloud.talk.activities.CallActivity
import com.nextcloud.talk.call.TalkCallInterop
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_CALL_VOICE_ONLY
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_NOTIFICATION_TIMESTAMP
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_ROOM_TOKEN
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * Bridges Talk's existing WebRTC calls into Core-Telecom so Android Auto and
 * other system call surfaces can control them.
 *
 * This class does not own media/signaling. It only mirrors call state and
 * translates Telecom requests into package-local Talk call controls.
 */
class TalkTelecomManager private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val callsManager = CallsManager(appContext)
    private val calls = ConcurrentHashMap<String, ManagedCall>()

    @Volatile
    private var registered = false

    @Synchronized
    fun registerWithTelecom() {
        if (registered) return
        val capabilities =
            CallsManager.CAPABILITY_BASELINE or CallsManager.CAPABILITY_SUPPORTS_VIDEO_CALLING
        callsManager.registerAppWithTelecom(capabilities)
        registered = true
    }

    fun onIncomingCall(
        callKey: String,
        callExtras: Bundle,
        displayName: String,
        video: Boolean
    ) {
        addCallIfNeeded(
            callKey = callKey,
            callExtras = callExtras,
            displayName = displayName,
            incoming = true,
            video = video,
            activityStarted = false
        )
    }

    fun onCallStarted(
        callKey: String,
        callExtras: Bundle,
        displayName: String,
        incoming: Boolean,
        video: Boolean
    ) {
        val existing = calls[callKey]
        if (existing != null) {
            existing.activityStarted = true
            existing.callExtras = Bundle(callExtras)
            existing.control?.let { control ->
                scope.launch { activateStartedCall(existing, control) }
            }
            return
        }

        addCallIfNeeded(
            callKey = callKey,
            callExtras = callExtras,
            displayName = displayName,
            incoming = incoming,
            video = video,
            activityStarted = true
        )
    }

    fun onCallActive(callKey: String) {
        val managed = calls[callKey] ?: return
        managed.control?.let { control ->
            scope.launch {
                if (!managed.incoming) {
                    control.setActive()
                }
            }
        }
    }

    fun onCallEnded(callKey: String) {
        val managed = calls.remove(callKey) ?: return
        managed.control?.let { control ->
            scope.launch {
                runCatching {
                    control.disconnect(DisconnectCause(DisconnectCause.LOCAL))
                }.onFailure { Log.w(TAG, "Telecom disconnect failed for $callKey", it) }
            }
        }
    }

    private suspend fun activateStartedCall(managed: ManagedCall, control: CallControlScope) {
        runCatching {
            when {
                managed.incoming && !managed.answeredByTelecom -> {
                    control.answer(
                        if (managed.video) {
                            CallAttributesCompat.CALL_TYPE_VIDEO_CALL
                        } else {
                            CallAttributesCompat.CALL_TYPE_AUDIO_CALL
                        }
                    )
                }

                !managed.incoming -> control.setActive()
            }
        }.onFailure {
            Log.w(TAG, "Unable to activate Talk call in Telecom: ${managed.callKey}", it)
        }
    }

    private fun addCallIfNeeded(
        callKey: String,
        callExtras: Bundle,
        displayName: String,
        incoming: Boolean,
        video: Boolean,
        activityStarted: Boolean
    ) {
        if (callKey.isBlank() || calls.containsKey(callKey)) return
        registerWithTelecom()

        val managed = ManagedCall(
            callKey = callKey,
            callExtras = Bundle(callExtras),
            displayName = displayName.ifBlank { "Nextcloud Talk" },
            incoming = incoming,
            video = video,
            activityStarted = activityStarted
        )
        if (calls.putIfAbsent(callKey, managed) != null) return

        scope.launch {
            try {
                val roomToken = managed.callExtras.getString(KEY_ROOM_TOKEN).orEmpty()
                val attributes = CallAttributesCompat(
                    displayName = managed.displayName,
                    address = Uri.parse("sip:${Uri.encode(roomToken)}@nextcloud-talk"),
                    direction = if (managed.incoming) {
                        CallAttributesCompat.DIRECTION_INCOMING
                    } else {
                        CallAttributesCompat.DIRECTION_OUTGOING
                    },
                    callType = if (managed.video) {
                        CallAttributesCompat.CALL_TYPE_VIDEO_CALL
                    } else {
                        CallAttributesCompat.CALL_TYPE_AUDIO_CALL
                    },
                    // Talk does not currently expose a true hold operation that stops
                    // both microphone and incoming media, so don't advertise hold yet.
                    callCapabilities = 0
                )

                callsManager.addCall(
                    callAttributes = attributes,
                    onAnswer = { requestedCallType ->
                        managed.answeredByTelecom = true
                        launchTalkCall(
                            managed,
                            voiceOnly = requestedCallType != CallAttributesCompat.CALL_TYPE_VIDEO_CALL
                        )
                    },
                    onDisconnect = {
                        cancelIncomingNotification(managed)
                        if (managed.activityStarted) {
                            TalkCallInterop.requestDisconnect(appContext, managed.callKey)
                        } else {
                            calls.remove(managed.callKey)
                        }
                    },
                    onSetActive = {
                        if (!managed.activityStarted) {
                            managed.answeredByTelecom = managed.incoming
                            launchTalkCall(managed, voiceOnly = !managed.video)
                        }
                    },
                    onSetInactive = {
                        // We intentionally do not advertise hold. If Telecom must make
                        // the call inactive (for example for a cellular call), ending
                        // Talk is safer than leaving WebRTC media active in the car.
                        if (managed.activityStarted) {
                            TalkCallInterop.requestDisconnect(appContext, managed.callKey)
                        } else {
                            calls.remove(managed.callKey)
                        }
                    }
                ) {
                    managed.control = this

                    scope.launch {
                        isMuted
                            .distinctUntilChanged()
                            .collect { muted ->
                                TalkCallInterop.requestMute(appContext, managed.callKey, muted)
                            }
                    }

                    if (managed.activityStarted) {
                        scope.launch { activateStartedCall(managed, this@addCall) }
                    }
                }
            } catch (t: Throwable) {
                calls.remove(callKey)
                Log.e(TAG, "Unable to add Talk call to Telecom: $callKey", t)
            }
        }
    }

    private fun launchTalkCall(managed: ManagedCall, voiceOnly: Boolean) {
        managed.activityStarted = true
        cancelIncomingNotification(managed)

        val extras = Bundle(managed.callExtras).apply {
            putBoolean(KEY_CALL_VOICE_ONLY, voiceOnly)
        }
        appContext.startActivity(
            Intent(appContext, CallActivity::class.java).apply {
                putExtras(extras)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
        )
    }

    private fun cancelIncomingNotification(managed: ManagedCall) {
        val notificationId = managed.callExtras.getInt(KEY_NOTIFICATION_TIMESTAMP, 0)
        if (notificationId != 0) {
            NotificationManagerCompat.from(appContext).cancel(notificationId)
        }
    }

    private data class ManagedCall(
        val callKey: String,
        var callExtras: Bundle,
        val displayName: String,
        val incoming: Boolean,
        val video: Boolean,
        @Volatile var activityStarted: Boolean,
        @Volatile var answeredByTelecom: Boolean = false,
        @Volatile var control: CallControlScope? = null
    )

    companion object {
        private const val TAG = "TalkTelecomManager"

        @Volatile
        private var instance: TalkTelecomManager? = null

        fun get(context: Context): TalkTelecomManager =
            instance ?: synchronized(this) {
                instance ?: TalkTelecomManager(context).also { instance = it }
            }
    }
}
