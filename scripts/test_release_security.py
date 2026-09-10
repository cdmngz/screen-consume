"""Regression tests prove that privacy guardrails reject weakened artifacts."""

from pathlib import Path
import unittest
import xml.etree.ElementTree as ET

from check_release_security import ANDROID, check_backup_rules, check_manifest


class ReleaseSecurityTest(unittest.TestCase):
    def setUp(self):
        self.manifest = ET.fromstring('''
            <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                package="org.screenconsume.app">
                <uses-permission android:name="android.permission.PACKAGE_USAGE_STATS" />
                <uses-permission android:name="android.permission.WAKE_LOCK" />
                <uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
                <uses-permission android:name="org.screenconsume.app.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION" />
                <application android:allowBackup="false" android:fullBackupContent="@xml/backup_rules"
                    android:dataExtractionRules="@xml/data_extraction_rules">
                    <activity android:name="org.screenconsume.app.MainActivity" android:exported="true">
                        <intent-filter>
                            <action android:name="android.intent.action.MAIN" />
                            <category android:name="android.intent.category.LAUNCHER" />
                        </intent-filter>
                    </activity>
                    <service android:name="androidx.work.impl.background.systemjob.SystemJobService"
                        android:exported="true" android:permission="android.permission.BIND_JOB_SERVICE" />
                </application>
            </manifest>
        ''')
        self.app = self.manifest.find("application")
        rules = Path(__file__).resolve().parents[1] / "app/src/main/res/xml"
        self.legacy = ET.parse(rules / "backup_rules.xml").getroot()
        self.extraction = ET.parse(rules / "data_extraction_rules.xml").getroot()

    def test_reviewed_configuration_passes(self):
        check_manifest(self.manifest)
        check_backup_rules(self.legacy, self.extraction)

    def test_added_permission_is_rejected_even_with_sdk_variant(self):
        ET.SubElement(self.manifest, "uses-permission-sdk-23", {ANDROID + "name": "android.permission.INTERNET"})
        with self.assertRaisesRegex(ValueError, "permissions"):
            check_manifest(self.manifest)

    def test_backup_and_debug_flags_are_rejected(self):
        for flag in ("allowBackup", "debuggable", "testOnly", "usesCleartextTraffic"):
            with self.subTest(flag=flag):
                self.app.set(ANDROID + flag, "true")
                with self.assertRaises(ValueError):
                    check_manifest(self.manifest)
                self.app.set(ANDROID + flag, "false")

    def test_unprotected_export_is_rejected(self):
        self.app.find("service").attrib.pop(ANDROID + "permission")
        with self.assertRaisesRegex(ValueError, "protection"):
            check_manifest(self.manifest)

    def test_new_export_is_rejected(self):
        ET.SubElement(self.app, "receiver", {ANDROID + "name": "example.Receiver", ANDROID + "exported": "true"})
        with self.assertRaisesRegex(ValueError, "Unreviewed"):
            check_manifest(self.manifest)

    def test_implicit_exposure_is_rejected(self):
        self.app.find("service").attrib.pop(ANDROID + "exported")
        with self.assertRaisesRegex(ValueError, "explicitly"):
            check_manifest(self.manifest)

    def test_debug_component_is_rejected_even_if_private(self):
        ET.SubElement(self.app, "activity", {ANDROID + "name": "androidx.compose.ui.tooling.PreviewActivity",
                                           ANDROID + "exported": "false"})
        with self.assertRaisesRegex(ValueError, "tooling"):
            check_manifest(self.manifest)

    def test_deep_link_is_rejected(self):
        ET.SubElement(self.app.find("activity/intent-filter"), "data", {ANDROID + "scheme": "screenconsume"})
        with self.assertRaisesRegex(ValueError, "additional intents"):
            check_manifest(self.manifest)

    def test_missing_backup_reference_is_rejected(self):
        self.app.attrib.pop(ANDROID + "dataExtractionRules")
        with self.assertRaisesRegex(ValueError, "dataExtractionRules"):
            check_manifest(self.manifest)

    def test_missing_transfer_exclusion_is_rejected(self):
        transfer = self.extraction.find("device-transfer")
        transfer.remove(transfer.find("exclude"))
        with self.assertRaisesRegex(ValueError, "storage domains"):
            check_backup_rules(self.legacy, self.extraction)

    def test_backup_include_is_rejected(self):
        ET.SubElement(self.legacy, "include", {"domain": "database", "path": "."})
        with self.assertRaisesRegex(ValueError, "includes"):
            check_backup_rules(self.legacy, self.extraction)


if __name__ == "__main__":
    unittest.main()
