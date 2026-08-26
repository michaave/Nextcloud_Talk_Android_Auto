/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Michael Avery
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.auto

import android.media.AudioAttributes
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.car.app.CarContext
import androidx.car.app.CarToast
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
import com.nextcloud.talk.data.database.model.ChatMessageEntity
import com.nextcloud.talk.data.database.model.ConversationEntity
import com.nextcloud.talk.data.user.model.User
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.max

/** Displays a driver-safe, paged window of recent Talk messages for one conversation. */
internal class TalkConversationHistoryScreen(
    carContext: CarContext,
    private val activeUser: User,
    private val conversation: ConversationEntity,
    private val chatMessagesDao: ChatMessagesDao
) : Screen(carContext) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    // Stored newest-first so the top of each page is the newest visible message.
    private var messages: List<ChatMessageEntity> = emptyList()
    private var loading = true
    private var errorMessage: String? = null
    private var pageFromNewest = 0
    private var ttsReady = false
    private var textToSpeech: TextToSpeech? = null

    init {
        lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onDestroy(owner: LifecycleOwner) {
                    scope.cancel()
                    textToSpeech?.stop()
                    textToSpeech?.shutdown()
                    textToSpeech = null
                }
            }
        )

        textToSpeech = TextToSpeech(carContext.applicationContext) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
            if (ttsReady) {
                textToSpeech?.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
            } else {
                Log.e(TAG, "TextToSpeech initialization failed with status=$status")
            }
            invalidate()
        }

        observeMessages()
    }

    override fun onGetTemplate(): Template = try {
        buildTemplate()
    } catch (t: Throwable) {
        Log.e(TAG, "Failed to build conversation history for ${conversation.internalId}", t)
        buildSimpleTemplate("Unable to display conversation history")
    }

    private fun buildTemplate(): Template {
        val itemList = ItemList.Builder()

        when {
            loading -> itemList.addItem(Row.Builder().setTitle("Loading messages…").build())
            errorMessage != null -> itemList.addItem(Row.Builder().setTitle(errorMessage!!).build())
            messages.isEmpty() -> itemList.addItem(Row.Builder().setTitle("No recent messages").build())
            else -> addHistoryRows(itemList)
        }

        return ListTemplate.Builder()
            .setHeader(
                Header.Builder()
                    .setStartHeaderAction(Action.BACK)
                    .setTitle(conversation.displayName)
                    .build()
            )
            .setSingleList(itemList.build())
            .build()
    }

    private fun addHistoryRows(itemList: ItemList.Builder) {
        val listLimit = getListContentLimit()
        val pageSize = max(1, listLimit - RESERVED_CONTROL_ROWS)
        val pageCount = ((messages.size + pageSize - 1) / pageSize).coerceAtLeast(1)
        pageFromNewest = pageFromNewest.coerceIn(0, pageCount - 1)

        if (pageFromNewest == 0) {
            itemList.addItem(
                Row.Builder()
                    .setTitle(if (ttsReady) "Read latest message aloud" else "Preparing read aloud…")
                    .addText("Play the newest Talk message through the car audio")
                    .setOnClickListener { speakLatestMessage() }
                    .build()
            )
        }

        val startInclusive = pageFromNewest * pageSize
        val endExclusive = (startInclusive + pageSize).coerceAtMost(messages.size)

        // messages is newest-first, so scrolling down always moves backward in time.
        messages.subList(startInclusive, endExclusive).forEach { message ->
            val sender = if (message.actorId == activeUser.userId) {
                "You"
            } else {
                message.actorDisplayName.ifBlank { "Talk user" }
            }

            itemList.addItem(
                Row.Builder()
                    .setTitle(sender)
                    .addText(message.message)
                    .build()
            )
        }

        if (endExclusive < messages.size) {
            itemList.addItem(
                Row.Builder()
                    .setTitle("Older messages")
                    .addText("Show the previous page")
                    .setOnClickListener {
                        pageFromNewest++
                        invalidate()
                    }
                    .build()
            )
        }

        if (pageFromNewest > 0) {
            itemList.addItem(
                Row.Builder()
                    .setTitle("Newer messages")
                    .addText("Return toward the latest messages")
                    .setOnClickListener {
                        pageFromNewest--
                        invalidate()
                    }
                    .build()
            )
        }
    }

    private fun observeMessages() {
        scope.launch {
            try {
                chatMessagesDao
                    .getMessagesForConversation(conversation.internalId, null)
                    .collectLatest { messageList ->
                        messages = messageList
                            .asSequence()
                            .filter { !it.deleted && it.message.isNotBlank() }
                            .take(MAX_HISTORY_MESSAGES)
                            .toList()

                        loading = false
                        errorMessage = null
                        pageFromNewest = 0
                        invalidate()
                    }
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to load history for ${conversation.internalId}", t)
                loading = false
                errorMessage = "Talk message history is unavailable"
                invalidate()
            }
        }
    }

    private fun speakLatestMessage() {
        val latest = messages.firstOrNull()
        if (latest == null) {
            CarToast.makeText(carContext, "No message to read", CarToast.LENGTH_SHORT).show()
            return
        }

        val tts = textToSpeech
        if (!ttsReady || tts == null) {
            CarToast.makeText(carContext, "Read aloud is not ready yet", CarToast.LENGTH_SHORT).show()
            return
        }

        val sender = if (latest.actorId == activeUser.userId) {
            "You"
        } else {
            latest.actorDisplayName.ifBlank { "Talk user" }
        }

        val spokenText = "$sender says: ${latest.message}"
        val result = tts.speak(
            spokenText,
            TextToSpeech.QUEUE_FLUSH,
            null,
            "talk-auto-${conversation.internalId}-${latest.id}"
        )

        if (result == TextToSpeech.ERROR) {
            Log.e(TAG, "TextToSpeech rejected latest message for ${conversation.internalId}")
            CarToast.makeText(carContext, "Unable to read this message", CarToast.LENGTH_SHORT).show()
        }
    }

    private fun getListContentLimit(): Int = try {
        carContext
            .getCarService(ConstraintManager::class.java)
            .getContentLimit(ConstraintManager.CONTENT_LIMIT_TYPE_LIST)
            .coerceAtLeast(MIN_LIST_LIMIT)
    } catch (t: Throwable) {
        Log.w(TAG, "Unable to query Android Auto list limit; using fallback", t)
        FALLBACK_LIST_LIMIT
    }

    private fun buildSimpleTemplate(message: String): Template =
        ListTemplate.Builder()
            .setHeader(
                Header.Builder()
                    .setStartHeaderAction(Action.BACK)
                    .setTitle(conversation.displayName)
                    .build()
            )
            .setSingleList(
                ItemList.Builder()
                    .addItem(Row.Builder().setTitle(message).build())
                    .build()
            )
            .build()

    companion object {
        private const val TAG = "TalkAuto"
        private const val MAX_HISTORY_MESSAGES = 100
        private const val RESERVED_CONTROL_ROWS = 3
        private const val MIN_LIST_LIMIT = 4
        private const val FALLBACK_LIST_LIMIT = 6
    }
}
