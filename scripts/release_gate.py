"""Fail closed on unapproved releases and unrelated workflow runs."""
import json
import os
import subprocess
import sys
from pathlib import Path
from release import bumped, validate, version


def gh(path):
    return json.loads(subprocess.check_output(["gh", "api", path]))


def gh_list(path):
    pages = json.loads(subprocess.check_output(["gh", "api", "--paginate", "--slurp", path]))
    return [item for page in pages for item in page]


def check(condition, message):
    if not condition:
        raise ValueError(message)


def approved(pr, repo):
    reviews = gh_list(f"repos/{repo}/pulls/{pr['number']}/reviews?per_page=100")
    # Only the latest decisive review from each reviewer counts.
    latest = {}
    for review in reviews:
        if review["state"] in {"APPROVED", "CHANGES_REQUESTED", "DISMISSED"}:
            latest[review["user"]["login"]] = review
    check(not any(r["state"] == "CHANGES_REQUESTED" for r in latest.values()),
          "Release has outstanding change requests")
    approvers = [r["user"]["login"] for r in latest.values()
                 if r["state"] == "APPROVED" and r["commit_id"] == pr["head"]["sha"]
                 and r["user"].get("type") == "User"]
    # This personal repository's owner is the explicitly required release approver.
    # Avoid privileged collaborator lookups in a read-only gate.
    check(repo.split("/", 1)[0] in approvers,
          "Release needs repository-owner approval of its final PR commit")


def main():
    repo = os.environ["GITHUB_REPOSITORY"]
    event = json.loads(Path(os.environ["GITHUB_EVENT_PATH"]).read_text())
    if sys.argv[1] == "closed":
        run = event["workflow_run"]
        check(run["conclusion"] == "success" and run["event"] == "push"
              and run["head_branch"] == "main" and run["head_repository"]["full_name"] == repo,
              "Expected successful main push CI")
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
        deployments = gh_list(f"repos/{repo}/deployments?environment=play-closed&per_page=100")
        matches = []
        for deployment in deployments:
            if deployment.get("task") != "play-closed-release":
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
    approved(prs[0], repo)
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
