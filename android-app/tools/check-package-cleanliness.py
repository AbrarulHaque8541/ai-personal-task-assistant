#!/usr/bin/env python3
"""Fail if the checked-out repository still contains any retired Android namespace/package fragment or source path."""
from pathlib import Path
import sys

repo = Path(sys.argv[1]).resolve()
# Build the retired identifier from character codes so the forbidden literal is
# not itself stored in this checker.
old_segment = "".join(chr(code) for code in (99, 117, 101))
old_package = ".".join(("com", old_segment, "daymark"))
old_path = old_package.replace(".", "/")
# Also reject the old namespace prefix and the dotted segment on their own.
# Construct them at runtime so this guard does not preserve the forbidden text.
forbidden_fragments = (".".join(("com", old_segment)), "." + old_segment)
ignored = {".git", ".gradle", "build", "node_modules", "__pycache__"}
matches = []
for path in repo.rglob("*"):
    if not path.is_file() or any(part in ignored for part in path.relative_to(repo).parts):
        continue
    rel = path.relative_to(repo).as_posix()
    if old_path in rel or any(fragment in rel for fragment in forbidden_fragments):
        matches.append(rel)
        continue
    try:
        content = path.read_text(encoding="utf-8")
    except (UnicodeDecodeError, OSError):
        continue
    if old_package in content or old_path in content or any(fragment in content for fragment in forbidden_fragments):
        matches.append(rel)
if matches:
    print("FAIL: retired package identity/path remains in checked-out repository:")
    for item in sorted(set(matches)):
        print(f" - {item}")
    raise SystemExit(1)
# Also assert the new identity is consistent across Gradle and Java source paths.
gradle = repo / "android-app/app/build.gradle.kts"
gradle_text = gradle.read_text(encoding="utf-8")
for marker in (
    'namespace = "com.abrar.daymark"',
    'applicationId = "com.abrar.daymark"',
    'testInstrumentationRunner = "com.abrar.daymark.DaymarkPlatformInstrumentation"',
):
    if marker not in gradle_text:
        print(f"FAIL package identity: missing Gradle marker: {marker}")
        raise SystemExit(1)

source_roots = (
    repo / "android-app/app/src/main/java/com/abrar/daymark",
    repo / "android-app/app/src/githubSideload/java/com/abrar/daymark",
    repo / "android-app/app/src/play/java/com/abrar/daymark",
    repo / "android-app/app/src/androidTest/java/com/abrar/daymark",
)
for source_root in source_roots:
    if not source_root.is_dir():
        print(f"FAIL package identity: missing source root: {source_root.relative_to(repo)}")
        raise SystemExit(1)
    for java_file in source_root.rglob("*.java"):
        relative = java_file.relative_to(source_root.parent.parent.parent.parent)
        expected_package = ".".join(relative.parts[1:-1])
        import re
        match = re.search(r"^package\s+([A-Za-z0-9_.]+)\s*;", java_file.read_text(encoding="utf-8"), re.MULTILINE)
        if not match or match.group(1) != expected_package:
            print(f"FAIL package identity: {java_file.relative_to(repo)} expected package {expected_package}")
            raise SystemExit(1)

print("PASS package cleanliness: no retired namespace fragment/path; Gradle identity and Java package paths are consistent")
