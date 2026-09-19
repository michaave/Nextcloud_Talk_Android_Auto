<!--
SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
SPDX-License-Identifier: GPL-3.0-or-later
-->

# Android Auto call and image validation

The Android Auto branch incorporates official Talk master through
`6501e73496eebd0c433aab133fcdfa1d20795836` (checked September 19, 2026).
The Google Play package remains `com.nextcloud.talk2.auto`; the existing build
workflow signs its upload bundle with the persistent Talk Auto key.

## Automated coverage

The Android Auto build runs the car regression tests, the upstream avatar
selection tests, and compiles the generic flavor before producing the APK/AAB.
Call regression tests cover registration completing after hangup, replacement
calls in the same room, notification timeout versus answered calls, late car
connection, call collector cleanup, audio ownership and fallback, and unmute
arriving before microphone setup. Image cache tests cover repeat requests,
account separation, credential changes, and avatar version changes.

Full repository `detekt` and `ktlintCheck` also need to be run. These checks
already fail on the unchanged upstream-merged fork; compare the baseline report
before attributing existing issues to a change.

## Vehicle acceptance test

Use the new AAB on the existing Play Console internal testing track. Update the
installed Play testing app without uninstalling it. Test with the vehicle parked.

1. Connect Android Auto, open Talk, and time the first conversation list and first
   actual avatar. Reopen Talk and compare the cached load. Images should appear
   individually while message previews are still loading.
2. Receive a voice call while Maps is displayed. Answer from the vehicle. Confirm
   two-way audio through the vehicle microphone and speakers and a stable call
   for at least two minutes. Test vehicle mute/unmute and hangup.
3. Start an outgoing call from a selected Talk conversation. Confirm the correct
   person/room rings and repeat the audio, mute, and hangup checks.
4. Call the same conversation again immediately after hanging up. Confirm no
   stale call surface or reconnection cycle remains.
5. Decline an incoming call on the phone, then let another incoming call time out.
   Confirm Android Auto also stops ringing. Answer another call on the phone and
   verify removing its incoming notification does not end the accepted call.
6. Start a call on the phone, then connect Android Auto. Confirm audio transfers
   to the vehicle. Disconnect Android Auto and verify the remaining phone audio
   route. Repeat with the server's usual MCU or peer-to-peer call setup.
7. Update a user's avatar and a room avatar, then reopen the car conversation
   list after refresh. Verify updated images and correct avatars after switching
   accounts. Check image attachments and older conversation-history pages.

Call stability and actual loading times require this device/server/vehicle test;
compilation and simulated lifecycle tests do not establish those measurements.

If a call or image fails, capture the relevant log immediately afterward:

```text
adb logcat -d -s TalkTelecomManager:* TalkAuto:* WebRtcAudioManager:*
```

Logs can contain conversation identifiers. Retain only the entries needed to
explain the reproduction.
