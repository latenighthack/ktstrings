#!/usr/bin/env python3
"""Qualify actual generated Kotlin XCFrameworks through direct Xcode embedding."""
import argparse
import hashlib
import json
import pathlib
import plistlib
import subprocess
import sys

parser = argparse.ArgumentParser()
parser.add_argument("xcframework", type=pathlib.Path)
parser.add_argument("--kind", choices=["static", "dynamic"], required=True)
parser.add_argument("--simulator", help="Available iPhone simulator UDID; automatically selected when omitted")
parser.add_argument("--output", type=pathlib.Path, default=pathlib.Path("build/apple-distribution-proof"))
args = parser.parse_args()
if args.simulator is None:
    inventory = json.loads(subprocess.check_output(["xcrun", "simctl", "list", "devices", "available", "--json"]))
    choices = [device for runtime, devices in inventory["devices"].items() if "iOS" in runtime for device in devices if device.get("isAvailable", True)]
    assert choices, "Apple proof requires an available iOS simulator"
    args.simulator = next((device["udid"] for device in choices if "Basekit bindings acceptance" == device["name"]), choices[0]["udid"])
xcframework = args.xcframework.resolve()
output = args.output.resolve() / args.kind
output.mkdir(parents=True, exist_ok=True)

def run(*command, **kwargs):
    return subprocess.run([str(part) for part in command], check=True, **kwargs)

def resource_root(framework):
    return framework / "Versions/Current/Resources" if (framework / "Versions").exists() else framework

frameworks = sorted(xcframework.glob("*/Shared.framework"))
assert frameworks, "XCFramework has no framework slices"
expected = None
for framework in frameworks:
    resources = resource_root(framework)
    with (resources / "Info.plist").open("rb") as stream:
        info = plistlib.load(stream)
    assert info["CFBundleIdentifier"] == "com.example.Shared"
    assert info["CFBundleDevelopmentRegion"] == "en"
    assert set(info["CFBundleLocalizations"]) == {"en", "fr", "ar", "ru", "ja", "en-GB"}
    contents = {str(file.relative_to(resources)): hashlib.sha256(file.read_bytes()).hexdigest() for file in resources.glob("*.lproj/*")}
    assert len(contents) == 12, f"Missing resources in {framework}"
    if expected is None:
        expected = contents
    assert contents == expected, "Device, simulator and macOS translation contents differ"
    symbols = run("nm", framework / "Shared", stdout=subprocess.PIPE).stdout.decode()
    assert "ktstrings_appleproof_items_count" in symbols, "Native helper absent from Kotlin framework"

swift = '''import UIKit
import Shared
@main final class AppDelegate: UIResponder, UIApplicationDelegate {
    var window: UIWindow?
    func application(_ application: UIApplication, didFinishLaunchingWithOptions options: [UIApplication.LaunchOptionsKey: Any]?) -> Bool {
        let resolver = AppleMessagesResolver(bundlePath: nil)
        let greeting = Messages.shared.welcome(name: "Ada")
        precondition(resolver.resolve(text: greeting, requestedLocale: "fr-CA") == "Bienvenue, Ada")
        precondition(resolver.resolve(text: greeting, requestedLocale: " fr ") == "Bienvenue, Ada")
        precondition(resolver.resolve(text: greeting, requestedLocale: "fr-CA-u-nu-latn") == "Bienvenue, Ada")
        precondition(resolver.resolve(text: greeting, requestedLocale: "ar") == "Welcome, Ada")
        precondition(resolver.resolve(text: greeting, requestedLocale: "en-GB") == "Hello, Ada")
        precondition(resolver.resolve(text: Messages.shared.itemsCount(count: 23), requestedLocale: "ru").hasSuffix("несколько"))
        precondition(resolver.resolve(text: Messages.shared.itemsCount(count: 12), requestedLocale: "ru").hasSuffix("много"))
        precondition(resolver.resolve(text: Messages.shared.itemsCount(count: 1), requestedLocale: "ja").hasSuffix("個"))
        let overridden = AppleMessagesResolver(bundlePath: Bundle.main.privateFrameworksPath! + "/Shared.framework")
        precondition(overridden.resolve(text: greeting, requestedLocale: "en") == "Welcome, Ada")
        let plural = Messages.shared.itemsCount(count: 3)
        precondition(resolver.resolve(text: plural, requestedLocale: "en") == "3 items")
        precondition(resolver.resolve(text: plural, requestedLocale: "ar").hasSuffix("قليل"))
        precondition(resolver.resolve(text: LiteralText(text: "historical literal"), requestedLocale: "ar") == "historical literal")
        print("KTSTRINGS_APPLE_SIMULATOR_PASS")
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.1) { exit(0) }
        return true
    }
}
'''
(output / "App.swift").write_text(swift)
(output / "project.yml").write_text(f'''name: KtstringsAppleProof
options:
  deploymentTarget:
    iOS: "15.0"
targets:
  Proof:
    type: application
    platform: iOS
    sources: [App.swift]
    settings:
      base:
        PRODUCT_BUNDLE_IDENTIFIER: com.example.ktstrings.appleproof
        GENERATE_INFOPLIST_FILE: YES
        CODE_SIGNING_ALLOWED: NO
    dependencies:
      - framework: {xcframework}
        embed: true
        codeSign: true
''')
run("xcodegen", "generate", "--spec", output / "project.yml", "--project", output)
project = output / "KtstringsAppleProof.xcodeproj"
derived = output / "DerivedData"
run("xcodebuild", "-project", project, "-scheme", "Proof", "-configuration", "Release", "-sdk", "iphonesimulator", "-destination", f"id={args.simulator}", "-derivedDataPath", derived, "build", stdout=(output / "simulator-build.log").open("w"), stderr=subprocess.STDOUT)
app = derived / "Build/Products/Release-iphonesimulator/Proof.app"
embedded = app / "Frameworks/Shared.framework"
assert embedded.is_dir(), "Xcode failed to embed resource-bearing framework"
assert (embedded / "en.lproj/Ktstrings_appleproof.stringsdict").is_file()
if args.kind == "static":
    if (embedded / "Shared").exists():
        # Xcode 26 injects a codeless dylib stub after removing the static archive.
        stub_symbols = run("nm", embedded / "Shared", stdout=subprocess.PIPE).stdout
        assert b"ktstrings_appleproof" not in stub_symbols and b"Kotlin" not in stub_symbols, "Static framework implementation was incorrectly embedded"
    app_symbols = run("nm", app / "Proof", stdout=subprocess.PIPE).stdout
    assert b"ktstrings_appleproof" in app_symbols, "Static helper was not linked into application"
