#!/usr/bin/env python3
"""Keep Android Auto-specific dependencies on the latest published versions."""

from pathlib import Path

path = Path("app/build.gradle.kts")
text = path.read_text()
target = '"gplayImplementation"("androidx.core:core-telecom:1.1.0-alpha06")'
unpublished = '"gplayImplementation"("androidx.core:core-telecom:1.1.0-beta01")'

if target not in text:
    count = text.count(unpublished)
    if count != 1:
        raise SystemExit(f"core-telecom dependency: expected one beta01 line to restore, found {count}")
    text = text.replace(unpublished, target, 1)

path.write_text(text)
