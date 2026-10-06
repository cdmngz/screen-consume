"""Release metadata preparation and validation; no credentials required."""
import argparse
import json
import os
from pathlib import Path
import re

DEFAULTS = {
    "en-US": "Bug fixes and improvements.",
    "es-ES": "Correcciones de errores y mejoras.",
    "de-DE": "Fehlerbehebungen und Verbesserungen.",
    "fr-FR": "Corrections de bugs et améliorations.",
    "it-IT": "Correzioni di errori e miglioramenti.",
    "pt-PT": "Correções de erros e melhorias.",
}
GRADLE = Path("app/build.gradle.kts")
METADATA = Path("release/current.json")


def version(source):
    names = re.findall(r'versionName = "(\d+\.\d+\.\d+)"', source)
    codes = re.findall(r'versionCode = (\d+)', source)
    if len(names) != 1 or len(codes) != 1:
        raise ValueError("Expected exactly one semantic version and version code")
    return names[0], int(codes[0])


def bumped(name, kind):
    major, minor, patch = map(int, name.split("."))
    return {"patch": f"{major}.{minor}.{patch + 1}",
            "minor": f"{major}.{minor + 1}.0",
            "major": f"{major + 1}.0.0"}[kind]


def validate(data, source):
    name, code = version(source)
    if data.get("versionName") != name or data.get("versionCode") != code:
        raise ValueError("Release metadata does not match Android version")
    if not 1 <= code <= 2100000000:
        raise ValueError("Version code outside Play limits")
    notes = data.get("notes", {})
    if set(notes) != set(DEFAULTS):
        raise ValueError("Release notes must cover all six languages")
    for text in notes.values():
        if not isinstance(text, str) or not text.strip() or len(text) > 500:
            raise ValueError("Each release note must contain 1–500 characters")
    if data.get("bump") not in {"patch", "minor", "major"}:
        raise ValueError("Invalid version bump")
    return data


def prepare(kind):
    source = GRADLE.read_text()
    name, code = version(source)
    name = bumped(name, kind)
    source = re.sub(r'versionName = "[^"]+"', f'versionName = "{name}"', source)
    source = re.sub(r'versionCode = \d+', f'versionCode = {code + 1}', source)
    notes = {lang: os.environ.get("NOTES_" + lang[:2].upper(), "").strip() or default
             for lang, default in DEFAULTS.items()}
    data = validate({"versionName": name, "versionCode": code + 1,
                     "bump": kind, "notes": notes}, source)
    GRADLE.write_text(source)
    METADATA.parent.mkdir(exist_ok=True)
    METADATA.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n")
    print(name)


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("command", choices=["prepare", "validate"])
    parser.add_argument("--bump", choices=["patch", "minor", "major"])
    args = parser.parse_args()
    if args.command == "prepare":
        if not args.bump:
            parser.error("--bump is required")
        prepare(args.bump)
    else:
        validate(json.loads(METADATA.read_text()), GRADLE.read_text())
