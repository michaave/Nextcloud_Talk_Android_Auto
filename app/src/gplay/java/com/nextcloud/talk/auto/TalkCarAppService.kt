/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Michael Avery
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.auto

import android.content.Intent
import android.content.pm.ApplicationInfo
import androidx.car.app.CarAppService
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.car.app.validation.HostValidator
import autodagger.AutoInjector
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.auto.call.TalkTelecomManager
import com.nextcloud.talk.chat.data.network.ChatNetworkDataSource
import com.nextcloud.talk.data.database.dao.ChatMessagesDao
import com.nextcloud.talk.data.database.dao.ConversationsDao
import com.nextcloud.talk.utils.database.user.CurrentUserProvider
import javax.inject.Inject

/** Android Auto entry point for Talk messaging and calling. */
@AutoInjector(NextcloudTalkApplication::class)
class TalkCarAppService : CarAppService() {
    @Inject lateinit var currentUserProvider: CurrentUserProvider
    @Inject lateinit var conversationsDao: ConversationsDao
    @Inject lateinit var chatMessagesDao: ChatMessagesDao
    @Inject lateinit var chatNetworkDataSource: ChatNetworkDataSource

    override fun onCreate() {
        super.onCreate()
        NextcloudTalkApplication.sharedApplication!!.componentApplication.inject(this)
        TalkTelecomManager.get(applicationContext).registerWithTelecom()
    }

    override fun createHostValidator(): HostValidator =
        if ((applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
            HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
        } else {
            HostValidator.Builder(this)
                .addAllowedHosts(androidx.car.app.R.array.hosts_allowlist_sample)
                .build()
        }

    override fun onCreateSession(): Session = TalkCarSession(
        currentUserProvider,
        conversationsDao,
        chatMessagesDao,
        chatNetworkDataSource
    )
}

private class TalkCarSession(
    private val currentUserProvider: CurrentUserProvider,
    private val conversationsDao: ConversationsDao,
    private val chatMessagesDao: ChatMessagesDao,
    private val chatNetworkDataSource: ChatNetworkDataSource
) : Session() {
    override fun onCreateScreen(intent: Intent): Screen =
        TalkConversationsScreen(
            carContext,
            currentUserProvider,
            conversationsDao,
            chatMessagesDao,
            chatNetworkDataSource,
            isRootScreen = true
        )
}
