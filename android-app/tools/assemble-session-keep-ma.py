#!/usr/bin/env python3
from pathlib import Path
import base64, sys
root = Path(sys.argv[1] if len(sys.argv) > 1 else ".")
blob_dir = root / "tools" / "session-keep-blob"
parts = sorted(blob_dir.glob("*.txt"), key=lambda p: int(p.stem))
if not parts:
    sys.exit("no session-keep-blob parts")
data = "".join(p.read_text() for p in parts)
out = root / "app/src/main/java/com/cue/daymark/MainActivity.java"
out.write_bytes(base64.b64decode(data))
print("wrote", out, out.stat().st_size)
