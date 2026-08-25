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
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.car.app.model.Action
import androidx.car.app.model.Header
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.car.app.validation.HostValidator
import autodagger.AutoInjector
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.auto.call.TalkTelecomManager
import com.nextcloud.talk.data.database.dao.ChatMessagesDao
import com.nextcloud.talk.data.database.dao.ConversationsDao
import com.nextcloud.talk.utils.database.user.CurrentUserProvider
import javax.inject.Inject

/** Android Auto entry point for Talk messaging and calling. */
@AutoInjector(NextcloudTalkApplication::class)
class TalkCarAppService : CarAppService() {
    @Inject
    lateinit var currentUserProvider: CurrentUserProvider

    @Inject
    lateinit var conversationsDao: ConversationsDao

    @Inject
    lateinit var chatMessagesDao: ChatMessagesDao

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

    override fun onCreateSession(): Session = TalkCarSession(currentUserProvider, conversationsDao, chatMessagesDao)
}

private class TalkCarSession(
    private val currentUserProvider: CurrentUserProvider,
    private val conversationsDao: ConversationsDao,
    private val chatMessagesDao: ChatMessagesDao
) : Session() {
    override fun onCreateScreen(intent: Intent): Screen =
        TalkCarHomeScreen(carContext, currentUserProvider, conversationsDao, chatMessagesDao)
}

private class TalkCarHomeScreen(
    carContext: CarContext,
    private val currentUserProvider: CurrentUserProvider,
    private val conversationsDao: ConversationsDao,
    private val chatMessagesDao: ChatMessagesDao
) : Screen(carContext) {
    override fun onGetTemplate(): Template {
        val items = ItemList.Builder()
            .addItem(
                Row.Builder()
                    .setTitle("Messages")
                    .addText("Read and reply to recent Talk conversations")
                    .setOnClickListener {
                        screenManager.push(
                            TalkConversationsScreen(
                                carContext,
                                currentUserProvider,
                                conversationsDao,
                                chatMessagesDao
                            )
                        )
                    }
                    .build()
            )
            .addItem(
                Row.Builder()
                    .setTitle("Calls")
                    .addText("Control Talk voice calls through Android Telecom")
                    .setOnClickListener {
                        screenManager.push(
                            TalkCarStatusScreen(
                                carContext,
                                "Calls",
                                "Incoming and active Talk calls are connected to Android Telecom. " +
                                    "Contact-selected outgoing calls are the next call UI layer."
                            )
                        )
                    }
                    .build()
            )
            .build()

        return ListTemplate.Builder()
            .setHeader(
                Header.Builder()
                    .setStartHeaderAction(Action.APP_ICON)
                    .setTitle("Nextcloud Talk")
                    .build()
            )
            .setSingleList(items)
            .build()
    }
}

private class TalkCarStatusScreen(carContext: CarContext, private val title: String, private val status: String) :
    Screen(carContext) {
    override fun onGetTemplate(): Template =
        ListTemplate.Builder()
            .setHeader(
                Header.Builder()
                    .setStartHeaderAction(Action.BACK)
                    .setTitle(title)
                    .build()
            )
            .setSingleList(
                ItemList.Builder()
                    .addItem(Row.Builder().setTitle(status).build())
                    .build()
            )
            .build()
}