else:
    assert (embedded / "Shared").is_file()
if (embedded / "Shared").exists():
    run("codesign", "--force", "--sign", "-", embedded)
run("codesign", "--force", "--sign", "-", app)
run("codesign", "--verify", "--deep", "--strict", app)
run("xcrun", "simctl", "bootstatus", args.simulator, "-b")
run("xcrun", "simctl", "install", args.simulator, app)
launch = run("xcrun", "simctl", "launch", "--console", args.simulator, "com.example.ktstrings.appleproof", stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
print(launch.stdout.decode())
assert b"KTSTRINGS_APPLE_SIMULATOR_PASS" in launch.stdout, "Simulator did not prove native lookup"
archive = output / "Proof.xcarchive"
run("xcodebuild", "-project", project, "-scheme", "Proof", "-configuration", "Release", "-destination", "generic/platform=iOS", "-archivePath", archive, "archive", stdout=(output / "archive-build.log").open("w"), stderr=subprocess.STDOUT)
archived_app = archive / "Products/Applications/Proof.app"
archived_framework = archived_app / "Frameworks/Shared.framework"
assert (archived_framework / "en.lproj/Ktstrings_appleproof.stringsdict").is_file(), "Device archive lost translations"
if args.kind == "static" and (archived_framework / "Shared").exists():
    stub_symbols = run("nm", archived_framework / "Shared", stdout=subprocess.PIPE).stdout
    assert b"ktstrings_appleproof" not in stub_symbols and b"Kotlin" not in stub_symbols
if (archived_framework / "Shared").exists():
    run("codesign", "--force", "--sign", "-", archived_framework)
run("codesign", "--force", "--sign", "-", archived_app)
run("codesign", "--verify", "--deep", "--strict", archived_app)

macos = next(framework for framework in frameworks if "macos" in str(framework.parent))
native_swift = output / "MacProof.swift"
native_swift.write_text('''import Foundation
import Shared
let resolver = AppleMessagesResolver(bundlePath: CommandLine.arguments[1])
precondition(resolver.resolve(text: Messages.shared.welcome(name: "Ada"), requestedLocale: "fr-CA") == "Bienvenue, Ada")
print("KTSTRINGS_MACOS_PASS")
''')
native = output / "MacProof"
run("xcrun", "swiftc", "-F", macos.parent, "-framework", "Shared", "-Xlinker", "-rpath", "-Xlinker", macos.parent, native_swift, "-o", native)
run(native, macos)
negative = output / "Negative.swift"
for call in ['Messages.shared.welcome(name: 42)', 'Messages.shared.welcome()']:
    negative.write_text('import Shared\nlet invalid = ' + call + '\n')
    result = subprocess.run(["xcrun", "swiftc", "-typecheck", "-F", str(macos.parent), str(negative)], stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    assert result.returncode != 0, "Invalid Swift arguments unexpectedly compiled"
print(f"{args.kind} Kotlin XCFramework qualified: all slices, Swift typing, simulator, device archive, embedded binary policy, signatures, macOS")
