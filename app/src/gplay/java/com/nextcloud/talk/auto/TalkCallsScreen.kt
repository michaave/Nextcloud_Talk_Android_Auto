/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Michael Avery
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.auto

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.Header
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.ParkedOnlyOnClickListener
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.nextcloud.talk.chat.ChatActivity
import com.nextcloud.talk.data.database.dao.ConversationsDao
import com.nextcloud.talk.data.database.model.ConversationEntity
import com.nextcloud.talk.models.json.conversations.ConversationEnums
import com.nextcloud.talk.utils.bundle.BundleKeys
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_CALL_VOICE_ONLY
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_ROOM_TOKEN
import com.nextcloud.talk.utils.database.user.CurrentUserProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

internal class TalkCallsScreen(
    carContext: CarContext,
    private val currentUserProvider: CurrentUserProvider,
    private val conversationsDao: ConversationsDao
) : Screen(carContext) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var conversations: List<ConversationEntity> = emptyList()
    private var loading = true
    private var errorMessage: String? = null

    init {
        lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onDestroy(owner: LifecycleOwner) {
                    scope.cancel()
                }
            }
        )
        observeConversations()
    }

    override fun onGetTemplate(): Template {
        val itemList = ItemList.Builder()

        when {
            loading -> itemList.addItem(Row.Builder().setTitle("Loading calls…").build())
            errorMessage != null -> itemList.addItem(Row.Builder().setTitle(errorMessage!!).build())
            conversations.isEmpty() -> {
                itemList.addItem(
                    Row.Builder()
                        .setTitle("No conversations available for calls")
                        .build()
                )
            }

            else -> conversations.forEach { conversation ->
                val row = Row.Builder()
                    .setTitle(conversation.displayName)
                    .addText(if (conversation.hasCall) "Join ongoing Talk call" else "Start voice call")

                if (hasMicrophonePermission()) {
                    row.setOnClickListener { startVoiceCall(conversation) }
                } else {
                    row.setOnClickListener(
                        ParkedOnlyOnClickListener.create {
                            requestMicrophonePermissionAndStart(conversation)
                        }
                    )
                }

                itemList.addItem(row.build())
            }
        }

        return ListTemplate.Builder()
            .setHeader(
                Header.Builder()
                    .setStartHeaderAction(Action.BACK)
                    .setTitle("Calls")
                    .build()
            )
            .setSingleList(itemList.build())
            .build()
    }

    private fun observeConversations() {
        scope.launch {
            try {
                val activeUser = currentUserProvider.getCurrentUser().getOrElse { throw it }
                val accountId = activeUser.id ?: error("Current Talk account has no local ID")

                conversationsDao.getConversationsForUser(accountId).collectLatest { conversationList ->
                    conversations = conversationList
                        .asSequence()
                        .filter(::isCallableConversation)
                        .sortedByDescending(ConversationEntity::lastActivity)
                        .take(MAX_CONVERSATIONS)
                        .toList()
                    loading = false
                    errorMessage = null
                    invalidate()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                loading = false
                errorMessage = "Talk calls are unavailable"
                invalidate()
            }
        }
    }

    private fun hasMicrophonePermission(): Boolean =
        ContextCompat.checkSelfPermission(
            carContext,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

    private fun requestMicrophonePermissionAndStart(conversation: ConversationEntity) {
        carContext.requestPermissions(listOf(Manifest.permission.RECORD_AUDIO)) { grantedPermissions, _ ->
            if (Manifest.permission.RECORD_AUDIO in grantedPermissions) {
                startVoiceCall(conversation)
            } else {
                CarToast.makeText(
                    carContext,
                    "Microphone permission is required for Talk calls",
                    CarToast.LENGTH_LONG
                ).show()
            }
            invalidate()
        }

        CarToast.makeText(
            carContext,
            "Grant microphone access on your phone",
            CarToast.LENGTH_LONG
        ).show()
    }

    private fun startVoiceCall(conversation: ConversationEntity) {
        try {
            carContext.startActivity(
                Intent(carContext, ChatActivity::class.java).apply {
                    putExtra(KEY_ROOM_TOKEN, conversation.token)
                    putExtra(BundleKeys.KEY_FROM_NOTIFICATION_START_CALL, true)
                    putExtra(KEY_CALL_VOICE_ONLY, true)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                }
            )
        } catch (_: ActivityNotFoundException) {
            CarToast.makeText(carContext, "Unable to start Talk call", CarToast.LENGTH_SHORT).show()
        }
    }

    private fun isCallableConversation(conversation: ConversationEntity): Boolean =
        conversation.type != ConversationEnums.ConversationType.DUMMY &&
            conversation.type != ConversationEnums.ConversationType.ROOM_SYSTEM &&
            (conversation.canStartCall || conversation.hasCall)

    companion object {
        private const val MAX_CONVERSATIONS = 10
    }
}
