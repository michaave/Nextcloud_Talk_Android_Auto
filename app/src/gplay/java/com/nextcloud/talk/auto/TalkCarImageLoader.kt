/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Michael Avery
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.auto

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.car.app.model.CarIcon
import androidx.core.graphics.drawable.IconCompat
import androidx.core.graphics.drawable.toBitmap
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.nextcloud.talk.R
import com.nextcloud.talk.conversationlist.ui.AvatarContent
import com.nextcloud.talk.conversationlist.ui.buildAvatarContent
import com.nextcloud.talk.data.database.mappers.toDomainModel
import com.nextcloud.talk.data.database.model.ChatMessageEntity
import com.nextcloud.talk.data.database.model.ConversationEntity
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.utils.ApiUtils
import com.nextcloud.talk.utils.AvatarImageLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.security.MessageDigest
import java.util.Locale

/** Phone-side authenticated image loader for Android Auto. */
internal object TalkCarImageLoader {
    private const val TAG = "TalkAuto"
    private const val AVATAR_SIZE = 128
    private const val PREVIEW_SIZE = 640
    private const val IMAGE_TIMEOUT_MS = 10_000L
    private const val MAX_CONCURRENT_IMAGES = 4

    private val imageRequests = Semaphore(MAX_CONCURRENT_IMAGES)

    suspend fun loadConversationAvatar(context: Context, user: User, conversation: ConversationEntity): CarIcon? {
        if (user.baseUrl == null) return null
        val content = buildAvatarContent(conversation.toDomainModel(), user, isDark = false)
        return when (content) {
            is AvatarContent.Url -> loadAuthenticatedIcon(
                context,
                user,
                content.url,
                cropSquare = true,
                versioned = content.versioned
            )
            is AvatarContent.Res -> resourceIcon(context, content.resId)
            AvatarContent.NoteToSelf -> resourceIcon(context, R.drawable.ic_note_to_self)
            AvatarContent.System -> resourceIcon(context, R.drawable.ic_launcher_foreground)
        }
    }

    suspend fun loadMessageImage(context: Context, user: User, message: ChatMessageEntity): CarIcon? {
        val attachment = findImageAttachment(message) ?: return null
        val baseUrl = user.baseUrl ?: return null

        val candidates = buildList {
            attachment.fileId?.takeIf(String::isNotBlank)?.let {
                add(ApiUtils.getUrlForFilePreviewWithFileId(baseUrl, it, PREVIEW_SIZE))
            }
            attachment.path?.takeIf(String::isNotBlank)?.let {
                add(ApiUtils.getUrlForFilePreviewWithRemotePath(baseUrl, it, PREVIEW_SIZE))
            }
        }

        candidates.forEachIndexed { index, url ->
            val icon = loadAuthenticatedIcon(context, user, url, cropSquare = false)
            if (icon != null) {
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
        context: Context,
        user: User,
        url: String,
        cropSquare: Boolean,
        versioned: Boolean = false
    ): CarIcon? =
        imageRequests.withPermit {
            withTimeoutOrNull(IMAGE_TIMEOUT_MS) {
                val imageLoader = if (versioned) AvatarImageLoader.get(context) else context.imageLoader
                val cacheKey = cacheKey(user, url)
                val request = ImageRequest.Builder(context)
                    .data(url)
                    .memoryCacheKey(cacheKey)
                    .diskCacheKey(cacheKey)
                    .addHeader("Authorization", user.getCredentials())
                    .addHeader("OCS-APIRequest", "true")
                    .addHeader("Accept", "image/*")
                    .size(if (cropSquare) AVATAR_SIZE else PREVIEW_SIZE)
                    .allowHardware(false)
                    .build()
                val result = imageLoader.execute(request) as? SuccessResult ?: return@withTimeoutOrNull null
                withContext(Dispatchers.Default) {
                    createIcon(result.drawable.toBitmap(), cropSquare)
                }
            }
        }

    private fun resourceIcon(context: Context, resource: Int): CarIcon =
        CarIcon.Builder(IconCompat.createWithResource(context, resource)).build()

    private fun createIcon(source: Bitmap, cropSquare: Boolean): CarIcon {
        var bitmap = source
        if (cropSquare && bitmap.width != bitmap.height) {
            val side = minOf(bitmap.width, bitmap.height)
            val x = (bitmap.width - side) / 2
            val y = (bitmap.height - side) / 2
            bitmap = Bitmap.createBitmap(bitmap, x, y, side, side)
        }
        return CarIcon.Builder(IconCompat.createWithBitmap(bitmap)).build()
    }

    internal fun cacheKey(user: User, url: String): String =
        "talk-auto-v2:" + MessageDigest.getInstance("SHA-256")
            .digest(listOf(user.baseUrl, user.id, user.username, user.token, url).joinToString("\n").toByteArray())
            .joinToString("") { "%02x".format(it) }

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
                Log.d(
                    TAG,
                    "Image-like attachment has no file id/path message=${message.internalId} keys=${parameter.keys}"
                )
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

    private data class ImageAttachment(val fileId: String?, val path: String?, val name: String, val mimeType: String?)

    private val IMAGE_EXTENSIONS = setOf(
        ".jpg", ".jpeg", ".png", ".gif", ".webp", ".bmp", ".heic", ".heif", ".avif"
    )
}
