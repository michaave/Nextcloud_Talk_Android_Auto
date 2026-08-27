/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Michael Avery
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.auto

import android.util.Log
import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ItemList
import androidx.car.app.model.Row
import androidx.car.app.model.SearchTemplate
import androidx.car.app.model.Template
import com.nextcloud.talk.chat.data.network.ChatNetworkDataSource
import com.nextcloud.talk.data.database.model.ConversationEntity
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.utils.ApiUtils
import com.nextcloud.talk.utils.message.SendMessageUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Driver-safe proactive Talk message composer. The host keyboard provides voice dictation. */
internal class TalkMessageComposeScreen(
    carContext: CarContext,
    private val activeUser: User,
    private val conversation: ConversationEntity,
    private val chatNetworkDataSource: ChatNetworkDataSource
) : Screen(carContext) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var draft = ""
    private var sending = false

    private val callback = object : SearchTemplate.SearchCallback {
        override fun onSearchTextChanged(searchText: String) {
            draft = searchText
            invalidate()
        }

        override fun onSearchSubmitted(searchText: String) {
            draft = searchText.trim()
            if (draft.isNotEmpty() && !sending) {
                sendMessage(draft)
            }
        }
    }

    init {
        lifecycle.addObserver(
            object : androidx.lifecycle.DefaultLifecycleObserver {
                override fun onDestroy(owner: androidx.lifecycle.LifecycleOwner) {
                    scope.cancel()
                }
            }
        )
    }

    override fun onGetTemplate(): Template {
        val resultList = ItemList.Builder()

        when {
            sending -> resultList.addItem(
                Row.Builder()
                    .setTitle("Sending message…")
                    .addText(draft)
                    .build()
            )
            draft.isBlank() -> resultList.addItem(
                Row.Builder()
                    .setTitle("Dictate or type your message")
                    .addText("Use the microphone on the Android Auto keyboard, then submit")
                    .build()
            )
            else -> resultList.addItem(
                Row.Builder()
                    .setTitle("Send to ${conversation.displayName}")
                    .addText(draft)
                    .setOnClickListener { sendMessage(draft) }
                    .build()
            )
        }

        return SearchTemplate.Builder(callback)
            .setHeaderAction(Action.BACK)
            .setSearchHint("Message ${conversation.displayName}")
            .setInitialSearchText(draft)
            .setShowKeyboardByDefault(true)
            .setItemList(resultList.build())
            .build()
    }

    private fun sendMessage(text: String) {
        val message = text.trim()
        if (message.isEmpty() || sending) return

        sending = true
        invalidate()
        scope.launch {
            try {
                val baseUrl = activeUser.baseUrl ?: error("Talk account has no base URL")
                val url = ApiUtils.getUrlForChat(ApiUtils.API_V1, baseUrl, conversation.token)
                chatNetworkDataSource.sendChatMessage(
                    credentials = activeUser.getCredentials(),
                    url = url,
                    message = message,
                    displayName = activeUser.displayName ?: activeUser.userId.orEmpty(),
                    replyTo = 0,
                    sendWithoutNotification = false,
                    referenceId = SendMessageUtils().generateReferenceId(),
                    threadTitle = null
                )
                Log.i(TAG, "Sent proactive Android Auto message to ${conversation.internalId}")
                CarToast.makeText(carContext, "Message sent", CarToast.LENGTH_SHORT).show()
                screenManager.pop()
            } catch (t: Throwable) {
                Log.e(TAG, "Unable to send proactive Android Auto message", t)
                sending = false
                CarToast.makeText(carContext, "Unable to send Talk message", CarToast.LENGTH_LONG).show()
                invalidate()
            }
        }
    }

    companion object {
        private const val TAG = "TalkAuto"
    }
}
