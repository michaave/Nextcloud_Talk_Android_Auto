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
import androidx.car.app.model.Header
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
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

/** Lists recent Talk conversations and opens a dedicated message-history screen. */
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

    override fun onGetTemplate(): Template = try {
        buildTemplate()
    } catch (t: Throwable) {
        Log.e(TAG, "Failed to build Android Auto Messages template", t)
        buildErrorTemplate("Unable to display Talk messages")
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

                    val row = Row.Builder()
                        .setTitle(conversation.displayName)
                        .addText(
                            latestMessage?.let { message ->
                                val sender = if (message.actorId == activeUser.userId) {
                                    "You"
                                } else {
                                    message.actorDisplayName.ifBlank { "Talk user" }
                                }
                                "$sender: ${message.message}"
                            } ?: "No recent messages"
                        )
                        .setBrowsable(true)
                        .setOnClickListener {
                            screenManager.push(
                                TalkConversationHistoryScreen(
                                    carContext,
                                    activeUser,
                                    conversation,
                                    chatMessagesDao
                                )
                            )
                        }
                        .build()

                    itemList.addItem(row)
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
                            .firstOrNull { !it.deleted && it.message.isNotBlank() }

                        ConversationSnapshot(conversation, latestMessage)
                    }

                    loading = false
                    errorMessage = null
                    invalidate()
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
                    .setStartHeaderAction(Action.BACK)
                    .setTitle("Messages")
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
