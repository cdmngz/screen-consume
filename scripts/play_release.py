"""Publish a signed bundle or promote its existing version using the Play API.

Uses Python's standard library and OpenSSL; credentials never become CLI arguments.
"""
import base64
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import time
from urllib.error import HTTPError
from urllib.parse import quote, urlencode
from urllib.request import Request, urlopen
from release import validate

PACKAGE = "org.screenconsume.app"
BASE = f"https://androidpublisher.googleapis.com/androidpublisher/v3/applications/{PACKAGE}"


def request(url, method="GET", data=None, token=None, content_type="application/json"):
    headers = {"Content-Type": content_type}
    if token:
        headers["Authorization"] = "Bearer " + token
    body = json.dumps(data).encode() if isinstance(data, dict) else data
    try:
        with urlopen(Request(url, data=body, headers=headers, method=method), timeout=180) as response:
            body = response.read()
            return json.loads(body) if body else {}
    except HTTPError as error:
        # Do not print response bodies or request headers: they may contain sensitive data.
        raise RuntimeError(f"Remote API rejected {method} request (HTTP {error.code})") from None


def b64(data):
    return base64.urlsafe_b64encode(data).rstrip(b"=")


def token():
    credentials = json.loads(os.environ["PLAY_SERVICE_ACCOUNT_JSON"])
    now = int(time.time())
    payload = {"iss": credentials["client_email"],
               "scope": "https://www.googleapis.com/auth/androidpublisher",
               "aud": "https://oauth2.googleapis.com/token", "iat": now, "exp": now + 3600}
    message = b".".join(b64(json.dumps(part).encode()) for part in
                         ({"alg": "RS256", "typ": "JWT"}, payload))
    with tempfile.TemporaryDirectory() as directory:
        key = Path(directory) / "account.pem"
        key.write_text(credentials["private_key"])
        key.chmod(0o600)
        signature = subprocess.check_output(["openssl", "dgst", "-sha256", "-sign", str(key)],
                                            input=message, stderr=subprocess.DEVNULL)
    assertion = (message + b"." + b64(signature)).decode()
    result = request("https://oauth2.googleapis.com/token", "POST", urlencode({
        "grant_type": "urn:ietf:params:oauth:grant-type:jwt-bearer", "assertion": assertion
    }).encode(), content_type="application/x-www-form-urlencoded")
    return result["access_token"]


