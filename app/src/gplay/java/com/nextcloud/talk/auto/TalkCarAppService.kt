/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Michael Avery
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.auto

import android.content.ApplicationInfo
import android.content.Intent
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
import com.nextcloud.talk.auto.call.TalkTelecomManager

/** Android Auto entry point for Talk messaging and calling. */
class TalkCarAppService : CarAppService() {
    override fun onCreate() {
        super.onCreate()
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

    override fun onCreateSession(): Session = TalkCarSession()
}

private class TalkCarSession : Session() {
    override fun onCreateScreen(intent: Intent): Screen = TalkCarHomeScreen(carContext)
}

private class TalkCarHomeScreen(carContext: CarContext) : Screen(carContext) {
    override fun onGetTemplate(): Template {
        val items = ItemList.Builder()
            .addItem(
                Row.Builder()
                    .setTitle("Messages")
                    .addText("Read, reply to, and start Talk conversations")
                    .setOnClickListener {
                        screenManager.push(
                            TalkCarStatusScreen(
                                carContext,
                                "Messages",
                                "Messaging notifications and voice replies are enabled. " +
                                    "Conversation history and contact selection are the next layer."
                            )
                        )
                    }
                    .build()
            )
            .addItem(
                Row.Builder()
                    .setTitle("Calls")
                    .addText("Start and control Talk voice calls")
                    .setOnClickListener {
                        screenManager.push(
                            TalkCarStatusScreen(
                                carContext,
                                "Calls",
                                "Talk is registered with Android Telecom. " +
                                    "The next layer connects Telecom callbacks to Talk WebRTC calls."
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

private class TalkCarStatusScreen(
    carContext: CarContext,
    private val title: String,
    private val status: String
) : Screen(carContext) {
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
