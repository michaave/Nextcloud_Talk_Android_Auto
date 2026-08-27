/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Michael Avery
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.auto

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.car.app.model.CarIcon
import androidx.core.graphics.drawable.IconCompat
import com.nextcloud.talk.data.database.model.ChatMessageEntity
import com.nextcloud.talk.data.database.model.ConversationEntity
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.utils.ApiUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/** Phone-side authenticated image loader for Android Auto. */
internal object TalkCarImageLoader {
    private const val TAG = "TalkAuto"
    private const val AVATAR_API_VERSION = 4
    private const val PREVIEW_SIZE = 640

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val cache = ConcurrentHashMap<String, CarIcon>()

    suspend fun loadConversationAvatar(user: User, conversation: ConversationEntity): CarIcon? {
        val baseUrl = user.baseUrl ?: return null
        val cacheKey = "avatar:${conversation.internalId}:${conversation.avatarVersion}"
        cache[cacheKey]?.let { return it }

        val url = ApiUtils.getUrlForConversationAvatarWithVersion(
            AVATAR_API_VERSION,
            baseUrl,
            conversation.token,
            false,
            conversation.avatarVersion
        )
        return loadAuthenticatedIcon(user, url, cacheKey, cropSquare = true)
    }

    suspend fun loadMessageImage(user: User, message: ChatMessageEntity): CarIcon? {
        val attachment = findImageAttachment(message) ?: return null
        val baseUrl = user.baseUrl ?: return null
        val cacheKey = "message:${message.internalId}:${attachment.fileId}"
        cache[cacheKey]?.let { return it }

        val url = ApiUtils.getUrlForFilePreviewWithFileId(baseUrl, attachment.fileId, PREVIEW_SIZE)
        return loadAuthenticatedIcon(user, url, cacheKey, cropSquare = false)
    }

    fun imageAttachmentName(message: ChatMessageEntity): String? = findImageAttachment(message)?.name

    fun hasImageAttachment(message: ChatMessageEntity): Boolean = findImageAttachment(message) != null

    private suspend fun loadAuthenticatedIcon(
        user: User,
        url: String,
        cacheKey: String,
        cropSquare: Boolean
    ): CarIcon? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(url)
                .header("Authorization", user.getCredentials())
                .header("OCS-APIRequest", "true")
                .header("User-Agent", ApiUtils.userAgent)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "Image request failed ${response.code} for $url")
                    return@withContext null
                }

                val bytes = response.body?.bytes() ?: return@withContext null
                var bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@withContext null
                if (cropSquare && bitmap.width != bitmap.height) {
                    val side = minOf(bitmap.width, bitmap.height)
                    val x = (bitmap.width - side) / 2
                    val y = (bitmap.height - side) / 2
                    bitmap = Bitmap.createBitmap(bitmap, x, y, side, side)
                }

                val icon = CarIcon.Builder(IconCompat.createWithBitmap(bitmap)).build()
                cache[cacheKey] = icon
                icon
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Unable to load Talk car image from $url", t)
            null
        }
    }

    private fun findImageAttachment(message: ChatMessageEntity): ImageAttachment? {
        val parameters = message.messageParameters ?: return null
        for (parameter in parameters.values) {
            val mimeType = parameter["mimetype"]
                ?: parameter["mimeType"]
                ?: parameter["mime-type"]
                ?: continue
            if (!mimeType.startsWith("image/", ignoreCase = true)) continue

            val fileId = parameter["id"]
                ?: parameter["fileId"]
                ?: parameter["file-id"]
                ?: continue
            if (fileId.isBlank()) continue

            val name = parameter["name"]
                ?: parameter["filename"]
                ?: parameter["fileName"]
                ?: "Image"
            return ImageAttachment(fileId, name)
        }
        return null
    }

    private data class ImageAttachment(val fileId: String, val name: String)
}
