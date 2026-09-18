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
import androidx.car.app.connection.CarConnection
import androidx.core.app.NotificationManagerCompat
import androidx.core.telecom.CallAttributesCompat
import androidx.core.telecom.CallControlResult
import androidx.core.telecom.CallControlScope
import androidx.core.telecom.CallEndpointCompat
import androidx.core.telecom.CallsManager
import androidx.core.telecom.extensions.CallsManagerExtensions
import androidx.core.telecom.extensions.Participant as TelecomParticipant
import androidx.core.telecom.extensions.ParticipantExtension
import androidx.lifecycle.LiveData
import com.nextcloud.talk.activities.CallActivity
import com.nextcloud.talk.call.TalkCallInterop
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_CALL_VOICE_ONLY
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_NOTIFICATION_TIMESTAMP
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_ROOM_TOKEN
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
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
class TalkTelecomManager internal constructor(
    context: Context,
    private val callsManager: CallsManager = CallsManager(context.applicationContext),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
    carConnectionType: LiveData<Int> = CarConnection(context.applicationContext).type,
    private val callExtensions: CallsManagerExtensions = callsManager
) {
    private val appContext = context.applicationContext
    private val calls = ConcurrentHashMap<String, ManagedCall>()

    @Volatile
    private var projectedToCar = false

    @Volatile
    private var registered = false

    init {
        carConnectionType.observeForever { connectionType ->
            val connected = connectionType == CarConnection.CONNECTION_TYPE_PROJECTION
            if (projectedToCar == connected) return@observeForever
            projectedToCar = connected
            Log.i(TAG, "Android Auto projection active=$projectedToCar")
            calls.values.forEach { managed ->
                managed.vehicleRouteRequested = false
                managed.control?.let { control ->
                    control.launch { maybePreferVehicleEndpoint(managed, control) }
                }
            }
        }
    }

    @Synchronized
    fun registerWithTelecom() {
        if (registered) return
        val capabilities =
            CallsManager.CAPABILITY_BASELINE or CallsManager.CAPABILITY_SUPPORTS_VIDEO_CALLING
        callsManager.registerAppWithTelecom(capabilities)
        registered = true
    }

    fun onIncomingCall(callKey: String, callExtras: Bundle, displayName: String, video: Boolean) {
        addCallIfNeeded(
            callKey = callKey,
            callExtras = callExtras,
            displayName = displayName,
            incoming = true,
            video = video,
            activityStarted = false
        )
    }

    fun onCallStarted(callKey: String, callExtras: Bundle, displayName: String, incoming: Boolean, video: Boolean) {
        val existing = calls[callKey]
        if (existing != null) {
            existing.activityStarted = true
            existing.callExtras = Bundle(callExtras)
            existing.control?.let { control ->
                control.launch { activateStartedCall(existing, control) }
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
            control.launch {
                if (!managed.telecomActive && !managed.activationInFlight) {
                    activateStartedCall(managed, control)
                }
            }
        }
    }

    fun onCallEnded(callKey: String) {
        val managed = calls.remove(callKey) ?: return
        TalkCallInterop.clearTelecomAudioState(appContext, callKey)
        managed.control?.let { control ->
            control.launch {
                runCatching {
                    control.disconnect(DisconnectCause(DisconnectCause.LOCAL))
                }.onFailure { Log.w(TAG, "Telecom disconnect failed for $callKey", it) }
            }
        }
    }

    fun onIncomingCallDismissed(callKey: String, notificationId: Int) {
        val managed = calls[callKey] ?: return
        if (managed.incoming &&
            !managed.activityStarted &&
            !managed.answeredByTelecom &&
            managed.callExtras.getInt(KEY_NOTIFICATION_TIMESTAMP, 0) == notificationId
        ) {
            onCallEnded(callKey)
        }
    }

    fun onParticipantsChanged(
        callKey: String,
        participantIds: Array<String>,
        participantNames: Array<String>,
        activeParticipantId: String?
    ) {
        if (callKey.isBlank() || participantIds.size != participantNames.size) return
        val managed = calls[callKey] ?: return
        managed.participants = participantIds.zip(participantNames)
            .mapNotNull { (id, name) ->
                id.takeIf { it.isNotBlank() }?.let { TelecomParticipant(it, name.ifBlank { "Guest" }) }
            }
            .distinctBy(TelecomParticipant::id)
        managed.activeParticipantId = activeParticipantId
        managed.control?.launch { publishParticipantState(managed) }
    }

    fun requestAudioEndpoint(callKey: String, route: String) {
        if (callKey.isBlank() || route.isBlank()) return
        val managed = calls[callKey] ?: return
        val control = managed.control ?: return
        val endpoint = managed.availableEndpoints.firstOrNull { endpoint ->
            routeForEndpoint(endpoint) == route
        } ?: if (route == TalkCallInterop.AUDIO_ROUTE_BLUETOOTH) {
            managed.availableEndpoints.firstOrNull { endpoint ->
                routeForEndpoint(endpoint) == TalkCallInterop.AUDIO_ROUTE_EXTERNAL
            }
        } else {
            null
        }

        if (endpoint == null) {
            Log.w(TAG, "No Telecom endpoint for requested route $route on $callKey")
            return
        }

        managed.vehicleRouteRequested = true
        control.launch {
            runCatching {
                control.requestEndpointChange(endpoint)
            }.onFailure {
                Log.w(TAG, "Unable to request Telecom audio route $route for $callKey", it)
            }
        }
    }

    private suspend fun activateStartedCall(managed: ManagedCall, control: CallControlScope) {
        if (!isCurrentCall(managed) || managed.activationInFlight || managed.telecomActive) return
        managed.activationInFlight = true
        try {
            val result = if (managed.incoming && !managed.answeredByTelecom) {
                control.answer(
                    if (managed.video) {
                        CallAttributesCompat.CALL_TYPE_VIDEO_CALL
                    } else {
                        CallAttributesCompat.CALL_TYPE_AUDIO_CALL
                    }
                )
            } else {
                control.setActive()
            }

            when (result) {
                is CallControlResult.Success -> {
                    if (managed.incoming) {
                        managed.answeredByTelecom = true
                    }
                    managed.telecomActive = true
                    Log.i(TAG, "Telecom call active: ${managed.callKey}")
                    maybePreferVehicleEndpoint(managed, control)
                }
                is CallControlResult.Error ->
                    Log.w(TAG, "Telecom activation rejected for ${managed.callKey}: error=${result.errorCode}")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (t: Exception) {
            Log.w(TAG, "Unable to activate Talk call in Telecom: ${managed.callKey}", t)
        } finally {
            managed.activationInFlight = false
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
                registerWithTelecom()
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

                callExtensions.addCallWithExtensions(
                    callAttributes = attributes,
                    onAnswer = { requestedCallType ->
                        if (!isCurrentCall(managed)) return@addCallWithExtensions
                        managed.answeredByTelecom = true
                        managed.telecomActive = true
                        Log.i(TAG, "Telecom answered ${managed.callKey}")
                        if (!managed.activityStarted) {
                            launchTalkCall(
                                managed,
                                voiceOnly = requestedCallType != CallAttributesCompat.CALL_TYPE_VIDEO_CALL
                            )
                        }
                        managed.control?.let { maybePreferVehicleEndpoint(managed, it) }
                    },
                    onDisconnect = {
                        if (!calls.remove(managed.callKey, managed)) return@addCallWithExtensions
                        cancelIncomingNotification(managed)
                        TalkCallInterop.clearTelecomAudioState(appContext, managed.callKey)
                        if (managed.activityStarted) {
                            TalkCallInterop.requestDisconnect(appContext, managed.callKey)
                        }
                    },
                    onSetActive = {
                        if (!isCurrentCall(managed)) return@addCallWithExtensions
                        managed.telecomActive = true
                        Log.i(TAG, "Telecom requested active for ${managed.callKey}")
                        if (!managed.activityStarted) {
                            managed.answeredByTelecom = managed.incoming
                            launchTalkCall(managed, voiceOnly = !managed.video)
                        }
                        managed.control?.let { maybePreferVehicleEndpoint(managed, it) }
                    },
                    onSetInactive = {
                        // Holding requires suspending both microphone and remote playback.
                        throw UnsupportedOperationException("Talk does not support holding calls")
                    }
                ) {
                    val participantExtension = addParticipantExtension(
                        initialParticipants = managed.participants,
                        initialActiveParticipant = managed.activeParticipant()
                    )
                    onCall {
                        val callControl = this
                        if (!isCurrentCall(managed)) {
                            disconnect(DisconnectCause(DisconnectCause.LOCAL))
                            return@onCall
                        }
                        managed.control = callControl
                        managed.participantExtension = participantExtension
                        publishParticipantState(managed)

                        launch {
                            currentCallEndpoint
                                .distinctUntilChanged()
                                .collect { endpoint ->
                                    managed.currentEndpoint = endpoint
                                    publishAudioState(managed)
                                }
                        }

                        launch {
                            availableEndpoints
                                .distinctUntilChanged()
                                .collect { endpoints ->
                                    managed.availableEndpoints = endpoints
                                    publishAudioState(managed)
                                    maybePreferVehicleEndpoint(managed, callControl)
                                }
                        }

                        launch {
                            isMuted
                                .distinctUntilChanged()
                                .collect { muted ->
                                    if (isCurrentCall(managed)) {
                                        TalkCallInterop.requestMute(appContext, managed.callKey, muted)
                                    }
                                }
                        }

                        if (managed.activityStarted) {
                            launch { activateStartedCall(managed, callControl) }
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (t: Exception) {
                Log.e(TAG, "Unable to add Talk call to Telecom: $callKey", t)
            } finally {
                managed.control = null
                managed.participantExtension = null
                if (calls.remove(callKey, managed)) {
                    TalkCallInterop.clearTelecomAudioState(appContext, callKey)
                }
            }
        }
    }

    private suspend fun maybePreferVehicleEndpoint(managed: ManagedCall, control: CallControlScope) {
        if (!isCurrentCall(managed) ||
            !projectedToCar ||
            !managed.telecomActive ||
            managed.vehicleRouteRequested ||
            managed.vehicleRouteInFlight
        ) {
            return
        }

        val vehicleEndpoint = managed.availableEndpoints.firstOrNull { it.type == CallEndpointCompat.TYPE_BLUETOOTH }
            ?: managed.availableEndpoints.firstOrNull { it.type == CallEndpointCompat.TYPE_STREAMING }
            ?: return

        if (managed.currentEndpoint?.identifier == vehicleEndpoint.identifier) {
            managed.vehicleRouteRequested = true
            return
        }

        managed.vehicleRouteInFlight = true
        try {
            when (val result = control.requestEndpointChange(vehicleEndpoint)) {
                is CallControlResult.Success -> {
                    managed.vehicleRouteRequested = true
                    Log.i(
                        TAG,
                        "Routed ${managed.callKey} to projected vehicle endpoint " +
                            "type=${vehicleEndpoint.type} name=${vehicleEndpoint.name}"
                    )
                }
                is CallControlResult.Error ->
                    Log.w(
                        TAG,
                        "Vehicle endpoint request rejected for ${managed.callKey}: " +
                            "error=${result.errorCode} type=${vehicleEndpoint.type}"
                    )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Unable to route ${managed.callKey} to vehicle endpoint", e)
        } finally {
            managed.vehicleRouteInFlight = false
        }
    }

    private suspend fun publishParticipantState(managed: ManagedCall) {
        val participantExtension = managed.participantExtension ?: return
        participantExtension.updateParticipants(managed.participants)
        participantExtension.updateActiveParticipant(managed.activeParticipant())
    }

    private fun publishAudioState(managed: ManagedCall) {
        if (!isCurrentCall(managed)) return
        val currentRoute = managed.currentEndpoint?.let(::routeForEndpoint)
        val availableRoutes = managed.availableEndpoints
            .mapNotNull(::routeForEndpoint)
            .distinct()
            .toTypedArray()

        TalkCallInterop.updateTelecomAudioState(
            appContext,
            managed.callKey,
            currentRoute,
            availableRoutes
        )
    }

    private fun isCurrentCall(managed: ManagedCall): Boolean = calls[managed.callKey] === managed

    private fun routeForEndpoint(endpoint: CallEndpointCompat): String? =
        when (endpoint.type) {
            CallEndpointCompat.TYPE_EARPIECE -> TalkCallInterop.AUDIO_ROUTE_EARPIECE
            CallEndpointCompat.TYPE_BLUETOOTH -> TalkCallInterop.AUDIO_ROUTE_BLUETOOTH
            CallEndpointCompat.TYPE_WIRED_HEADSET -> TalkCallInterop.AUDIO_ROUTE_WIRED_HEADSET
            CallEndpointCompat.TYPE_SPEAKER -> TalkCallInterop.AUDIO_ROUTE_SPEAKER
            CallEndpointCompat.TYPE_STREAMING -> TalkCallInterop.AUDIO_ROUTE_EXTERNAL
            else -> null
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
        @Volatile var telecomActive: Boolean = false,
        @Volatile var activationInFlight: Boolean = false,
        @Volatile var vehicleRouteRequested: Boolean = false,
        @Volatile var vehicleRouteInFlight: Boolean = false,
        @Volatile var control: CallControlScope? = null,
        @Volatile var participantExtension: ParticipantExtension? = null,
        @Volatile var participants: List<TelecomParticipant> = emptyList(),
        @Volatile var activeParticipantId: String? = null,
        @Volatile var currentEndpoint: CallEndpointCompat? = null,
        @Volatile var availableEndpoints: List<CallEndpointCompat> = emptyList()
    )

    private fun ManagedCall.activeParticipant(): TelecomParticipant? =
        participants.firstOrNull { it.id == activeParticipantId }

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
