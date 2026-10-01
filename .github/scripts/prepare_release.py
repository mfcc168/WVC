"""Validate the built release APK before creating downloadable artifacts."""

import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess


def prepare_release():
    root = Path(__file__).resolve().parents[2]
    apk_dir = root / "app/build/outputs/apk/release"
    metadata = json.loads((apk_dir / "output-metadata.json").read_text())
    if metadata.get("applicationId") != "com.lafarge.wvc" or metadata.get("variantName") != "release":
        raise ValueError("Expected the WVC release variant.")
    elements = metadata.get("elements", [])
    if len(elements) != 1 or elements[0].get("filters"):
        raise ValueError("Expected one universal release APK.")
    artifact = elements[0]
    version = artifact["versionName"]
    if not re.fullmatch(r"[0-9]+(?:\.[0-9]+){1,2}(?:-[0-9A-Za-z]+(?:[.-][0-9A-Za-z]+)*)?", version):
        raise ValueError("Use a numeric versionName such as 1.1, 1.2.0, or 1.2.0-beta.1.")
    if not isinstance(artifact["versionCode"], int) or not 1 <= artifact["versionCode"] <= 2100000000:
        raise ValueError("versionCode must be a positive Android version code.")
    if os.environ.get("GITHUB_REF_TYPE") == "tag" and os.environ["GITHUB_REF_NAME"] != f"v{version}":
        raise ValueError(f"Release tag must match versionName exactly: expected v{version}.")
    filename = artifact["outputFile"]
    if Path(filename).name != filename or not filename.endswith(".apk"):
        raise ValueError("Invalid APK filename in build metadata.")
    apk = apk_dir / filename
    sdk = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
    if not sdk:
        raise ValueError("Set ANDROID_HOME to the Android SDK directory.")
    build_tools = Path(sdk) / "build-tools/36.0.0"
    # An unsigned APK or invalid signature fails here and never reaches a release.
    subprocess.run([str(build_tools / "apksigner"), "verify", "--verbose", str(apk)], check=True)
    badging = subprocess.check_output([str(build_tools / "aapt2"), "dump", "badging", str(apk)], text=True)
    if re.search(r"^application-debuggable", badging, re.MULTILINE):
        raise ValueError("A debuggable APK cannot be published as a release.")

    destination = root / "build/release-dist"
    destination.mkdir(parents=True, exist_ok=True)
    output = destination / f"WVC-{version}.apk"
    shutil.copyfile(apk, output)
    digest = hashlib.sha256(output.read_bytes()).hexdigest()
    (destination / "SHA256SUMS.txt").write_text(f"{digest}  {output.name}\n")
    if output_file := os.environ.get("GITHUB_OUTPUT"):
        with open(output_file, "a") as stream:
            stream.write(f"version={version}\nprerelease={str('-' in version).lower()}\n")
    if summary_file := os.environ.get("GITHUB_STEP_SUMMARY"):
        with open(summary_file, "a") as stream:
            stream.write(f"## WVC {version}\n\nSigned release APK: **{output.name}**\n\n")
            stream.write(f"Version code: {artifact['versionCode']}. Download the APK and checksum from this run's artifacts.\n")
    print(f"Prepared {output.name} and SHA256SUMS.txt")


if __name__ == "__main__":
    prepare_release()
