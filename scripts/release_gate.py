"""Publish only exact releases authorized by an admin or maintainer dispatch."""
import io
import json
import os
import re
import subprocess
import sys
import zipfile
from pathlib import Path
from urllib.parse import quote
from release import bumped, validate, version


def gh(path):
    return json.loads(subprocess.check_output(["gh", "api", path]))


def gh_list(path):
    pages = json.loads(subprocess.check_output(["gh", "api", "--paginate", "--slurp", path]))
    return [item for page in pages for item in page]


def check(condition, message):
    if not condition:
        raise ValueError(message)


CI_JOBS = {'Unit tests', 'Android lint', 'Unused Kotlin code',
           'Build APKs and bundle', 'Privacy and release security'}


def maintainer(repo, login):
    permission = gh(f"repos/{repo}/collaborators/{quote(login, safe='')}/permission")
    check(permission.get('role_name') in {'admin', 'maintain'}
          and permission.get('user', {}).get('type') == 'User',
          'Release actions require a human repository admin or maintainer')


def rerun_operator():
    if int(os.environ.get('GITHUB_RUN_ATTEMPT', '1')) > 1:
        maintainer(os.environ['GITHUB_REPOSITORY'], os.environ['GITHUB_TRIGGERING_ACTOR'])


def no_change_requests(repo, number):
    reviews = gh_list(f'repos/{repo}/pulls/{number}/reviews?per_page=100')
    latest = {}
    for review in reviews:
        if review['state'] in {'APPROVED', 'CHANGES_REQUESTED', 'DISMISSED'}:
            latest[review['user']['login']] = review['state']
    check('CHANGES_REQUESTED' not in latest.values(), 'Release has outstanding change requests')


def successful_ci(repo, run_id, sha, branch):
    run = gh(f'repos/{repo}/actions/runs/{run_id}')
    check(run['conclusion'] == 'success' and run['event'] == 'workflow_dispatch'
          and run['path'] == '.github/workflows/android-ci.yml'
          and run['head_branch'] == branch and run['head_sha'] == sha
          and run['head_repository']['full_name'] == repo,
          'Expected successful dispatched CI on the exact release commit')
    pages = json.loads(subprocess.check_output([
        'gh', 'api', '--paginate', '--slurp',
        f'repos/{repo}/actions/runs/{run_id}/jobs?per_page=100']))
    jobs = [job for page in pages for job in page['jobs']]
    check(all(any(j['name'] == name and j['conclusion'] == 'success' for j in jobs)
              for name in CI_JOBS), 'Every Android CI job must succeed')


def read_authorization(repo, run_id):
    artifacts = gh(f'repos/{repo}/actions/runs/{run_id}/artifacts?name=release-authorization&per_page=100')
    matches = [a for a in artifacts['artifacts'] if a['name'] == 'release-authorization']
    check(len(matches) == 1 and not matches[0]['expired']
          and matches[0]['size_in_bytes'] <= 65536, 'Missing or invalid release authorization artifact')
    data = subprocess.check_output(['gh', 'api',
                                   f"repos/{repo}/actions/artifacts/{matches[0]['id']}/zip"])
    check(len(data) <= 65536, 'Authorization archive is too large')
    # Do not extract archive paths or execute downloaded content.
    with zipfile.ZipFile(io.BytesIO(data)) as archive:
        check(archive.namelist() == ['release-authorization.json']
              and archive.getinfo('release-authorization.json').file_size <= 4096,
              'Invalid authorization archive contents')
        return json.loads(archive.read('release-authorization.json'))


def authorized(pr, repo, sha):
    # Associated-PR listings omit merged_by; fetch the complete immutable merge evidence.
    pr = gh(f"repos/{repo}/pulls/{pr['number']}")
    check(pr.get('merged_at') and pr['merge_commit_sha'] == sha and pr['base']['ref'] == 'main'
          and pr['head']['repo'] and pr['head']['repo']['full_name'] == repo,
          'Expected the exact merged release PR in this repository')
    match = re.fullmatch(r'codex/release/[0-9]+\.[0-9]+\.[0-9]+-([0-9]+)', pr['head']['ref'])
    check(match is not None, 'Expected a workflow-generated release branch')
    run_id = match.group(1)
    run = gh(f'repos/{repo}/actions/runs/{run_id}')
    check(run['conclusion'] == 'success' and run['event'] == 'workflow_dispatch'
          and run['path'] == '.github/workflows/release-prepare.yml'
          and run['head_branch'] == 'main' and run['head_repository']['full_name'] == repo,
          'Release needs a successful authorized Prepare release PR run')
    for user in (run['actor'], run['triggering_actor']):
        check(user['type'] == 'User', 'Release must be initiated by a human')
        maintainer(repo, user['login'])
    no_change_requests(repo, pr['number'])
    receipt = read_authorization(repo, run_id)
    check(receipt['pr'] == pr['number'] and receipt['head_sha'] == pr['head']['sha']
          and receipt['merge_sha'] == sha and receipt['base_sha'] == run['head_sha']
          and receipt['run_attempt'] == run['run_attempt'],
          'Merged release does not match its authorization')
    check(pr['user']['login'] == 'github-actions[bot]' and pr['user']['type'] == 'Bot'
          and pr['merged_by']['login'] == 'github-actions[bot]',
          'Expected a release created and merged by the authorized workflow')
    successful_ci(repo, receipt['ci_run_id'], receipt['head_sha'], pr['head']['ref'])
    commit = gh(f'repos/{repo}/commits/{sha}')
    check(len(commit['parents']) == 1 and commit['parents'][0]['sha'] == receipt['base_sha'],
          'Release base changed after authorization')


