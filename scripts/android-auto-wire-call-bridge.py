#!/usr/bin/env python3
"""Wire Talk's shared WebRTC call lifecycle into the optional Android Auto Telecom bridge."""

from pathlib import Path


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected exactly one match, found {count}")
    return text.replace(old, new, 1)


def patch_notification_worker() -> None:
    path = Path("app/src/main/java/com/nextcloud/talk/jobs/NotificationWorker.kt")
    text = path.read_text()

    if "import com.nextcloud.talk.call.TalkCallInterop" not in text:
        text = replace_once(
            text,
            "import com.nextcloud.talk.callnotification.CallNotificationActivity\n",
            "import com.nextcloud.talk.call.TalkCallInterop\n"
            "import com.nextcloud.talk.callnotification.CallNotificationActivity\n",
            "NotificationWorker TalkCallInterop import",
        )

    marker = (
        "            val isVideoCall = (conversation.callFlag and Participant.InCallFlags.WITH_VIDEO) > 0\n"
        "            val primaryAnswerIntent = if (isVideoCall) answerVideoPendingIntent else answerVoicePendingIntent\n"
    )
    replacement = (
        "            val isVideoCall = (conversation.callFlag and Participant.InCallFlags.WITH_VIDEO) > 0\n\n"
        "            TalkCallInterop.notifyIncomingCall(\n"
        "                applicationContext,\n"
        "                bundle,\n"
        "                conversation.displayName,\n"
        "                isVideoCall\n"
        "            )\n\n"
        "            val primaryAnswerIntent = if (isVideoCall) answerVideoPendingIntent else answerVoicePendingIntent\n"
    )
    if "TalkCallInterop.notifyIncomingCall(" not in text:
        text = replace_once(text, marker, replacement, "NotificationWorker incoming call hook")

    path.write_text(text)


