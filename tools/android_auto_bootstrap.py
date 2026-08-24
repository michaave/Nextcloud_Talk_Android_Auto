# SPDX-FileCopyrightText: 2026 Michael Avery
# SPDX-License-Identifier: GPL-3.0-or-later

"""Idempotently add the Android Auto Car App + Core-Telecom foundation."""

from pathlib import Path


def replace_once(text: str, old: str, new: str, description: str) -> str:
    if old not in text:
        raise RuntimeError(f"Could not find {description}")
    return text.replace(old, new, 1)


gradle = Path("app/build.gradle.kts")
text = gradle.read_text()
if "androidx.car.app:app:1.7.0" not in text:
    anchor = 'dependencies {\n    implementation("androidx.media3:media3-session:1.11.0")\n'
    addition = '''dependencies {
    // Android Auto communication UI is isolated to the Google Play flavor.
    "gplayImplementation"("androidx.car.app:app:1.7.0")
    "gplayImplementation"("androidx.car.app:app-projected:1.7.0")
    "gplayImplementation"("androidx.core:core-telecom:1.1.0-alpha06")
    implementation("androidx.media3:media3-session:1.11.0")
'''
    text = replace_once(text, anchor, addition, "dependencies anchor")
    gradle.write_text(text)

manifest = Path("app/src/gplay/AndroidManifest.xml")
text = manifest.read_text()
if "android.permission.MANAGE_OWN_CALLS" not in text:
    marker = '<manifest xmlns:android="http://schemas.android.com/apk/res/android"\n          xmlns:tools="http://schemas.android.com/tools">\n'
    text = replace_once(
        text,
        marker,
        marker + '\n    <uses-permission android:name="android.permission.MANAGE_OWN_CALLS" />\n',
        "gplay manifest root",
    )

if "androidx.car.app.minCarApiLevel" not in text:
    marker = '        <meta-data android:name="google_analytics_adid_collection_enabled" android:value="false" />\n'
    addition = '''

        <!-- ConversationItem and communication templates require Car API 7. -->
        <meta-data
            android:name="androidx.car.app.minCarApiLevel"
            android:value="7" />
'''
    text = replace_once(text, marker, marker + addition, "gplay application metadata")

if ".auto.TalkCarAppService" not in text:
    marker = '        <service\n            android:name=".services.firebase.NCFirebaseMessagingService"'
    service = '''        <service
            android:name=".auto.TalkCarAppService"
            android:exported="true">
            <intent-filter>
                <action android:name="androidx.car.app.CarAppService" />
                <category android:name="androidx.car.app.category.MESSAGING" />
                <category android:name="androidx.car.app.category.CALLING" />
            </intent-filter>
        </service>

'''
    text = replace_once(text, marker, service + marker, "gplay service anchor")
manifest.write_text(text)

descriptor = Path("app/src/gplay/res/xml/automotive_app_desc.xml")
text = descriptor.read_text()
if '<uses name="template" />' not in text:
    text = replace_once(
        text,
        '    <uses name="notification" />\n',
        '    <uses name="notification" />\n    <uses name="template" />\n',
        "Android Auto notification capability",
    )
    descriptor.write_text(text)

service_file = Path("app/src/gplay/java/com/nextcloud/talk/auto/TalkCarAppService.kt")
service_file.parent.mkdir(parents=True, exist_ok=True)
if not service_file.exists():
    service_file.write_text('''/*
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
''')

telecom_file = Path("app/src/gplay/java/com/nextcloud/talk/auto/call/TalkTelecomManager.kt")
telecom_file.parent.mkdir(parents=True, exist_ok=True)
if not telecom_file.exists():
    telecom_file.write_text('''/*
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
''')