def main():
    check(sys.argv[1] in {'closed', 'production', 'rerun'}, 'Unknown release gate mode')
    repo = os.environ["GITHUB_REPOSITORY"]
    rerun_operator()
    if sys.argv[1] == 'rerun':
        return
    if sys.argv[1] == 'production':
        check(os.environ['GITHUB_REF'] == 'refs/heads/main', 'Production must run on main')
        for login in {os.environ['GITHUB_ACTOR'], os.environ['GITHUB_TRIGGERING_ACTOR']}:
            maintainer(repo, login)
    event = json.loads(Path(os.environ["GITHUB_EVENT_PATH"]).read_text())
    if sys.argv[1] == "closed":
        run = event["workflow_run"]
        check(run["conclusion"] == "success" and run["event"] in {"push", "workflow_dispatch"}
              and run["head_branch"] == "main" and run["head_repository"]["full_name"] == repo,
              "Expected successful main CI")
        sha = run["head_sha"]
    else:
        run_id = os.environ["CLOSED_RUN_ID"]
        check(run_id.isdigit(), "Run ID must be numeric")
        run = gh(f"repos/{repo}/actions/runs/{run_id}")
        check(run["conclusion"] == "success" and run["event"] == "workflow_run"
              and run["path"] == ".github/workflows/release-closed.yml"
              and run["head_branch"] == "main", "Expected successful closed-testing workflow")
        jobs = gh(f"repos/{repo}/actions/runs/{run_id}/jobs?per_page=100")
        check(any(j["name"] == "Publish closed testing" and j["conclusion"] == "success"
                  for j in jobs["jobs"]), "Closed publication job did not succeed")
        # workflow_run head_sha describes the workflow revision, not necessarily the CI commit.
        # A publication receipt records the actual release SHA after Play commits successfully.
        deployments = gh_list(f"repos/{repo}/deployments?environment=google-play-testing&per_page=100")
        matches = []
        for deployment in deployments:
            if deployment.get("task") != "google-play-testing-release":
                continue
            statuses = gh_list(deployment["statuses_url"])
            if any(s["state"] == "success" and
                   s.get("log_url", "").endswith(f"/actions/runs/{run_id}") for s in statuses):
                matches.append(deployment)
        check(len(matches) == 1, "Expected exactly one successful closed deployment for this run")
        sha = matches[0]["sha"]
    prs = gh_list(f"repos/{repo}/commits/{sha}/pulls?per_page=100")
    prs = [p for p in prs if p.get("merged_at") and p["merge_commit_sha"] == sha
           and p["base"]["ref"] == "main" and p["head"]["repo"]
           and p["head"]["repo"]["full_name"] == repo
           and p["head"]["ref"].startswith("codex/release/")]
    if sys.argv[1] == "closed" and not prs:
        with open(os.environ["GITHUB_OUTPUT"], "a") as out:
            out.write("release=false\n")
        return
    check(len(prs) == 1, "Expected one merged release PR")
    authorized(prs[0], repo, sha)
    subprocess.run(["git", "merge-base", "--is-ancestor", sha, "origin/main"], check=True)
    subprocess.run(["git", "checkout", "--detach", sha], check=True)
    data = validate(json.loads(Path("release/current.json").read_text()),
                    Path("app/build.gradle.kts").read_text())
    before = subprocess.check_output(["git", "show", f"{sha}^:app/build.gradle.kts"]).decode()
    old_name, old_code = version(before)
    check(data["versionName"] == bumped(old_name, data["bump"])
          and data["versionCode"] == old_code + 1, "Release bump is stale or invalid")
    with open(os.environ["GITHUB_OUTPUT"], "a") as out:
        out.write(f"release=true\nsha={sha}\nversion={data['versionName']}\n")


if __name__ == "__main__":
    main()
