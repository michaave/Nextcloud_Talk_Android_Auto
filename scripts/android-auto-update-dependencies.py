#!/usr/bin/env python3
"""Keep Android Auto-specific dependencies on the selected current versions."""

from pathlib import Path

path = Path("app/build.gradle.kts")
text = path.read_text()
old = '"gplayImplementation"("androidx.core:core-telecom:1.1.0-alpha06")'
new = '"gplayImplementation"("androidx.core:core-telecom:1.1.0-beta01")'

if new not in text:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"core-telecom dependency: expected one alpha06 line, found {count}")
    text = text.replace(old, new, 1)

path.write_text(text)
