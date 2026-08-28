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
import com.nextcloud.talk.models.json.conversations.ConversationEnums
import com.nextcloud.talk.utils.ApiUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.Locale
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
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val cache = ConcurrentHashMap<String, CarIcon>()

    suspend fun loadConversationAvatar(user: User, conversation: ConversationEntity): CarIcon? {
        val baseUrl = user.baseUrl ?: return null
        val cacheKey = "avatar:${conversation.internalId}:${conversation.avatarVersion}:${conversation.name}"
        cache[cacheKey]?.let { return it }

        val candidates = buildList {
            if (conversation.type == ConversationEnums.ConversationType.ROOM_TYPE_ONE_TO_ONE_CALL &&
                conversation.name.isNotBlank()
            ) {
                add(ApiUtils.getUrlForAvatar(baseUrl, conversation.name, requestBigSize = false))
            }
            add(
                ApiUtils.getUrlForConversationAvatarWithVersion(
                    AVATAR_API_VERSION,
                    baseUrl,
                    conversation.token,
                    false,
                    conversation.avatarVersion
                )
            )
        }

        candidates.forEachIndexed { index, url ->
            val icon = loadAuthenticatedIcon(user, url, "$cacheKey:$index", cropSquare = true)
            if (icon != null) {
                cache[cacheKey] = icon
                Log.i(TAG, "Loaded car avatar for ${conversation.internalId} from candidate $index")
                return icon
            }
        }

        Log.w(TAG, "No avatar candidate succeeded for ${conversation.internalId}")
        return null
    }

    suspend fun loadMessageImage(user: User, message: ChatMessageEntity): CarIcon? {
        val attachment = findImageAttachment(message) ?: return null
        val baseUrl = user.baseUrl ?: return null
        val identity = attachment.fileId ?: attachment.path ?: attachment.name
        val cacheKey = "message:${message.internalId}:$identity"
        cache[cacheKey]?.let { return it }

        val candidates = buildList {
            attachment.fileId?.takeIf(String::isNotBlank)?.let {
                add(ApiUtils.getUrlForFilePreviewWithFileId(baseUrl, it, PREVIEW_SIZE))
            }
            attachment.path?.takeIf(String::isNotBlank)?.let {
                add(ApiUtils.getUrlForFilePreviewWithRemotePath(baseUrl, it, PREVIEW_SIZE))
            }
        }

        candidates.forEachIndexed { index, url ->
            val icon = loadAuthenticatedIcon(user, url, "$cacheKey:$index", cropSquare = false)
            if (icon != null) {
                cache[cacheKey] = icon
                Log.i(TAG, "Loaded image preview message=${message.internalId} candidate=$index")
                return icon
            }
        }

        Log.w(TAG, "Unable to load image preview for message=${message.internalId} attachment=$attachment")
        return null
    }

    fun imageAttachmentName(message: ChatMessageEntity): String? = findImageAttachment(message)?.name

    fun hasImageAttachment(message: ChatMessageEntity): Boolean = findImageAttachment(message) != null

    fun attachmentDisplayName(message: ChatMessageEntity): String? {
        val parameters = message.messageParameters ?: return null
        parameters.values.forEach { raw ->
            val p = normalize(raw)
            val name = p["name"] ?: p["filename"] ?: p["basename"] ?: p["file"]
            if (!name.isNullOrBlank()) return name
        }
        return null
    }

    private suspend fun loadAuthenticatedIcon(
        user: User,
        url: String,
        cacheKey: String,
        cropSquare: Boolean
    ): CarIcon? = withContext(Dispatchers.IO) {
        try {
            cache[cacheKey]?.let { return@withContext it }
            val request = Request.Builder()
                .url(url)
                .header("Authorization", user.getCredentials())
                .header("OCS-APIRequest", "true")
                .header("Accept", "image/*")
                .header("User-Agent", ApiUtils.userAgent)
                .build()

            client.newCall(request).execute().use { response ->
                val contentType = response.header("Content-Type") ?: ""
                if (!response.isSuccessful) {
                    Log.w(TAG, "Image request failed code=${response.code} type=$contentType url=$url")
                    return@withContext null
                }

                val bytes = response.body?.bytes() ?: return@withContext null
                var bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                if (bitmap == null) {
                    Log.w(TAG, "Image decode failed type=$contentType bytes=${bytes.size} url=$url")
                    return@withContext null
                }
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
        for (rawParameter in parameters.values) {
            val parameter = normalize(rawParameter)
            val name = parameter["name"]
                ?: parameter["filename"]
                ?: parameter["basename"]
                ?: parameter["file"]
                ?: "Image"
            val mimeType = parameter["mimetype"]
                ?: parameter["mime_type"]
                ?: parameter["contenttype"]
                ?: parameter["type"]?.takeIf { it.startsWith("image/", ignoreCase = true) }
            val isImage = mimeType?.startsWith("image/", ignoreCase = true) == true || isImageName(name)
            if (!isImage) continue

            val fileId = parameter["id"]
                ?: parameter["fileid"]
                ?: parameter["file_id"]
            val path = parameter["path"]
                ?: parameter["filepath"]
                ?: parameter["file_path"]
                ?: parameter["link"]?.takeIf { it.startsWith("/") }

            if (fileId.isNullOrBlank() && path.isNullOrBlank()) {
                Log.d(TAG, "Image-like attachment has no file id/path message=${message.internalId} keys=${parameter.keys}")
                continue
            }
            return ImageAttachment(fileId, path, name, mimeType)
        }

        if (message.message == "{file}") {
            Log.d(TAG, "Unparsed file metadata message=${message.internalId} params=${message.messageParameters}")
        }
        return null
    }

    private fun normalize(parameter: Map<String?, String?>): Map<String, String> =
        parameter.entries.mapNotNull { (key, value) ->
            if (key == null || value == null) null else key.lowercase(Locale.ROOT).replace("-", "_") to value
        }.toMap()

    private fun isImageName(name: String): Boolean {
        val lower = name.lowercase(Locale.ROOT)
        return IMAGE_EXTENSIONS.any(lower::endsWith)
    }

    private data class ImageAttachment(
        val fileId: String?,
        val path: String?,
        val name: String,
        val mimeType: String?
    )

    private val IMAGE_EXTENSIONS = setOf(
        ".jpg", ".jpeg", ".png", ".gif", ".webp", ".bmp", ".heic", ".heif", ".avif"
    )
}
