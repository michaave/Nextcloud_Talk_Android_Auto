/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Michael Avery
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.auto

import android.content.Intent
import android.media.AudioAttributes
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.car.app.CarContext
import androidx.car.app.CarToast
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
import com.nextcloud.talk.chat.ChatActivity
import com.nextcloud.talk.chat.data.network.ChatNetworkDataSource
import com.nextcloud.talk.data.database.dao.ChatMessagesDao
import com.nextcloud.talk.data.database.model.ChatMessageEntity
import com.nextcloud.talk.data.database.model.ConversationEntity
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.utils.bundle.BundleKeys
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_CALL_VOICE_ONLY
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_ROOM_TOKEN
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.max

/** Displays the selected conversation and its driver-safe Talk actions. */
internal class TalkConversationHistoryScreen(
    carContext: CarContext,
    private val activeUser: User,
    private val conversation: ConversationEntity,
    private val chatMessagesDao: ChatMessagesDao,
    private val chatNetworkDataSource: ChatNetworkDataSource
) : Screen(carContext) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var messages: List<ChatMessageEntity> = emptyList()
    private var loading = true
    private var errorMessage: String? = null
    private var pageFromNewest = 0
    private var ttsReady = false
    private var textToSpeech: TextToSpeech? = null
    private val messageImages = mutableMapOf<String, CarIcon>()

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

        if (pageFromNewest == 0) {
            addConversationActions(itemList)
        }

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

    private fun addConversationActions(itemList: ItemList.Builder) {
        itemList.addItem(
            Row.Builder()
                .setTitle("Send message")
                .addText("Dictate or type a new Talk message")
                .setBrowsable(true)
                .setOnClickListener {
                    screenManager.push(
                        TalkMessageComposeScreen(
                            carContext,
                            activeUser,
                            conversation,
                            chatNetworkDataSource
                        )
                    )
                }
                .build()
        )

        if (conversation.canStartCall || conversation.hasCall) {
            itemList.addItem(
                Row.Builder()
                    .setTitle(if (conversation.hasCall) "Join voice call" else "Start voice call")
                    .addText("Call ${conversation.displayName} through Talk")
                    .setOnClickListener { startVoiceCall() }
                    .build()
            )
        }

        if (messages.isNotEmpty()) {
            itemList.addItem(
                Row.Builder()
                    .setTitle(if (ttsReady) "Read latest message aloud" else "Preparing read aloud…")
                    .addText("Play the newest Talk message through the car audio")
                    .setOnClickListener { speakLatestMessage() }
                    .build()
            )
        }
    }

    private fun addHistoryRows(itemList: ItemList.Builder) {
        val listLimit = getListContentLimit()
        val reserved = if (pageFromNewest == 0) RESERVED_FIRST_PAGE_ROWS else RESERVED_PAGING_ROWS
        val pageSize = max(1, listLimit - reserved)
        val pageCount = ((messages.size + pageSize - 1) / pageSize).coerceAtLeast(1)
        pageFromNewest = pageFromNewest.coerceIn(0, pageCount - 1)

        val startInclusive = pageFromNewest * pageSize
        val endExclusive = (startInclusive + pageSize).coerceAtMost(messages.size)

        messages.subList(startInclusive, endExclusive).forEach { message ->
            val sender = if (message.actorId == activeUser.userId) {
                "You"
            } else {
                message.actorDisplayName.ifBlank { "Talk user" }
            }

            val imageName = TalkCarImageLoader.imageAttachmentName(message)
            val rowBuilder = Row.Builder().setTitle(sender)
            val body = when {
                imageName != null && message.message == "{file}" -> imageName
                imageName != null && message.message.isBlank() -> imageName
                message.message == "{file}" -> TalkCarImageLoader.attachmentDisplayName(message) ?: "Attachment"
                else -> message.message
            }
            if (body.isNotBlank()) {
                rowBuilder.addText(body)
            }
            messageImages[message.internalId]?.let { image ->
                rowBuilder.setImage(image, Row.IMAGE_TYPE_LARGE)
            }
            itemList.addItem(rowBuilder.build())
        }

        if (endExclusive < messages.size) {
            itemList.addItem(
                Row.Builder()
                    .setTitle("Older messages")
                    .addText("Show the previous page")
                    .setOnClickListener {
                        pageFromNewest++
                        loadVisibleImages()
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
                        loadVisibleImages()
                        invalidate()
                    }
                    .build()
            )
        }
    }

    private fun startVoiceCall() {
        try {
            val appContext = carContext.applicationContext
            Log.i(
                TAG,
                "Starting phone-side voice call internalId=${conversation.internalId} " +
                    "token=${conversation.token} name=${conversation.displayName} account=${activeUser.id}"
            )
            appContext.startActivity(
                Intent(appContext, ChatActivity::class.java).apply {
                    putExtra(KEY_ROOM_TOKEN, conversation.token)
                    putExtra(BundleKeys.KEY_FROM_NOTIFICATION_START_CALL, true)
                    putExtra(KEY_CALL_VOICE_ONLY, true)
                    putExtra(BundleKeys.KEY_CONVERSATION_DISPLAY_NAME, conversation.displayName)
                    putExtra(BundleKeys.KEY_CONVERSATION_NAME, conversation.name)
                    activeUser.id?.let { putExtra(BundleKeys.KEY_INTERNAL_USER_ID, it) }
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
            CarToast.makeText(
                carContext,
                "Calling ${conversation.displayName}",
                CarToast.LENGTH_SHORT
            ).show()
        } catch (t: Throwable) {
            Log.e(TAG, "Unable to start phone-side Talk voice call", t)
            CarToast.makeText(carContext, "Unable to start Talk call", CarToast.LENGTH_LONG).show()
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
                            .filter {
                                !it.deleted &&
                                    (it.message.isNotBlank() || TalkCarImageLoader.hasImageAttachment(it))
                            }
                            .take(MAX_HISTORY_MESSAGES)
                            .toList()

                        loading = false
                        errorMessage = null
                        pageFromNewest = 0
                        invalidate()
                        loadVisibleImages()
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

    private fun loadVisibleImages() {
        val listLimit = getListContentLimit()
        val reserved = if (pageFromNewest == 0) RESERVED_FIRST_PAGE_ROWS else RESERVED_PAGING_ROWS
        val pageSize = max(1, listLimit - reserved)
        val start = (pageFromNewest * pageSize).coerceAtMost(messages.size)
        val end = (start + pageSize).coerceAtMost(messages.size)
        messages.subList(start, end).forEach { message ->
            if (!TalkCarImageLoader.hasImageAttachment(message) || messageImages.containsKey(message.internalId)) {
                return@forEach
            }
            scope.launch {
                val image = TalkCarImageLoader.loadMessageImage(activeUser, message)
                if (image != null) {
                    messageImages[message.internalId] = image
                    invalidate()
                }
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
        val spokenBody = TalkCarImageLoader.imageAttachmentName(latest)?.let { "an image named $it" }
            ?: if (latest.message == "{file}") {
                TalkCarImageLoader.attachmentDisplayName(latest)?.let { "a file named $it" } ?: "a file"
            } else {
                latest.message
            }

        val result = tts.speak(
            "$sender sent $spokenBody",
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
        private const val RESERVED_FIRST_PAGE_ROWS = 5
        private const val RESERVED_PAGING_ROWS = 2
        private const val MIN_LIST_LIMIT = 4
        private const val FALLBACK_LIST_LIMIT = 6
    }
}
