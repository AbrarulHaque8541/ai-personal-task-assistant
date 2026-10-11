#!/bin/sh
set -eu
ROOT="$(CDPATH= cd -- "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

mkdir -p "$TMP/com/cue/daymark"
cp app/src/main/java/com/cue/daymark/NaturalLanguageTaskParse.java "$TMP/com/cue/daymark/"
cat > "$TMP/com/cue/daymark/NlParseSmoke.java" <<'JAVA'
package com.cue.daymark;
import java.time.LocalDate;
final class NlParseSmoke {
    public static void main(String[] args) {
        LocalDate today = LocalDate.of(2026, 10, 11);
        NaturalLanguageTaskParse.Result r =
                NaturalLanguageTaskParse.parse("call dentist tomorrow 4pm high #work", today);
        if (!"call dentist".equals(r.title)) throw new AssertionError("title=" + r.title);
        if (!"2026-10-12".equals(r.dueDate)) throw new AssertionError("due=" + r.dueDate);
        if (!"16:00".equals(r.dueTime)) throw new AssertionError("time=" + r.dueTime);
        if (!"high".equals(r.priority)) throw new AssertionError("pri=" + r.priority);
        if (r.tags.size() != 1 || !"work".equals(r.tags.get(0))) throw new AssertionError("tags=" + r.tags);
        String notes = NaturalLanguageTaskParse.notesWithTags("", r.tags);
        if (!"#work".equals(notes)) throw new AssertionError("notes=" + notes);
        System.out.println("PASS nl-parse smoke");
    }
}
JAVA

mkdir -p "$TMP/out"
javac -encoding UTF-8 -d "$TMP/out" \
  "$TMP/com/cue/daymark/NaturalLanguageTaskParse.java" \
  "$TMP/com/cue/daymark/NlParseSmoke.java"
java -cp "$TMP/out" com.cue.daymark.NlParseSmoke
