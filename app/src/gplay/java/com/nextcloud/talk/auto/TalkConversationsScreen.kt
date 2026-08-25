/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Michael Avery
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.auto

import android.content.Intent
import android.os.Bundle
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.messaging.model.CarMessage
import androidx.car.app.messaging.model.ConversationCallback
import androidx.car.app.messaging.model.ConversationItem
import androidx.car.app.model.Action
import androidx.car.app.model.CarText
import androidx.car.app.model.Header
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.nextcloud.talk.data.database.dao.ChatMessagesDao
import com.nextcloud.talk.data.database.dao.ConversationsDao
import com.nextcloud.talk.data.database.model.ChatMessageEntity
import com.nextcloud.talk.data.database.model.ConversationEntity
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.models.json.conversations.ConversationEnums
import com.nextcloud.talk.receivers.DirectReplyReceiver
import com.nextcloud.talk.receivers.MarkAsReadReceiver
import com.nextcloud.talk.utils.NotificationUtils
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_INTERNAL_USER_ID
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_MESSAGE_ID
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_ROOM_TOKEN
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_SYSTEM_NOTIFICATION_ID
import com.nextcloud.talk.utils.database.user.CurrentUserProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

internal class TalkConversationsScreen(
    carContext: CarContext,
    private val currentUserProvider: CurrentUserProvider,
    private val conversationsDao: ConversationsDao,
    private val chatMessagesDao: ChatMessagesDao
) : Screen(carContext) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var user: User? = null
    private var snapshots: List<ConversationSnapshot> = emptyList()
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
            loading -> itemList.addItem(Row.Builder().setTitle("Loading conversations…").build())
            errorMessage != null -> itemList.addItem(Row.Builder().setTitle(errorMessage!!).build())
            snapshots.isEmpty() -> itemList.addItem(Row.Builder().setTitle("No recent conversations").build())
            else -> {
                val activeUser = user ?: return buildListTemplate(itemList.build())
                snapshots.forEach { snapshot ->
                    itemList.addItem(buildConversationItem(activeUser, snapshot))
                }
            }
        }

        return buildListTemplate(itemList.build())
    }

    private fun observeConversations() {
        scope.launch {
            try {
                val activeUser = currentUserProvider.getCurrentUser().getOrElse { throw it }
                val accountId = activeUser.id ?: error("Current Talk account has no local ID")
                user = activeUser

                conversationsDao.getConversationsForUser(accountId).collectLatest { conversations ->
                    snapshots = conversations
                        .asSequence()
                        .filter(::isVisibleConversation)
                        .sortedByDescending(ConversationEntity::lastActivity)
                        .take(MAX_CONVERSATIONS)
                        .map { conversation ->
                            val messages = chatMessagesDao
                                .getMessagesForConversation(conversation.internalId, null)
                                .first()
                                .asSequence()
                                .filter { !it.deleted && it.message.isNotBlank() }
                                .take(MAX_MESSAGES_PER_CONVERSATION)
                                .toList()
                                .asReversed()

                            ConversationSnapshot(conversation, messages)
                        }
                        .toList()

                    loading = false
                    errorMessage = null
                    invalidate()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                loading = false
                errorMessage = "Talk conversations are unavailable"
                invalidate()
            }
        }
    }

    private fun buildConversationItem(activeUser: User, snapshot: ConversationSnapshot): ConversationItem {
        val conversation = snapshot.conversation
        val self = Person.Builder()
            .setName(activeUser.displayName ?: activeUser.userId ?: activeUser.username ?: "You")
            .setKey(activeUser.userId ?: activeUser.username ?: activeUser.id?.toString() ?: "self")
            .build()

        val messages = snapshot.messages.map { message ->
            buildCarMessage(activeUser, conversation, message)
        }

        val callback = object : ConversationCallback {
            override fun onMarkAsRead() {
                val newestMessageId = snapshot.messages.lastOrNull()?.id ?: return
                sendMarkAsRead(conversation, newestMessageId)
            }

            override fun onTextReply(replyText: String) {
                if (replyText.isNotBlank() &&
                    conversation.conversationReadOnlyState ==
                    ConversationEnums.ConversationReadOnlyState.CONVERSATION_READ_WRITE
                ) {
                    sendDirectReply(conversation, replyText)
                }
            }
        }

        return ConversationItem.Builder(
            conversation.internalId,
            CarText.create(conversation.displayName),
            self,
            messages,
            callback
        )
            .setGroupConversation(isGroupConversation(conversation))
            .build()
    }

    private fun buildCarMessage(
        activeUser: User,
        conversation: ConversationEntity,
        message: ChatMessageEntity
    ): CarMessage {
        val sentBySelf = message.actorId == activeUser.userId
        val sender = if (sentBySelf) {
            null
        } else {
            Person.Builder()
                .setName(message.actorDisplayName.ifBlank { "Talk user" })
                .setKey("${conversation.internalId}:${message.actorType}:${message.actorId}")
                .build()
        }

        val timestampMillis = if (message.timestamp < TIMESTAMP_MILLISECONDS_THRESHOLD) {
            message.timestamp * 1000L
        } else {
            message.timestamp
        }

        return CarMessage.Builder()
            .setBody(CarText.create(message.message))
            .setRead(sentBySelf || message.id <= conversation.lastReadMessage.toLong())
            .setReceivedTimeEpochMillis(timestampMillis)
            .apply { sender?.let(::setSender) }
            .build()
    }

    private fun sendDirectReply(conversation: ConversationEntity, replyText: String) {
        val intent = Intent(carContext, DirectReplyReceiver::class.java)
            .putExtra(KEY_SYSTEM_NOTIFICATION_ID, 0)
            .putExtra(KEY_ROOM_TOKEN, conversation.token)
            .putExtra(KEY_INTERNAL_USER_ID, conversation.accountId)

        val remoteInput = RemoteInput.Builder(NotificationUtils.KEY_DIRECT_REPLY).build()
        val results = Bundle().apply {
            putCharSequence(NotificationUtils.KEY_DIRECT_REPLY, replyText)
        }
        RemoteInput.addResultsToIntent(arrayOf(remoteInput), intent, results)
        carContext.sendBroadcast(intent)
    }

    private fun sendMarkAsRead(conversation: ConversationEntity, messageId: Long) {
        carContext.sendBroadcast(
            Intent(carContext, MarkAsReadReceiver::class.java)
                .putExtra(KEY_SYSTEM_NOTIFICATION_ID, 0)
                .putExtra(KEY_ROOM_TOKEN, conversation.token)
                .putExtra(KEY_INTERNAL_USER_ID, conversation.accountId)
                .putExtra(KEY_MESSAGE_ID, messageId.toInt())
        )
    }

    private fun buildListTemplate(itemList: ItemList): Template =
        ListTemplate.Builder()
            .setHeader(
                Header.Builder()
                    .setStartHeaderAction(Action.BACK)
                    .setTitle("Messages")
                    .build()
            )
            .setSingleList(itemList)
            .build()

    private fun isVisibleConversation(conversation: ConversationEntity): Boolean =
        conversation.type != ConversationEnums.ConversationType.DUMMY &&
            conversation.type != ConversationEnums.ConversationType.ROOM_SYSTEM

    private fun isGroupConversation(conversation: ConversationEntity): Boolean =
        conversation.type == ConversationEnums.ConversationType.ROOM_GROUP_CALL ||
            conversation.type == ConversationEnums.ConversationType.ROOM_PUBLIC_CALL

    private data class ConversationSnapshot(
        val conversation: ConversationEntity,
        val messages: List<ChatMessageEntity>
    )

    companion object {
        private const val MAX_CONVERSATIONS = 10
        private const val MAX_MESSAGES_PER_CONVERSATION = 5
        private const val TIMESTAMP_MILLISECONDS_THRESHOLD = 10_000_000_000L
    }
}
