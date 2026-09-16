#!/usr/bin/env python3
"""Adapt current upstream Talk call initialization so the Android Auto bridge patch can apply cleanly."""

from pathlib import Path

path = Path("app/src/main/java/com/nextcloud/talk/activities/CallActivity.kt")
text = path.read_text()

old = '''        processExtras(intent.extras!!)
        conversationUser = currentUserProviderOld.currentUser.blockingGet()

        if (warnAndFinishIfCallEndToEndEncryptionUnsupported()) {
            return
        }

        credentials = ApiUtils.getCredentials(conversationUser!!.username, conversationUser!!.token)
'''

new = '''        processExtras(intent.extras!!)
        conversationUser = currentUserProviderOld.currentUser.blockingGet()

        credentials = ApiUtils.getCredentials(conversationUser!!.username, conversationUser!!.token)

        if (warnAndFinishIfCallEndToEndEncryptionUnsupported()) {
            return
        }
'''

if old not in text:
    raise SystemExit("Current upstream CallActivity initialization block was not found")

path.write_text(text.replace(old, new, 1))
