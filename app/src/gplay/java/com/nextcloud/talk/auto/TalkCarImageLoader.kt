/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Michael Avery
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.auto

import android.content.Context
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
import java.io.File
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/** Phone-side authenticated image loader for Android Auto. */
internal object TalkCarImageLoader {
    private const val TAG = "TalkAuto"
    private const val AVATAR_API_VERSION = 4
    private const val PREVIEW_SIZE = 640

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val cache = ConcurrentHashMap<String, CarIcon>()

    suspend fun loadConversationAvatar(context: Context, user: User, conversation: ConversationEntity): CarIcon? {
        val baseUrl = user.baseUrl ?: return null
        val cacheKey = "avatar:${conversation.internalId}:${conversation.avatarVersion}:${conversation.name}"
        cache[cacheKey]?.let { return it }

        val candidates = buildList {
            // The versioned Talk avatar works for every conversation type. Try it first;
            // the old ordering could wait for a failed user-avatar request before falling back.
            add(
                ApiUtils.getUrlForConversationAvatarWithVersion(
                    AVATAR_API_VERSION,
                    baseUrl,
                    conversation.token,
                    false,
                    conversation.avatarVersion
                )
            )
            if (conversation.type == ConversationEnums.ConversationType.ROOM_TYPE_ONE_TO_ONE_CALL &&
                conversation.name.isNotBlank()
            ) {
                add(ApiUtils.getUrlForAvatar(baseUrl, conversation.name, requestBigSize = false))
            }
        }

        candidates.forEachIndexed { index, url ->
            val icon = loadAuthenticatedIcon(context, user, url, cacheKey, cropSquare = true)
            if (icon != null) {
                cache[cacheKey] = icon
                Log.i(TAG, "Loaded car avatar for ${conversation.internalId} from candidate $index")
                return icon
            }
        }

        Log.w(TAG, "No avatar candidate succeeded for ${conversation.internalId}")
        return null
    }

    suspend fun loadMessageImage(context: Context, user: User, message: ChatMessageEntity): CarIcon? {
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
            val icon = loadAuthenticatedIcon(context, user, url, cacheKey, cropSquare = false)
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
        context: Context,
        user: User,
        url: String,
        cacheKey: String,
        cropSquare: Boolean
    ): CarIcon? = withContext(Dispatchers.IO) {
        try {
            cache[cacheKey]?.let { return@withContext it }

            val diskFile = diskCacheFile(context, cacheKey)
            if (diskFile.isFile) {
                val cachedBytes = runCatching { diskFile.readBytes() }.getOrNull()
                val cachedIcon = cachedBytes?.let { decodeIcon(it, cropSquare) }
                if (cachedIcon != null) {
                    cache[cacheKey] = cachedIcon
                    Log.d(TAG, "Loaded Talk car image from disk cache key=$cacheKey")
                    return@withContext cachedIcon
                }
                runCatching { diskFile.delete() }
            }

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
                val icon = decodeIcon(bytes, cropSquare)
                if (icon == null) {
                    Log.w(TAG, "Image decode failed type=$contentType bytes=${bytes.size} url=$url")
                    return@withContext null
                }

                runCatching {
                    diskFile.parentFile?.mkdirs()
                    val tempFile = File(diskFile.parentFile, "${diskFile.name}.tmp")
                    tempFile.writeBytes(bytes)
                    if (!tempFile.renameTo(diskFile)) {
                        diskFile.writeBytes(bytes)
                        tempFile.delete()
                    }
                }.onFailure {
                    Log.d(TAG, "Unable to persist Talk car image cache key=$cacheKey", it)
                }

                cache[cacheKey] = icon
                icon
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Unable to load Talk car image from $url", t)
            null
        }
    }

    private fun decodeIcon(bytes: ByteArray, cropSquare: Boolean): CarIcon? {
        var bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
        if (cropSquare && bitmap.width != bitmap.height) {
            val side = minOf(bitmap.width, bitmap.height)
            val x = (bitmap.width - side) / 2
            val y = (bitmap.height - side) / 2
            bitmap = Bitmap.createBitmap(bitmap, x, y, side, side)
        }
        return CarIcon.Builder(IconCompat.createWithBitmap(bitmap)).build()
    }

    private fun diskCacheFile(context: Context, cacheKey: String): File {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(cacheKey.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return File(File(context.cacheDir, "talk_auto_images"), "$digest.img")
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
