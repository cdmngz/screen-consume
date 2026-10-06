"""Exercise authorization provenance, exact-commit CI, and protected release merges."""
import copy
import io
import json
import os
import tempfile
import unittest
import zipfile
from pathlib import Path
from unittest.mock import patch

import release_automation
import release_gate


class AuthorizationTests(unittest.TestCase):
    def setUp(self):
        self.pr = {'number': 7, 'head': {'sha': 'prepared', 'ref': 'codex/release/1.7.2-12',
                                                   'repo': {'full_name': 'owner/repo'}},
                   'user': {'login': 'github-actions[bot]', 'type': 'Bot'},
                   'merged_by': {'login': 'github-actions[bot]'}, 'merged_at': 'now',
                   'merge_commit_sha': 'merged', 'base': {'ref': 'main'}}
        self.run = {'conclusion': 'success', 'event': 'workflow_dispatch',
                    'path': '.github/workflows/release-prepare.yml', 'head_branch': 'main',
                    'head_repository': {'full_name': 'owner/repo'}, 'head_sha': 'base',
                    'actor': {'login': 'owner', 'type': 'User'},
                    'triggering_actor': {'login': 'maintainer', 'type': 'User'}, 'run_attempt': 1}
        self.receipt = {'pr': 7, 'head_sha': 'prepared', 'merge_sha': 'merged', 'base_sha': 'base',
                        'ci_run_id': 13, 'run_attempt': 1}

    def authorize(self):
        with patch('release_gate.gh', side_effect=[self.pr, self.run, {'parents': [{'sha': 'base'}]}]), \
             patch('release_gate.maintainer') as role, \
             patch('release_gate.no_change_requests'), \
             patch('release_gate.read_authorization', return_value=self.receipt), \
             patch('release_gate.successful_ci') as ci:
            release_gate.authorized(self.pr, 'owner/repo', 'merged')
        self.assertEqual(role.call_count, 2)
        ci.assert_called_once_with('owner/repo', 13, 'prepared', 'codex/release/1.7.2-12')

    def test_authorized_dispatch_accepts_exact_generated_merge(self):
        self.authorize()

    def test_failed_non_main_fork_or_different_workflow_cannot_authorize(self):
        for key, value in [('conclusion', 'failure'), ('event', 'push'), ('head_branch', 'feature'),
                           ('path', '.github/workflows/untrusted.yml'),
                           ('head_repository', {'full_name': 'fork/repo'})]:
            with self.subTest(key=key):
                run = copy.deepcopy(self.run)
                run[key] = value
                with patch('release_gate.gh', side_effect=[self.pr, run]), self.assertRaises(ValueError):
                    release_gate.authorized(self.pr, 'owner/repo', 'merged')

    def test_changed_head_merge_base_pr_or_attempt_rejected(self):
        for key in ('pr', 'head_sha', 'merge_sha', 'base_sha', 'run_attempt'):
            with self.subTest(key=key):
                receipt = dict(self.receipt, **{key: 'wrong'})
                with patch('release_gate.gh', side_effect=[self.pr, self.run]), \
                     patch('release_gate.maintainer'), \
                     patch('release_gate.no_change_requests'), \
                     patch('release_gate.read_authorization', return_value=receipt), \
                     self.assertRaisesRegex(ValueError, 'does not match'):
                    release_gate.authorized(self.pr, 'owner/repo', 'merged')

    def test_manual_or_modified_branch_rejected(self):
        for branch in ('codex/release/manual', 'feature/1.7.2-12', 'codex/release/1.7.2-12/injected'):
            self.pr['head']['ref'] = branch
            with patch('release_gate.gh', return_value=self.pr), self.assertRaises(ValueError):
                release_gate.authorized(self.pr, 'owner/repo', 'merged')

    def test_authorization_artifact_is_read_without_extraction(self):
        buffer = io.BytesIO()
        with zipfile.ZipFile(buffer, 'w') as archive:
            archive.writestr('release-authorization.json', json.dumps(self.receipt))
        metadata = {'artifacts': [{'id': 1, 'name': 'release-authorization',
                                  'expired': False, 'size_in_bytes': len(buffer.getvalue())}]}
        with patch('release_gate.gh', return_value=metadata), \
             patch('release_gate.subprocess.check_output', return_value=buffer.getvalue()):
            self.assertEqual(release_gate.read_authorization('owner/repo', 12), self.receipt)
        for filename, contents in [('../release-authorization.json', '{}'),
                                   ('release-authorization.json', 'x' * 4097)]:
            buffer = io.BytesIO()
            with zipfile.ZipFile(buffer, 'w') as archive:
                archive.writestr(filename, contents)
            with patch('release_gate.gh', return_value=metadata), \
                 patch('release_gate.subprocess.check_output', return_value=buffer.getvalue()), \
                 self.assertRaises(ValueError):
                release_gate.read_authorization('owner/repo', 12)

    def test_change_requests_veto_release_until_superseded(self):
        review = {'user': {'login': 'owner'}, 'state': 'CHANGES_REQUESTED'}
        with patch('release_gate.gh_list', return_value=[review]), self.assertRaises(ValueError):
            release_gate.no_change_requests('owner/repo', 7)
        dismissed = dict(review, state='DISMISSED')
        with patch('release_gate.gh_list', return_value=[review, dismissed]):
            release_gate.no_change_requests('owner/repo', 7)

    def test_expired_missing_or_ambiguous_authorization_rejected(self):
        for artifacts in ([], [{'name': 'release-authorization', 'expired': True}],
                          [{'name': 'release-authorization'}] * 2):
            with patch('release_gate.gh', return_value={'artifacts': artifacts}), \
                 self.assertRaises(ValueError):
                release_gate.read_authorization('owner/repo', 12)


