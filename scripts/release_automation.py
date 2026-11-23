"""Coordinate a maintainer-authorized release without bypassing branch protection."""
import json
import os
import subprocess
import sys
import time
from pathlib import Path

from release_gate import check, gh, maintainer, no_change_requests, successful_ci


def mutate(path, method, data):
    return json.loads(subprocess.check_output(
        ['gh', 'api', '--method', method, path, '--input', '-'],
        input=json.dumps(data).encode()))


def operator():
    repo = os.environ['GITHUB_REPOSITORY']
    check(os.environ['GITHUB_REF'] == 'refs/heads/main', 'Release actions must run on main')
    for login in {os.environ['GITHUB_ACTOR'], os.environ['GITHUB_TRIGGERING_ACTOR']}:
        maintainer(repo, login)


def dispatch_ci(repo, branch, sha):
    result = mutate(f'repos/{repo}/actions/workflows/android-ci.yml/dispatches', 'POST',
                    {'ref': branch, 'inputs': {'expected_sha': sha}, 'return_run_details': True})
    run_id = result['workflow_run_id']
    check(isinstance(run_id, int) and run_id > 0, 'Expected a dispatched CI run ID')
    print(f'Android CI: https://github.com/{repo}/actions/runs/{run_id}', flush=True)
    return run_id


def wait_for_ci_visibility(repo, run_id):
    # GitHub can return a run ID before the endpoint used by gh run watch can read it.
    command = ['gh', 'api', f'repos/{repo}/actions/runs/{run_id}?exclude_pull_requests=true', '--silent']
    for attempt in range(7):
        result = subprocess.run(command, capture_output=True, text=True)
        if result.returncode == 0:
            return
        if 'HTTP 404' not in result.stderr or attempt == 6:
            raise RuntimeError(f'Cannot read dispatched CI run {run_id}: {result.stderr.strip()}')
        time.sleep(5)


def check_pr(repo, number, head, base, branch):
    pr = gh(f'repos/{repo}/pulls/{number}')
    check(not pr['draft'] and (pr['state'] == 'open' or pr.get('merged')),
          'Expected an open or already merged release PR')
    check(pr['user']['login'] == 'github-actions[bot]' and pr['user']['type'] == 'Bot',
          'Expected the workflow-generated PR')
    check(pr['head']['sha'] == head and pr['head']['ref'] == branch
          and pr['head']['repo']['full_name'] == repo and pr['base']['ref'] == 'main',
          'Prepared release PR was changed')
    if not pr.get('merged'):
        check(gh(f'repos/{repo}/git/ref/heads/main')['object']['sha'] == base,
              'Main changed during release validation; close this PR and prepare a fresh release')
    no_change_requests(repo, number)
    comparison = gh(f'repos/{repo}/compare/{base}...{head}')
    check(comparison['status'] == 'ahead' and comparison['ahead_by'] == 1
          and comparison['total_commits'] == 1
          and len(comparison['commits'][0]['parents']) == 1
          and comparison['commits'][0]['parents'][0]['sha'] == base,
          'Expected exactly one generated release commit on the original main')
    files = comparison['files']
    check({f['filename'] for f in files} == {'app/build.gradle.kts', 'release/current.json'}
          and len(files) == 2 and all(f['status'] == 'modified' for f in files),
          'Release PR must only change the version and release notes')
    return pr


def wait_for_pr_checks(repo, number, head, base):
    # The dispatched head CI can pass before GitHub accepts the PR's required checks.
    url = f'https://github.com/{repo}/pull/{number}'
    command = ['gh', 'pr', 'view', str(number), '--repo', repo,
               '--json', 'state,headRefOid,mergeStateStatus']
    print(f'Waiting for branch protection on {url}. '
          'If shown on the PR, select “Approve workflows to run”.', flush=True)
    deadline = time.monotonic() + 30 * 60
    while True:
        status = json.loads(subprocess.check_output(command))
        check(status['state'] == 'OPEN' and status['headRefOid'] == head,
              'Prepared release PR was changed or closed while waiting')
        check(gh(f'repos/{repo}/git/ref/heads/main')['object']['sha'] == base,
              'Main changed during release validation; close this PR and prepare a fresh release')
        if status['mergeStateStatus'] == 'CLEAN':
            return
        remaining = deadline - time.monotonic()
        check(remaining > 0, f"GitHub still reports {status['mergeStateStatus']} for {url}. "
              'Check the PR and rerun failed jobs once merge requirements are met.')
        time.sleep(min(15, remaining))


def main():
    operator()
    mode = sys.argv[1]
    if mode == 'operator':
        return
    repo = os.environ['GITHUB_REPOSITORY']
    head = os.environ['PREPARED_SHA']
    branch = os.environ['PREPARED_BRANCH']
    if mode == 'ci':
        run_id = dispatch_ci(repo, branch, head)
        # Waiting happens on the Actions runner, with no release secrets or content writes.
        wait_for_ci_visibility(repo, run_id)
        subprocess.run(['gh', 'run', 'watch', str(run_id), '--repo', repo,
                        '--exit-status', '--interval', '30'], check=True)
        successful_ci(repo, run_id, head, branch)
        with open(os.environ['GITHUB_OUTPUT'], 'a') as output:
            output.write(f'ci_run_id={run_id}\n')
    elif mode == 'merge':
        number = int(os.environ['PREPARED_PR'])
        base = os.environ['GITHUB_SHA']
        check(branch.endswith(f"-{os.environ['GITHUB_RUN_ID']}"), 'Wrong preparation run')
        pr = check_pr(repo, number, head, base, branch)
        ci_run_id = int(os.environ['PREPARED_CI_RUN_ID'])
        successful_ci(repo, ci_run_id, head, branch)
        # No --admin or bypass: GitHub enforces all existing main protections.
        if pr.get('merged'):
            # Recover a failed artifact upload or dispatch without merging a second PR.
            check(pr['merged_by']['login'] == 'github-actions[bot]', 'Unexpected release merger')
            sha = pr['merge_commit_sha']
        else:
            wait_for_pr_checks(repo, number, head, base)
            pr = check_pr(repo, number, head, base, branch)
            check(not pr.get('merged'), 'Release PR was merged while waiting')
            result = mutate(f'repos/{repo}/pulls/{number}/merge', 'PUT',
                            {'sha': head, 'merge_method': 'squash'})
            check(result.get('merged') is True, 'GitHub did not merge the release PR')
            sha = result['sha']
        commit = gh(f'repos/{repo}/commits/{sha}')
        check(len(commit['parents']) == 1 and commit['parents'][0]['sha'] == base,
              'Merged release base does not match the authorized main')
        Path('release-authorization.json').write_text(json.dumps({
            'pr': number, 'head_sha': head, 'merge_sha': sha, 'base_sha': base,
            'ci_run_id': ci_run_id, 'run_attempt': int(os.environ['GITHUB_RUN_ATTEMPT'])}))
        with open(os.environ['GITHUB_OUTPUT'], 'a') as output:
            output.write(f'sha={sha}\n')
    elif mode == 'main-ci':
        dispatch_ci(repo, 'main', os.environ['MERGED_SHA'])
    else:
        raise ValueError('Unknown automation mode')


if __name__ == '__main__':
    main()