def patch_call_activity() -> None:
    path = Path("app/src/main/java/com/nextcloud/talk/activities/CallActivity.kt")
    text = path.read_text()

    if "import com.nextcloud.talk.call.TalkCallInterop" not in text:
        text = replace_once(
            text,
            "import com.nextcloud.talk.call.ReactionAnimator\n",
            "import com.nextcloud.talk.call.ReactionAnimator\n"
            "import com.nextcloud.talk.call.TalkCallInterop\n",
            "CallActivity TalkCallInterop import",
        )

    if "import com.nextcloud.talk.utils.registerBroadcastReceiver" not in text:
        text = replace_once(
            text,
            "import com.nextcloud.talk.utils.registerPermissionHandlerBroadcastReceiver\n",
            "import com.nextcloud.talk.utils.registerBroadcastReceiver\n"
            "import com.nextcloud.talk.utils.registerPermissionHandlerBroadcastReceiver\n",
            "CallActivity registerBroadcastReceiver import",
        )

    field_marker = (
        "    private var isIncomingCallFromNotification = false\n"
        "    private val callControlHandler = Handler()\n"
    )
    field_replacement = (
        "    private var isIncomingCallFromNotification = false\n"
        "    private var telecomControlReceiverRegistered = false\n"
        "    private var activeTelecomCallKey: String? = null\n"
        "    private val telecomControlReceiver = object : BroadcastReceiver() {\n"
        "        override fun onReceive(context: Context?, intent: Intent?) {\n"
        "            val action = intent?.action ?: return\n"
        "            val callKey = intent.getStringExtra(TalkCallInterop.EXTRA_CALL_KEY)\n"
        "            if (callKey.isNullOrBlank() || callKey != activeTelecomCallKey) return\n\n"
        "            when (action) {\n"
        "                TalkCallInterop.ACTION_CONTROL_DISCONNECT ->\n"
        "                    hangup(shutDownView = true, endCallForAll = false)\n\n"
        "                TalkCallInterop.ACTION_CONTROL_MUTE -> {\n"
        "                    val shouldMute = intent.getBooleanExtra(TalkCallInterop.EXTRA_MUTED, false)\n"
        "                    if (microphoneOn == shouldMute) {\n"
        "                        onMicrophoneClick()\n"
        "                    }\n"
        "                }\n"
        "            }\n"
        "        }\n"
        "    }\n"
        "    private val callControlHandler = Handler()\n"
    )
    if "telecomControlReceiverRegistered" not in text:
        text = replace_once(text, field_marker, field_replacement, "CallActivity Telecom receiver fields")

    oncreate_marker = (
        "        processExtras(intent.extras!!)\n"
        "        conversationUser = currentUserProviderOld.currentUser.blockingGet()\n\n"
        "        credentials = ApiUtils.getCredentials(conversationUser!!.username, conversationUser!!.token)\n"
    )
    oncreate_replacement = (
        "        processExtras(intent.extras!!)\n"
        "        conversationUser = currentUserProviderOld.currentUser.blockingGet()\n\n"
        "        val telecomAccountId = conversationUser.id\n"
        "        val telecomRoomToken = roomToken\n"
        "        if (telecomAccountId != null && !telecomRoomToken.isNullOrBlank()) {\n"
        "            activeTelecomCallKey = TalkCallInterop.callKey(telecomAccountId, telecomRoomToken)\n"
        "            registerTelecomControlReceiver()\n"
        "            TalkCallInterop.notifyCallStarted(\n"
        "                context = this,\n"
        "                accountId = telecomAccountId,\n"
        "                roomToken = telecomRoomToken,\n"
        "                displayName = conversationName.orEmpty(),\n"
        "                incoming = isIncomingCallFromNotification,\n"
        "                video = !isVoiceOnlyCall,\n"
        "                callExtras = Bundle(extras)\n"
        "            )\n"
        "        }\n\n"
        "        credentials = ApiUtils.getCredentials(conversationUser!!.username, conversationUser!!.token)\n"
    )
    if "TalkCallInterop.notifyCallStarted(" not in text:
        text = replace_once(text, oncreate_marker, oncreate_replacement, "CallActivity call-start hook")

    helper_marker = "    private fun initCallRecordingViewModel(recordingState: Int) {\n"
    helper_replacement = (
        "    private fun registerTelecomControlReceiver() {\n"
        "        if (telecomControlReceiverRegistered) return\n"
        "        val filter = IntentFilter().apply {\n"
        "            addAction(TalkCallInterop.ACTION_CONTROL_DISCONNECT)\n"
        "            addAction(TalkCallInterop.ACTION_CONTROL_MUTE)\n"
        "        }\n"
        "        registerBroadcastReceiver(telecomControlReceiver, filter, ReceiverFlag.NotExported)\n"
        "        telecomControlReceiverRegistered = true\n"
        "    }\n\n"
        "    private fun initCallRecordingViewModel(recordingState: Int) {\n"
    )
    if "private fun registerTelecomControlReceiver()" not in text:
        text = replace_once(text, helper_marker, helper_replacement, "CallActivity Telecom receiver helper")

    destroy_marker = (
        "        CallForegroundService.stop(applicationContext)\n"
        "        powerManagerUtils!!.updatePhoneState(PowerManagerUtils.PhoneState.IDLE)\n"
        "        super.onDestroy()\n"
    )
    destroy_replacement = (
        "        CallForegroundService.stop(applicationContext)\n"
        "        powerManagerUtils!!.updatePhoneState(PowerManagerUtils.PhoneState.IDLE)\n"
        "        if (telecomControlReceiverRegistered) {\n"
        "            unregisterReceiver(telecomControlReceiver)\n"
        "            telecomControlReceiverRegistered = false\n"
        "        }\n"
        "        super.onDestroy()\n"
    )
    if "unregisterReceiver(telecomControlReceiver)" not in text:
        text = replace_once(text, destroy_marker, destroy_replacement, "CallActivity receiver cleanup")

    hangup_marker = (
        "    private fun hangup(shutDownView: Boolean, endCallForAll: Boolean) {\n"
        "        Log.d(TAG, \"hangup! shutDownView=$shutDownView\")\n"
        "        joinRoomInitiated = false\n"
    )
    hangup_replacement = (
        "    private fun hangup(shutDownView: Boolean, endCallForAll: Boolean) {\n"
        "        Log.d(TAG, \"hangup! shutDownView=$shutDownView\")\n"
        "        if (shutDownView && ::conversationUser.isInitialized) {\n"
        "            val accountId = conversationUser.id\n"
        "            val token = roomToken\n"
        "            if (accountId != null && !token.isNullOrBlank()) {\n"
        "                TalkCallInterop.notifyCallEnded(this, accountId, token)\n"
        "            }\n"
        "        }\n"
        "        joinRoomInitiated = false\n"
    )
    if "TalkCallInterop.notifyCallEnded(this, accountId, token)" not in text:
        text = replace_once(text, hangup_marker, hangup_replacement, "CallActivity call-end hook")

    state_marker = (
        "        if (currentCallStatus == null || currentCallStatus !== callState) {\n"
        "            currentCallStatus = callState\n"
        "            if (handler == null) {\n"
    )
    state_replacement = (
        "        if (currentCallStatus == null || currentCallStatus !== callState) {\n"
        "            currentCallStatus = callState\n"
        "            if (\n"
        "                ::conversationUser.isInitialized &&\n"
        "                (callState === CallStatus.JOINED || callState === CallStatus.IN_CONVERSATION)\n"
        "            ) {\n"
        "                val accountId = conversationUser.id\n"
        "                val token = roomToken\n"
        "                if (accountId != null && !token.isNullOrBlank()) {\n"
        "                    TalkCallInterop.notifyCallActive(this, accountId, token)\n"
        "                }\n"
        "            }\n"
        "            if (handler == null) {\n"
    )
    if "TalkCallInterop.notifyCallActive(this, accountId, token)" not in text:
        text = replace_once(text, state_marker, state_replacement, "CallActivity call-active hook")

    path.write_text(text)


def main() -> None:
    patch_notification_worker()
    patch_call_activity()


if __name__ == "__main__":
    main()
