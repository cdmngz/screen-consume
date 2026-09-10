"""Fail CI when the unsigned release APK weakens reviewed Android boundaries."""

import argparse
import os
from pathlib import Path
import shutil
import subprocess
import sys
import xml.etree.ElementTree as ET


ANDROID = "{http://schemas.android.com/apk/res/android}"
PACKAGE = "org.screenconsume.app"
PERMISSIONS = {
    "android.permission.PACKAGE_USAGE_STATS",
    "android.permission.WAKE_LOCK",
    "android.permission.RECEIVE_BOOT_COMPLETED",
    f"{PACKAGE}.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION",
}
EXPORTED = {
    ("activity", f"{PACKAGE}.MainActivity"): None,
    ("service", "androidx.work.impl.background.systemjob.SystemJobService"):
        "android.permission.BIND_JOB_SERVICE",
    ("receiver", "androidx.work.impl.diagnostics.DiagnosticsReceiver"):
        "android.permission.DUMP",
    ("receiver", "androidx.profileinstaller.ProfileInstallReceiver"):
        "android.permission.DUMP",
}
DOMAINS = {"root", "file", "database", "sharedpref", "external"}
DEVICE_DOMAINS = {"device_root", "device_file", "device_database", "device_sharedpref"}


def require(condition, message):
    if not condition:
        raise ValueError(message)


def check_manifest(root):
    require(root.get("package") == PACKAGE, "Unexpected application package")
    permissions = {
        item.get(ANDROID + "name") for item in root
        if item.tag.startswith("uses-permission")
    }
    require(permissions == PERMISSIONS, "Release permissions differ from the reviewed allowlist")
    app = root.find("application")
    require(app is not None, "Missing application")
    require(app.get(ANDROID + "allowBackup") == "false", "Automatic backup must be disabled")
    for flag in ("debuggable", "testOnly", "usesCleartextTraffic"):
        require(app.get(ANDROID + flag, "false") == "false", f"Release enables {flag}")
    for key in ("fullBackupContent", "dataExtractionRules"):
        require(app.get(ANDROID + key, "").startswith("@"), f"Missing {key} resource")
    require(root.find("instrumentation") is None, "Instrumentation must not ship")
    for item in app:
        name = item.get(ANDROID + "name", "")
        require(not name.startswith("androidx.compose.ui.tooling."), "Compose tooling component in release")
        if item.tag not in {"activity", "activity-alias", "service", "receiver", "provider"}:
            continue
        exported = item.get(ANDROID + "exported")
        require(exported in {"true", "false"}, "Component must declare its exposure explicitly")
        if exported == "true":
            key = (item.tag, name)
            require(key in EXPORTED, "Unreviewed exported component")
            require(item.get(ANDROID + "permission") == EXPORTED[key], "Exported component protection changed")
            if key == ("activity", f"{PACKAGE}.MainActivity"):
                filters = item.findall("intent-filter")
                require(len(filters) == 1, "Launcher intent filters changed")
                entries = {(node.tag, node.get(ANDROID + "name")) for node in filters[0]}
                require(entries == {("action", "android.intent.action.MAIN"),
                                    ("category", "android.intent.category.LAUNCHER")},
                        "Launcher exposes additional intents or data")


def check_backup_rules(legacy, extraction):
    def exclusions(node, domains):
        require(node is not None, "Missing backup exclusion section")
        require(not node.findall("include"), "Backup includes must not be added")
        excluded = {item.get("domain") for item in node.findall("exclude") if item.get("path") == "."}
        require(domains <= excluded, "Backup exclusions no longer cover all storage domains")

    require(legacy.tag == "full-backup-content", "Unexpected legacy backup rules")
    require(extraction.tag == "data-extraction-rules", "Unexpected data extraction rules")
    exclusions(legacy, DOMAINS)
    for section in ("cloud-backup", "device-transfer"):
        exclusions(extraction.find(section), DOMAINS | DEVICE_DOMAINS)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apk", default="app/build/outputs/apk/release/app-release-unsigned.apk")
    parser.add_argument("--apkanalyzer", default=shutil.which("apkanalyzer"))
    args = parser.parse_args()
    analyzer = args.apkanalyzer
    if not analyzer:
        sdk = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
        require(sdk, "Set ANDROID_HOME or pass --apkanalyzer")
        analyzer = str(Path(sdk) / "cmdline-tools/latest/bin/apkanalyzer")

    def analyze(*command):
        return subprocess.check_output([analyzer, *command, args.apk], text=True).strip()

    check_manifest(ET.fromstring(analyze("manifest", "print")))

    def resource(name):
        # Resource shrinking can rename archive paths; resolve them from the APK table.
        path = analyze("resources", "value", "--config", "default", "--type", "xml", "--name", name)
        return ET.fromstring(analyze("resources", "xml", "--file", path))

    check_backup_rules(resource("backup_rules"), resource("data_extraction_rules"))
    print("Release APK privacy/security checks passed: permissions, exposure, backup, and debug flags.")


if __name__ == "__main__":
    try:
        main()
    except (ValueError, OSError, subprocess.CalledProcessError, ET.ParseError) as error:
        print(f"Release security check failed: {error}", file=sys.stderr)
        sys.exit(1)