class AutomationTests(unittest.TestCase):
    def setUp(self):
        self.pr = {'state': 'open', 'draft': False, 'merged': False,
                   'user': {'login': 'github-actions[bot]', 'type': 'Bot'},
                   'head': {'sha': 'head', 'ref': 'codex/release/1.7.2-12',
                            'repo': {'full_name': 'owner/repo'}}, 'base': {'ref': 'main'}}
        self.comparison = {'status': 'ahead', 'ahead_by': 1, 'total_commits': 1,
                           'commits': [{'parents': [{'sha': 'base'}]}],
                           'files': [{'filename': 'app/build.gradle.kts', 'status': 'modified'},
                                     {'filename': 'release/current.json', 'status': 'modified'}]}
        self.run = {'conclusion': 'success', 'event': 'workflow_dispatch',
                    'path': '.github/workflows/android-ci.yml', 'head_sha': 'head',
                    'head_branch': 'codex/release/1.7.2-12',
                    'head_repository': {'full_name': 'owner/repo'}}

    def check_pr(self, main='base'):
        with patch('release_automation.gh', side_effect=[self.pr, {'object': {'sha': main}},
                                                       self.comparison]), \
             patch('release_automation.no_change_requests'):
            return release_automation.check_pr('owner/repo', 7, 'head', 'base',
                                                'codex/release/1.7.2-12')

    def test_untouched_generated_pr_can_merge(self):
        self.assertEqual(self.check_pr(), self.pr)

    def test_changed_main_head_fork_or_extra_files_stop_merge(self):
        with self.assertRaises(ValueError):
            self.check_pr(main='new-main')
        for field, value in [('sha', 'changed'), ('repo', {'full_name': 'fork/repo'})]:
            original = copy.deepcopy(self.pr)
            self.pr['head'][field] = value
            with self.assertRaises(ValueError):
                self.check_pr()
            self.pr = original
        self.comparison['files'].append({'filename': 'scripts/play_release.py', 'status': 'modified'})
        with self.assertRaises(ValueError):
            self.check_pr()

    def test_ci_requires_every_job_and_exact_source(self):
        jobs = [{'name': name, 'conclusion': 'success'} for name in release_gate.CI_JOBS]
        for conclusion in ('success', 'skipped', 'failure'):
            jobs[0]['conclusion'] = conclusion
            with patch('release_gate.gh', return_value=self.run), \
                 patch('release_gate.subprocess.check_output', return_value=json.dumps([{'jobs': jobs}]).encode()):
                if conclusion == 'success':
                    release_gate.successful_ci('owner/repo', 13, 'head', 'codex/release/1.7.2-12')
                else:
                    with self.assertRaises(ValueError):
                        release_gate.successful_ci('owner/repo', 13, 'head', 'codex/release/1.7.2-12')
        self.run['head_sha'] = 'changed'
        with patch('release_gate.gh', return_value=self.run), self.assertRaises(ValueError):
            release_gate.successful_ci('owner/repo', 13, 'head', 'codex/release/1.7.2-12')

    def test_dispatch_requests_exact_commit_and_receives_run_id(self):
        with patch('release_automation.mutate', return_value={'workflow_run_id': 13}) as api:
            self.assertEqual(release_automation.dispatch_ci('owner/repo', 'main', 'merged'), 13)
        self.assertEqual(api.call_args.args[2]['inputs'], {'expected_sha': 'merged'})

    def test_operator_checks_original_and_rerun_actor_on_main(self):
        environment = {'GITHUB_REPOSITORY': 'owner/repo', 'GITHUB_REF': 'refs/heads/main',
                       'GITHUB_ACTOR': 'owner', 'GITHUB_TRIGGERING_ACTOR': 'maintainer'}
        with patch.dict(os.environ, environment), patch('release_automation.maintainer') as role:
            release_automation.operator()
        self.assertEqual(role.call_count, 2)
        with patch.dict(os.environ, dict(environment, GITHUB_REF='refs/heads/feature')), \
             self.assertRaises(ValueError):
            release_automation.operator()

    def test_successful_merge_records_exact_sha_and_no_bypass(self):
        environment = {'GITHUB_REPOSITORY': 'owner/repo', 'GITHUB_SHA': 'base',
                       'GITHUB_RUN_ID': '12', 'GITHUB_RUN_ATTEMPT': '1', 'PREPARED_SHA': 'head',
                       'PREPARED_BRANCH': 'codex/release/1.7.2-12', 'PREPARED_PR': '7',
                       'PREPARED_CI_RUN_ID': '13'}
        with tempfile.TemporaryDirectory() as directory:
            receipt = Path(directory) / 'receipt.json'
            output = Path(directory) / 'output'
            for merged in (False, True):
                self.pr['merged'] = merged
                self.pr['merge_commit_sha'] = 'merged'
                self.pr['merged_by'] = {'login': 'github-actions[bot]'}
                with patch.dict(os.environ, dict(environment, GITHUB_OUTPUT=str(output))), \
                     patch('sys.argv', ['release_automation.py', 'merge']), \
                     patch('release_automation.operator'), \
                     patch('release_automation.check_pr', return_value=self.pr), \
                     patch('release_automation.successful_ci'), \
                     patch('release_automation.gh', return_value={'parents': [{'sha': 'base'}]}), \
                     patch('release_automation.mutate', return_value={'merged': True, 'sha': 'merged'}) as api, \
                     patch('release_automation.Path', return_value=receipt):
                    release_automation.main()
                self.assertEqual(json.loads(receipt.read_text())['merge_sha'], 'merged')
                if merged:
                    api.assert_not_called()
                else:
                    api.assert_called_once_with('repos/owner/repo/pulls/7/merge', 'PUT',
                                                {'sha': 'head', 'merge_method': 'squash'})
            self.assertEqual(output.read_text(), 'sha=merged\nsha=merged\n')



if __name__ == '__main__':
    unittest.main()
