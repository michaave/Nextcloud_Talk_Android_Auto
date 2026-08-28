/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Michael Avery
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.auto

import android.util.Log
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.constraints.ConstraintManager
import androidx.car.app.model.Action
import androidx.car.app.model.CarIcon
import androidx.car.app.model.Header
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.nextcloud.talk.chat.data.network.ChatNetworkDataSource
import com.nextcloud.talk.data.database.dao.ChatMessagesDao
import com.nextcloud.talk.data.database.dao.ConversationsDao
import com.nextcloud.talk.data.database.model.ChatMessageEntity
import com.nextcloud.talk.data.database.model.ConversationEntity
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.models.json.conversations.ConversationEnums
import com.nextcloud.talk.utils.database.user.CurrentUserProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Conversation-first Android Auto home screen. */
internal class TalkConversationsScreen(
    carContext: CarContext,
    private val currentUserProvider: CurrentUserProvider,
    private val conversationsDao: ConversationsDao,
    private val chatMessagesDao: ChatMessagesDao,
    private val chatNetworkDataSource: ChatNetworkDataSource,
    private val isRootScreen: Boolean = false
) : Screen(carContext) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var user: User? = null
    private var snapshots: List<ConversationSnapshot> = emptyList()
    private var loading = true
    private var errorMessage: String? = null
    private val avatarIcons = mutableMapOf<String, CarIcon>()

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

    override fun onGetTemplate(): Template = try {
        buildTemplate()
    } catch (t: Throwable) {
        Log.e(TAG, "Failed to build Android Auto conversation template", t)
        buildErrorTemplate("Unable to display Talk conversations")
    }

    private fun buildTemplate(): Template {
        val itemList = ItemList.Builder()

        when {
            loading -> itemList.addItem(Row.Builder().setTitle("Loading conversations…").build())
            errorMessage != null -> itemList.addItem(Row.Builder().setTitle(errorMessage!!).build())
            snapshots.isEmpty() -> itemList.addItem(Row.Builder().setTitle("No recent conversations").build())
            else -> {
                val activeUser = user ?: return buildListTemplate(itemList.build())
                snapshots.take(getListContentLimit()).forEach { snapshot ->
                    val conversation = snapshot.conversation
                    val latestMessage = snapshot.latestMessage
                    val preview = latestMessage?.let { message ->
                        val sender = if (message.actorId == activeUser.userId) {
                            "You"
                        } else {
                            message.actorDisplayName.ifBlank { "Talk user" }
                        }
                        val body = when {
                            TalkCarImageLoader.hasImageAttachment(message) ->
                                TalkCarImageLoader.imageAttachmentName(message) ?: "Image"
                            message.message == "{file}" ->
                                TalkCarImageLoader.attachmentDisplayName(message) ?: "Attachment"
                            else -> message.message
                        }
                        "$sender: $body"
                    } ?: "No recent messages"

                    val rowBuilder = Row.Builder()
                        .setTitle(
                            if (conversation.unreadMessages > 0) {
                                "${conversation.displayName} (${conversation.unreadMessages})"
                            } else {
                                conversation.displayName
                            }
                        )
                        .addText(preview)
                        .setBrowsable(true)
                        .setOnClickListener {
                            screenManager.push(
                                TalkConversationHistoryScreen(
                                    carContext,
                                    activeUser,
                                    conversation,
                                    chatMessagesDao,
                                    chatNetworkDataSource
                                )
                            )
                        }

                    avatarIcons[conversation.internalId]?.let { icon ->
                        rowBuilder.setImage(icon, Row.IMAGE_TYPE_LARGE)
                    }
                    itemList.addItem(rowBuilder.build())
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
                    val recentConversations = conversations
                        .asSequence()
                        .filter(::isVisibleConversation)
                        .sortedByDescending(ConversationEntity::lastActivity)
                        .take(MAX_CONVERSATIONS)
                        .toList()

                    snapshots = recentConversations.map { conversation ->
                        val latestMessage = chatMessagesDao
                            .getMessagesForConversation(conversation.internalId, null)
                            .first()
                            .firstOrNull {
                                !it.deleted &&
                                    (it.message.isNotBlank() || TalkCarImageLoader.hasImageAttachment(it))
                            }
                        ConversationSnapshot(conversation, latestMessage)
                    }

                    loading = false
                    errorMessage = null
                    invalidate()
                    loadAvatars(activeUser, recentConversations)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                Log.e(TAG, "Failed while loading Android Auto conversations", t)
                loading = false
                errorMessage = "Talk conversations are unavailable"
                invalidate()
            }
        }
    }

    private fun loadAvatars(activeUser: User, conversations: List<ConversationEntity>) {
        conversations.take(getListContentLimit()).forEach { conversation ->
            if (avatarIcons.containsKey(conversation.internalId)) return@forEach
            scope.launch {
                val icon = TalkCarImageLoader.loadConversationAvatar(activeUser, conversation)
                if (icon != null) {
                    avatarIcons[conversation.internalId] = icon
                    invalidate()
                }
            }
        }
    }

    private fun getListContentLimit(): Int = try {
        carContext
            .getCarService(ConstraintManager::class.java)
            .getContentLimit(ConstraintManager.CONTENT_LIMIT_TYPE_LIST)
            .coerceAtLeast(1)
    } catch (t: Throwable) {
        Log.w(TAG, "Unable to query Android Auto conversation list limit; using fallback", t)
        FALLBACK_LIST_LIMIT
    }

    private fun buildListTemplate(itemList: ItemList): Template =
        ListTemplate.Builder()
            .setHeader(
                Header.Builder()
                    .setStartHeaderAction(if (isRootScreen) Action.APP_ICON else Action.BACK)
                    .setTitle("Nextcloud Talk")
                    .build()
            )
            .setSingleList(itemList)
            .build()

    private fun buildErrorTemplate(message: String): Template =
        buildListTemplate(
            ItemList.Builder()
                .addItem(Row.Builder().setTitle(message).build())
                .build()
        )

    private fun isVisibleConversation(conversation: ConversationEntity): Boolean =
        !conversation.hasArchived &&
            conversation.type != ConversationEnums.ConversationType.DUMMY &&
            conversation.type != ConversationEnums.ConversationType.ROOM_SYSTEM

    private data class ConversationSnapshot(
        val conversation: ConversationEntity,
        val latestMessage: ChatMessageEntity?
    )

    companion object {
        private const val TAG = "TalkAuto"
        private const val MAX_CONVERSATIONS = 20
        private const val FALLBACK_LIST_LIMIT = 6
    }
}
