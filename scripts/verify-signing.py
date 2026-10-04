#!/usr/bin/env python3
"""Qualify publication signing with a disposable key; never contact Central."""
import argparse
import os
import pathlib
import subprocess
import tempfile

root = pathlib.Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser()
parser.add_argument("--version", default="0.1.6-signing-proof")
args = parser.parse_args()
with tempfile.TemporaryDirectory(prefix="ktstrings-disposable-signing-") as signing_home:
    gpg = ["gpg", "--homedir", signing_home, "--batch", "--pinentry-mode", "loopback", "--passphrase", ""]
    subprocess.run(gpg + ["--quick-generate-key", "ktstrings disposable candidate proof <candidate-test@example.invalid>", "rsa2048", "sign", "1d"], check=True, stdout=subprocess.DEVNULL, stderr=subprocess.PIPE)
    identities = subprocess.check_output(gpg + ["--with-colons", "--list-secret-keys"], stderr=subprocess.PIPE).decode()
    fingerprint = next(line.split(":")[9] for line in identities.splitlines() if line.startswith("fpr:"))
    public = pathlib.Path(signing_home) / "public.asc"
    public.write_bytes(subprocess.check_output(gpg + ["--armor", "--export", fingerprint]))
    private = subprocess.check_output(gpg + ["--armor", "--export-secret-keys", fingerprint]).decode()
    environment = dict(os.environ)
    environment["ORG_GRADLE_PROJECT_signingInMemoryKey"] = private
    environment["ORG_GRADLE_PROJECT_signingInMemoryKeyId"] = fingerprint[-8:]
    environment["ORG_GRADLE_PROJECT_signingInMemoryKeyPassword"] = ""
    subprocess.run([str(root / "gradlew"), "-PreleaseSigning=true", f"-PVERSION_NAME={args.version}", "publishAllPublicationsToCandidateRepository", "--max-workers=2"], cwd=root, env=environment, check=True)
    subprocess.run(["python3", str(root / "scripts/verify-release-metadata.py"), "--version", args.version, "--require-signatures", "--public-key", str(public)], cwd=root, check=True)
print("Disposable signing proof passed; temporary private key removed; no remote upload performed.")