def sign(bundle):
    expected = os.environ["PLAY_UPLOAD_CERT_SHA256"].replace(":", "").lower()
    if len(expected) != 64 or any(c not in "0123456789abcdef" for c in expected):
        raise ValueError("Configure upload certificate SHA-256")
    with tempfile.TemporaryDirectory() as directory:
        key = Path(directory) / "upload.jks"
        key.write_bytes(base64.b64decode(os.environ["PLAY_UPLOAD_KEYSTORE_BASE64"], validate=True))
        key.chmod(0o600)
        alias = os.environ["PLAY_UPLOAD_KEY_ALIAS"]
        certificate = subprocess.check_output([
            "keytool", "-exportcert", "-keystore", str(key), "-alias", alias,
            "-storepass:env", "PLAY_UPLOAD_STORE_PASSWORD"], stderr=subprocess.DEVNULL)
        if hashlib.sha256(certificate).hexdigest() != expected:
            raise ValueError("Upload certificate does not match configured fingerprint")
        subprocess.run(["jarsigner", "-keystore", str(key),
                        "-storepass:env", "PLAY_UPLOAD_STORE_PASSWORD",
                        "-keypass:env", "PLAY_UPLOAD_KEY_PASSWORD", "-sigalg", "SHA256withRSA",
                        "-digestalg", "SHA-256", str(bundle), alias],
                       check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        # Verify signatures and require a signer (jarsigner alone accepts unsigned archives).
        result = subprocess.check_output(["jarsigner", "-verify", str(bundle)],
                                         stderr=subprocess.DEVNULL).decode()
        if "jar verified." not in result:
            raise ValueError("Bundle signature verification failed")
        signed_certificate = subprocess.check_output([
            "keytool", "-printcert", "-rfc", "-jarfile", str(bundle)], stderr=subprocess.DEVNULL)
        der = subprocess.check_output(["openssl", "x509", "-outform", "DER"],
                                      input=signed_certificate, stderr=subprocess.DEVNULL)
        if hashlib.sha256(der).hexdigest() != expected:
            raise ValueError("Signed bundle has unexpected upload certificate")


def completed_release(track, code):
    return next((r for r in track.get("releases", [])
                 if r.get("status") == "completed" and r.get("versionCodes") == [str(code)]), None)


def publish(mode):
    data = validate(json.loads(Path("release/current.json").read_text()),
                    Path("app/build.gradle.kts").read_text())
    closed = os.environ["PLAY_CLOSED_TRACK"]
    if not closed or closed in {"production", "internal"}:
        raise ValueError("Configure an existing closed-testing track ID")
    access = token()
    edit = request(BASE + "/edits", "POST", {}, access)["id"]
    base = BASE + "/edits/" + quote(edit, safe="")
    try:
        tracks = request(base + "/tracks", token=access)["tracks"]
        track = next((t for t in tracks if t["track"] == closed), None)
        if track is None:
            raise ValueError("Configured closed-testing track does not exist")
        code = data["versionCode"]
        production = next((t for t in tracks if t["track"] == "production"), {})
        production_codes = [int(c) for r in production.get("releases", [])
                            for c in r.get("versionCodes", [])]
        if production_codes and max(production_codes) >= code:
            raise ValueError("Production already contains this version or a newer version")
        notes = [{"language": language, "text": text} for language, text in data["notes"].items()]
        if mode == "closed":
            # Safe rerun after a successful commit but interrupted GitHub receipt step.
            existing = completed_release(track, code)
            if existing:
                if {n["language"]: n["text"] for n in existing.get("releaseNotes", [])} != data["notes"]:
                    raise ValueError("Already published version has different release notes")
                return
            codes = [int(c) for t in tracks for r in t.get("releases", [])
                     for c in r.get("versionCodes", [])]
            if codes and max(codes) >= code:
                raise ValueError("Version code must exceed all existing track releases")
            bundle = Path("app/build/outputs/bundle/release/app-release.aab")
            sign(bundle)
            uploaded = request(
                f"https://androidpublisher.googleapis.com/upload/androidpublisher/v3/applications/"
                f"{PACKAGE}/edits/{quote(edit, safe='')}/bundles?uploadType=media",
                "POST", bundle.read_bytes(), access, "application/octet-stream")
            if uploaded.get("versionCode") != code:
                raise ValueError("Uploaded bundle has unexpected version code")
            target = closed
        else:
            if not completed_release(track, code):
                raise ValueError("Selected version is not completed on the closed-testing track")
            target = "production"
        release = {"name": data["versionName"], "versionCodes": [str(code)],
                   "status": "completed", "releaseNotes": notes}
        request(base + "/tracks/" + quote(target, safe=""), "PUT",
                {"track": target, "releases": [release]}, access)
        request(base + ":validate", "POST", token=access)
        request(base + ":commit?changesInReviewBehavior=ERROR_IF_IN_REVIEW", "POST", token=access)
        print(f"Submitted {data['versionName']} ({code}) to {target}")
    finally:
        # Committed edits may no longer exist; only best-effort cleanup is necessary.
        try:
            request(base, "DELETE", token=access)
        except (RuntimeError, ValueError):
            pass


if __name__ == "__main__":
    if len(sys.argv) != 2 or sys.argv[1] not in {"closed", "production"}:
        raise SystemExit("Usage: play_release.py closed|production")
    publish(sys.argv[1])
