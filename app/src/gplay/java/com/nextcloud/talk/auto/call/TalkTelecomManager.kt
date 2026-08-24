/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Michael Avery
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.auto.call

import android.content.Context
import androidx.core.telecom.CallsManager

/** Owns Core-Telecom registration for the Android Auto capable build. */
class TalkTelecomManager private constructor(context: Context) {
    val callsManager = CallsManager(context.applicationContext)

    @Volatile
    private var registered = false

    @Synchronized
    fun registerWithTelecom() {
        if (registered) return
        val capabilities =
            CallsManager.CAPABILITY_BASELINE or CallsManager.CAPABILITY_SUPPORTS_VIDEO_CALLING
        callsManager.registerAppWithTelecom(capabilities)
        registered = true
    }

    companion object {
        @Volatile
        private var instance: TalkTelecomManager? = null

        fun get(context: Context): TalkTelecomManager =
            instance ?: synchronized(this) {
                instance ?: TalkTelecomManager(context).also { instance = it }
            }
    }
}
