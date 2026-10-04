#!/usr/bin/env python3
"""Check that Central serves the exact artifacts from the audited signed candidate."""
import argparse
import hashlib
import pathlib
import time
import urllib.error
import urllib.request

ROOT = pathlib.Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser()
parser.add_argument("--version", required=True)
parser.add_argument("--repository", type=pathlib.Path, default=ROOT / "build/candidate-repository")
parser.add_argument("--wait-seconds", type=int, default=0)
args = parser.parse_args()
repository = args.repository.resolve()
group = repository / "com/latenighthack/ktstrings"
artifacts = sorted(
    file
    for directory in group.glob("*/" + args.version)
    for file in directory.iterdir()
    if file.suffix in {".pom", ".module", ".jar", ".aar", ".klib"}
)
assert artifacts, "No candidate publications found; audit the signed candidate first"
pending = set(artifacts)
deadline = time.monotonic() + args.wait_seconds
while pending:
    for file in sorted(pending):
        url = "https://repo.maven.apache.org/maven2/" + file.relative_to(repository).as_posix()
        try:
            with urllib.request.urlopen(url, timeout=30) as response:
                published = response.read()
        except (urllib.error.URLError, TimeoutError) as failure:
            print(f"Waiting for {file.name}: {failure}", flush=True)
            continue
        assert hashlib.sha256(published).digest() == hashlib.sha256(file.read_bytes()).digest(), f"Central content differs from signed candidate: {url}"
        pending.remove(file)
    if not pending:
        break
    remaining = deadline - time.monotonic()
    if remaining <= 0:
        raise SystemExit(f"Central is missing {len(pending)} artifacts: " + ", ".join(file.name for file in sorted(pending)))
    time.sleep(min(15, remaining))
print(f"Verified {len(artifacts)} artifacts across {len({file.parent.parent.name for file in artifacts})} publications on Maven Central for {args.version}.")
