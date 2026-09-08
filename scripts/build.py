#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Build a signed lightweight APK using Android SDK command-line tools (no Gradle)."""
import argparse
import hashlib
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import zipfile

ROOT = Path(__file__).resolve().parents[1]




def run(args, capture=False):
    return subprocess.run([str(a) for a in args], check=True, text=True,
                          stdout=subprocess.PIPE if capture else None).stdout


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--keystore", type=Path, required=True)
    parser.add_argument("--alias", default="launcher")
    parser.add_argument("--sdk", default=os.environ.get("ANDROID_SDK_ROOT") or os.environ.get("ANDROID_HOME"))
    parser.add_argument("--build-tools", default="36.0.0")
    parser.add_argument("--api", default="35")
    args = parser.parse_args()
    if not args.sdk:
        parser.error("Set ANDROID_SDK_ROOT or pass --sdk")
    if not os.environ.get("TASK_CENTER_STORE_PASS"):
        parser.error("Set TASK_CENTER_STORE_PASS (not a command-line password)")
    if not args.keystore.is_file():
        parser.error("Signing keystore must exist")
    sdk = Path(args.sdk).expanduser()
    bt = sdk / "build-tools" / args.build_tools
    android = sdk / "platforms" / ("android-" + args.api) / "android.jar"
    suffix = ".bat" if os.name == "nt" else ""
    exe = ".exe" if os.name == "nt" else ""
    signer, d8 = bt / ("apksigner" + suffix), bt / ("d8" + suffix)
    aapt, align = bt / ("aapt" + exe), bt / ("zipalign" + exe)
    for tool in [signer, d8, aapt, align, android]:
        if not tool.is_file():
            parser.error("Missing SDK tool: " + str(tool))
    build = ROOT / "build"
    build.mkdir(exist_ok=True)
    out = build / "Task-Center-Installer-3.1.apk"
    with tempfile.TemporaryDirectory(prefix="task-center-", dir=build) as tmp:
        tmp = Path(tmp)
        assets, classes, dex = tmp / "assets", tmp / "classes", tmp / "dex"
        for folder in [assets, classes, dex]:
            folder.mkdir()
        for item in (ROOT / "app/assets").iterdir():
            if item.is_file():
                shutil.copyfile(item, assets / item.name)
        # Only our own source is Apache-licensed; ship both notices with new builds.
        for name in ["LICENSE", "NOTICE", "THIRD_PARTY_NOTICES.md"]:
            shutil.copyfile(ROOT / name, assets / name)
        unsigned, aligned = tmp / "unsigned.apk", tmp / "aligned.apk"
        run([aapt, "package", "-f", "-M", ROOT / "app/AndroidManifest.xml",
             "-S", ROOT / "app/res", "-A", assets, "-I", android, "-F", unsigned])
        sources = sorted((ROOT / "app/src").rglob("*.java"))
        run(["javac", "-source", "8", "-target", "8", "-encoding", "UTF-8",
             "-classpath", android, "-d", classes, *sources])
        run([d8, "--min-api", "28", "--lib", android, "--output", dex,
             *sorted(classes.rglob("*.class"))])
        with zipfile.ZipFile(unsigned, "a") as archive:
            for item in sorted(dex.glob("*.dex")):
                archive.write(item, item.name)
        run([align, "-f", "4", unsigned, aligned])
        sign = [signer, "sign", "--ks", args.keystore, "--ks-key-alias", args.alias,
                "--ks-pass", "env:TASK_CENTER_STORE_PASS", "--out", out, aligned]
        if os.environ.get("TASK_CENTER_KEY_PASS"):
            sign += ["--key-pass", "env:TASK_CENTER_KEY_PASS"]
        run(sign)
    run([signer, "verify", "--print-certs", out])
    with zipfile.ZipFile(out) as archive:
        assert "assets/security.apk" not in archive.namelist()
        assert archive.read("assets/catalog.signed.json") == (ROOT / "app/assets/catalog.signed.json").read_bytes()
        assert len(archive.read("classes.dex")) > 5000
    digest = hashlib.sha256(out.read_bytes()).hexdigest()
    (build / "SHA256SUMS.txt").write_text(f"{digest}  {out.name}\n", encoding="utf-8")
    print(f"Built {out} ({out.stat().st_size / 1024**2:.1f} MiB)")


if __name__ == "__main__":
    try:
        main()
    except subprocess.CalledProcessError as exc:
        sys.exit(f"Build failed (exit {exc.returncode})")
