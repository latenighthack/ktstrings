#!/usr/bin/env python3
"""Audit an isolated Maven repository before any Central upload."""
import argparse
import hashlib
import pathlib
import subprocess
import tempfile
import xml.etree.ElementTree as ET
import zipfile

ROOT = pathlib.Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser()
parser.add_argument("--repository", type=pathlib.Path, default=ROOT / "build/candidate-repository")
parser.add_argument("--version", default="0.1.0")
parser.add_argument("--require-signatures", action="store_true")
parser.add_argument("--public-key", type=pathlib.Path)
parser.add_argument("--check-tag", action="store_true")
args = parser.parse_args()
if args.check_tag:
    properties = dict(line.split("=", 1) for line in (ROOT / "gradle.properties").read_text().splitlines() if "=" in line and not line.startswith("#"))
    assert properties["VERSION_NAME"] == args.version, "Requested release does not match authoritative VERSION_NAME"
    tag = subprocess.check_output(["git", "describe", "--tags", "--exact-match", "HEAD"], cwd=ROOT, text=True).strip()
    assert tag == "v" + args.version, f"Tag {tag} does not match release {args.version}"
assert "SNAPSHOT" not in args.version and args.version != "unspecified"
group = args.repository.resolve() / "com/latenighthack/ktstrings"
required = {"ktstrings", "ktstrings-jvm", "ktstrings-android", "ktstrings-js", "ktstrings-iosarm64", "ktstrings-iossimulatorarm64", "ktstrings-iosx64", "ktstrings-macosarm64", "ktstrings-macosx64", "ktstrings-compiler", "ktstrings-compose", "ktstrings-gradle-plugin", "com.latenighthack.ktstrings.gradle.plugin"}
actual = {directory.parent.name for directory in group.glob("*/" + args.version) if directory.is_dir()}
assert required <= actual, f"Missing publications: {required - actual}"
ns = {"m": "http://maven.apache.org/POM/4.0.0"}
with tempfile.TemporaryDirectory(prefix="ktstrings-signature-audit-") as gpg_home:
    if args.public_key:
        subprocess.run(["gpg", "--homedir", gpg_home, "--batch", "--import", str(args.public_key.resolve())], check=True)
    for artifact in sorted(actual):
        directory = group / artifact / args.version
        stem = f"{artifact}-{args.version}"
        pom = directory / (stem + ".pom")
        tree = ET.parse(pom)
        assert tree.findtext("m:groupId", namespaces=ns) == "com.latenighthack.ktstrings"
        assert tree.findtext("m:version", namespaces=ns) == args.version
        assert tree.findtext("m:artifactId", namespaces=ns) == artifact
        for field in ("name", "description", "url", "licenses", "developers", "scm"):
            assert tree.find("m:" + field, ns) is not None, f"Missing POM {field}: {artifact}"
        deployed = [file for file in directory.iterdir() if file.suffix not in {".md5", ".sha1", ".sha256", ".sha512", ".asc"}]
        for file in deployed:
            for algorithm in ("md5", "sha1"):
                checksum = directory / (file.name + "." + algorithm)
                assert checksum.is_file(), f"Missing checksum: {checksum}"
                assert checksum.read_text().strip() == hashlib.new(algorithm, file.read_bytes()).hexdigest(), f"Bad checksum: {checksum}"
            if args.require_signatures:
                signature = directory / (file.name + ".asc")
                assert signature.is_file(), f"Missing signature: {signature}"
                command = ["gpg", "--batch"]
                if args.public_key:
                    command += ["--homedir", gpg_home]
                subprocess.run(command + ["--verify", str(signature), str(file)], check=True, stdout=subprocess.DEVNULL, stderr=subprocess.PIPE)
        if artifact != "com.latenighthack.ktstrings.gradle.plugin":
            assert (directory / (stem + ".module")).is_file(), f"Missing Gradle metadata: {artifact}"
            sources = directory / (stem + "-sources.jar")
            docs = directory / (stem + "-javadoc.jar")
            assert sources.is_file() and docs.is_file(), f"Missing sources/documentation: {artifact}"
            with zipfile.ZipFile(docs) as archive:
                assert "README.md" in archive.namelist(), f"Documentation archive has no project usage guide: {artifact}"
            for text_file in [pom, directory / (stem + ".module")]:
                contents = text_file.read_text()
                assert str(ROOT) not in contents and "SNAPSHOT" not in contents, f"Workspace/snapshot metadata: {text_file}"
print(f"Verified {len(actual)} publications for {args.version}: coordinates, POMs, Gradle metadata, sources, documentation, checksums" + (", valid PGP signatures" if args.require_signatures else "; unsigned development candidate"))
