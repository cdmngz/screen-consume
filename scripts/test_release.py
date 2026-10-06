"""Exercise version transitions, note validation, and Play promotion safeguards."""
import copy
import json
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import release
import play_release
import release_gate


class ReleaseTests(unittest.TestCase):
    def setUp(self):
        self.source = 'versionCode = 10\nversionName = "1.7.1"\n'
        self.data = {"versionCode": 10, "versionName": "1.7.1", "bump": "patch",
                     "notes": dict(release.DEFAULTS)}

    def test_bumps_reset_lower_components(self):
        self.assertEqual(release.bumped("1.7.9", "patch"), "1.7.10")
        self.assertEqual(release.bumped("1.7.9", "minor"), "1.8.0")
        self.assertEqual(release.bumped("1.7.9", "major"), "2.0.0")

    def test_all_languages_required(self):
        del self.data["notes"]["es-ES"]
        with self.assertRaises(ValueError):
            release.validate(self.data, self.source)

    def test_notes_and_version_fail_closed(self):
        for value in ("", " " * 10, "x" * 501, 9):
            data = copy.deepcopy(self.data)
            data["notes"]["en-US"] = value
            with self.assertRaises(ValueError):
                release.validate(data, self.source)
        self.data["versionCode"] = 11
        with self.assertRaises(ValueError):
            release.validate(self.data, self.source)

    def test_exact_completed_version_required(self):
        for state in ("draft", "inProgress", "halted"):
            track = {"releases": [{"versionCodes": ["10"], "status": state}]}
            self.assertIsNone(play_release.completed_release(track, 10))
        track = {"releases": [{"versionCodes": ["10", "11"], "status": "completed"}]}
        self.assertIsNone(play_release.completed_release(track, 10))
        track = {"releases": [{"versionCodes": ["10"], "status": "completed"}]}
        self.assertIsNotNone(play_release.completed_release(track, 10))

    def test_production_never_uploads_or_signs(self):
        calls = []
        track = {"track": "alpha", "releases": [
            {"versionCodes": ["10"], "status": "completed"}]}

        def api(url, method="GET", data=None, token=None, content_type=None):
            calls.append((url, method, data))
            if url.endswith('/edits'):
                return {"id": "edit"}
            if url.endswith('/tracks'):
                return {"tracks": [track]}
            return {}

        with patch.dict('os.environ', {"PLAY_CLOSED_TRACK": "alpha"}), \
             patch('play_release.Path.read_text', side_effect=[json.dumps(self.data), self.source]), \
             patch('play_release.token', return_value="token"), \
             patch('play_release.request', side_effect=api), \
             patch('play_release.sign') as sign:
            play_release.publish('production')
        sign.assert_not_called()
        self.assertFalse(any('/bundles' in url for url, _, _ in calls))
        updates = [data for url, method, data in calls if method == 'PUT']
        self.assertEqual(updates[0]['track'], 'production')
        self.assertEqual(updates[0]['releases'][0]['versionCodes'], ['10'])

    def test_production_rejects_unfinished_closed_release(self):
        def api(url, *args, **kwargs):
            if url.endswith('/edits'):
                return {"id": "edit"}
            return {"tracks": [{"track": "alpha", "releases": [
                {"versionCodes": ["10"], "status": "draft"}]}]}
        with patch.dict('os.environ', {"PLAY_CLOSED_TRACK": "alpha"}), \
             patch('play_release.Path.read_text', side_effect=[json.dumps(self.data), self.source]), \
             patch('play_release.token', return_value="token"), \
             patch('play_release.request', side_effect=api), \
             self.assertRaisesRegex(ValueError, 'not completed'):
            play_release.publish('production')


class GateTests(unittest.TestCase):
    def setUp(self):
        self.pr = {"number": 7, "head": {"sha": "final"}}
        self.review = {"state": "APPROVED", "commit_id": "final",
                       "user": {"login": "owner", "type": "User"}}

    def test_approval_of_final_commit_required(self):
        with patch('release_gate.gh_list', return_value=[self.review]), \
             patch('release_gate.gh', return_value={'permission': 'write'}):
            release_gate.approved(self.pr, 'owner/repo')
        self.review['commit_id'] = 'old'
        with patch('release_gate.gh_list', return_value=[self.review]), self.assertRaises(ValueError):
            release_gate.approved(self.pr, 'owner/repo')

    def test_outside_reviewer_cannot_authorize_release(self):
        with patch('release_gate.gh_list', return_value=[self.review]), \
             patch('release_gate.gh', return_value={'permission': 'read'}), \
             self.assertRaisesRegex(ValueError, 'maintainer approval'):
            release_gate.approved(self.pr, 'owner/repo')

    def test_bot_and_dismissed_approvals_rejected(self):
        for state, user_type in [('APPROVED', 'Bot'), ('DISMISSED', 'User')]:
            self.review['state'] = state
            self.review['user']['type'] = user_type
            with patch('release_gate.gh_list', return_value=[self.review]), self.assertRaises(ValueError):
                release_gate.approved(self.pr, 'owner/repo')

    def test_later_change_request_supersedes_approval(self):
        changed = copy.deepcopy(self.review)
        changed['state'] = 'CHANGES_REQUESTED'
        with patch('release_gate.gh_list', return_value=[self.review, changed]), self.assertRaises(ValueError):
            release_gate.approved(self.pr, 'owner/repo')

    def test_production_requires_successful_publication_job(self):
        run = {'conclusion': 'success', 'event': 'workflow_run', 'head_branch': 'main',
               'path': '.github/workflows/release-closed.yml'}
        with tempfile.TemporaryDirectory() as directory:
            event = Path(directory) / 'event.json'
            event.write_text('{}')
            with patch.dict(os.environ, {'GITHUB_REPOSITORY': 'owner/repo',
                                         'GITHUB_EVENT_PATH': str(event), 'CLOSED_RUN_ID': '12'}), \
                 patch('sys.argv', ['release_gate.py', 'production']), \
                 patch('release_gate.gh', side_effect=[run, {'jobs': [
                     {'name': 'Publish closed testing', 'conclusion': 'skipped'}]}]), \
                 self.assertRaisesRegex(ValueError, 'did not succeed'):
                release_gate.main()

    def test_prepare_defaults_and_custom_notes(self):
        with tempfile.TemporaryDirectory() as directory:
            gradle = Path(directory) / 'build.gradle.kts'
            metadata = Path(directory) / 'current.json'
            gradle.write_text('versionCode = 9\nversionName = "1.7.0"\n')
            with patch('release.GRADLE', gradle), patch('release.METADATA', metadata), \
                 patch.dict(os.environ, {'NOTES_ES': '  Corrección de gráficos.  '}, clear=True):
                release.prepare('minor')
            data = json.loads(metadata.read_text())
            self.assertEqual(data['versionName'], '1.8.0')
            self.assertEqual(data['versionCode'], 10)
            self.assertEqual(data['notes']['es-ES'], 'Corrección de gráficos.')
            self.assertEqual(data['notes']['en-US'], release.DEFAULTS['en-US'])
            release.validate(data, gradle.read_text())


if __name__ == '__main__':
    unittest.main()
