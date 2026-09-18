/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.auto

import com.nextcloud.talk.data.user.model.User
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Test

class TalkCarImageLoaderTest {
    private val user = User(id = 1, baseUrl = "https://cloud.example", username = "alice", token = "secret")
    private val url = "https://cloud.example/avatar?avatarVersion=1"

    @Test
    fun `reopening same image retains cache identity`() {
        assertEquals(TalkCarImageLoader.cacheKey(user, url), TalkCarImageLoader.cacheKey(user.copy(), url))
    }

    @Test
    fun `cached authenticated images are isolated across accounts and credentials`() {
        val key = TalkCarImageLoader.cacheKey(user, url)
        listOf(
            user.copy(id = 2),
            user.copy(username = "bob"),
            user.copy(token = "replacement"),
            user.copy(baseUrl = "https://other.example")
        ).forEach { other -> assertNotEquals(key, TalkCarImageLoader.cacheKey(other, url)) }
        assertFalse(key.contains("secret"))
    }

    @Test
    fun `new avatar version invalidates cached image`() {
        assertNotEquals(
            TalkCarImageLoader.cacheKey(user, url),
            TalkCarImageLoader.cacheKey(user, url.replace("Version=1", "Version=2"))
        )
    }
}
