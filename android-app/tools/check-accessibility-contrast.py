#!/usr/bin/env python3
"""Check text contrast in the Android app's declared palette values."""

from __future__ import annotations

import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parents[1]
SOURCE = ROOT / "app/src/main/java/com/cue/daymark/MainActivity.java"
MINIMUM_RATIO = 4.5
SURFACE_FIELDS = ("background", "surface", "surfaceAlt", "suggestionSurface", "accentSoft", "dangerSoft")
TEXT_FIELDS = ("text", "muted", "accent", "warning", "danger")
COLOR_PATTERN = re.compile(r"Color\.rgb\(\s*(\d+)\s*,\s*(\d+)\s*,\s*(\d+)\s*\)|Color\.(WHITE|BLACK)")


def block_after(source: str, marker: str) -> str:
    start = source.index(marker) + len(marker)
    opening = source.index("{", start - 1)
    depth = 0
    for index in range(opening, len(source)):
        if source[index] == "{":
            depth += 1
        elif source[index] == "}":
            depth -= 1
            if depth == 0:
                return source[opening + 1:index]
    raise AssertionError(f"Unclosed source block after {marker!r}")


def parse_color(token: re.Match[str]) -> tuple[int, int, int]:
    if token.group(4) == "WHITE":
        return (255, 255, 255)
    if token.group(4) == "BLACK":
        return (0, 0, 0)
    return tuple(int(token.group(i)) for i in (1, 2, 3))


def palette_values(branch: str, high_contrast: bool) -> dict[str, tuple[int, int, int]]:
    values: dict[str, tuple[int, int, int]] = {}
    fields = set(SURFACE_FIELDS + TEXT_FIELDS + ("line",))
    for field in fields:
        assignment = re.search(rf"^\s*{field}\s*=\s*([^;]+);", branch, re.MULTILINE)
        if assignment is None:
            raise AssertionError(f"Palette field {field!r} was not found")
        expression = assignment.group(1)
        choices = [parse_color(match) for match in COLOR_PATTERN.finditer(expression)]
        if "?" in expression:
            if len(choices) != 2:
                raise AssertionError(f"Could not resolve both {field!r} palette branches: {expression}")
            values[field] = choices[0] if high_contrast else choices[1]
        else:
            if len(choices) != 1:
                raise AssertionError(f"Could not resolve {field!r} palette value: {expression}")
            values[field] = choices[0]
    return values


def relative_luminance(color: tuple[int, int, int]) -> float:
    channels = []
    for value in color:
        channel = value / 255.0
        channels.append(channel / 12.92 if channel <= 0.04045 else ((channel + 0.055) / 1.055) ** 2.4)
    return 0.2126 * channels[0] + 0.7152 * channels[1] + 0.0722 * channels[2]


def contrast_ratio(first: tuple[int, int, int], second: tuple[int, int, int]) -> float:
    lighter, darker = sorted((relative_luminance(first), relative_luminance(second)), reverse=True)
    return (lighter + 0.05) / (darker + 0.05)


def main() -> int:
    source = SOURCE.read_text(encoding="utf-8")
    palette_start = source.index("private static final class Palette")
    palette_source = source[palette_start:source.index("static Palette from", palette_start)]
    dark_branch = block_after(palette_source, "if (dark)")
    else_marker = palette_source.index("} else {")
    light_branch = block_after(palette_source[else_marker + 1:], "else")

    failures: list[str] = []
    minimum_seen = float("inf")
    for appearance, branch in (("dark", dark_branch), ("light", light_branch)):
        for contrast_mode in (False, True):
            palette = palette_values(branch, contrast_mode)
            mode = f"{appearance} / {'high contrast' if contrast_mode else 'standard'}"
            for foreground_name in TEXT_FIELDS:
                for surface_name in SURFACE_FIELDS:
                    ratio = contrast_ratio(palette[foreground_name], palette[surface_name])
                    minimum_seen = min(minimum_seen, ratio)
                    if ratio + 1e-9 < MINIMUM_RATIO:
                        failures.append(
                            f"{mode}: {foreground_name} on {surface_name} is {ratio:.2f}:1"
                        )

            # The filled primary button uses dark background text in dark mode and white text in light mode.
            button_text = palette["background"] if appearance == "dark" else (255, 255, 255)
            ratio = contrast_ratio(button_text, palette["accent"])
            minimum_seen = min(minimum_seen, ratio)
            if ratio + 1e-9 < MINIMUM_RATIO:
                failures.append(f"{mode}: primary-button label on accent is {ratio:.2f}:1")

    if failures:
        print("FAIL palette contrast (small-text target: 4.5:1):")
        for failure in failures:
            print(f"  {failure}")
        return 1
    print(f"PASS palette contrast: all tested text pairs are at least 4.5:1; minimum {minimum_seen:.2f}:1")
    print("Note: this source-level calculation does not replace rendered-device or TalkBack testing.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
