#!/usr/bin/env python3
"""Run generated Foundation formatting code, independently of Kotlin's bridge."""
import argparse
import pathlib
import plistlib
import shutil
import subprocess

parser = argparse.ArgumentParser()
parser.add_argument("generated", type=pathlib.Path, help="generated root for integration/apple/localization")
parser.add_argument("--output", type=pathlib.Path, default=pathlib.Path("build/apple-native-proof"))
args = parser.parse_args()
apple = args.generated.resolve() / "apple"
output = args.output.resolve()
output.mkdir(parents=True, exist_ok=True)
metadata = dict(line.split("=", 1) for line in (apple / "catalog.properties").read_text().splitlines())
bundle = output / (metadata["frameworkName"] + ".framework")
bundle.mkdir(exist_ok=True)
for directory in (apple / "resources").iterdir():
    shutil.copytree(directory, bundle / directory.name, dirs_exist_ok=True)
with (bundle / "Info.plist").open("wb") as stream:
    plistlib.dump({"CFBundleIdentifier": metadata["bundleIdentifier"], "CFBundlePackageType": "FMWK", "CFBundleDevelopmentRegion": metadata["sourceLocale"], "CFBundleLocalizations": metadata["locales"].split(",")}, stream)
test = output / "proof.m"
test.write_text(r'''#import <Foundation/Foundation.h>
#import "Ktstrings_appleproof.h"
static void check(NSString *actual, NSString *expected) {
    if (![actual isEqualToString:expected]) { NSLog(@"Expected %@, got %@", expected, actual); exit(1); }
}
int main(int argc, char **argv) { @autoreleasepool {
    NSString *bundle = [NSString stringWithUTF8String:argv[1]];
    check(ktstrings_appleproof_welcome(bundle, @"en", @"Ada"), @"Welcome, Ada");
    check(ktstrings_appleproof_welcome(bundle, @"fr", @"Ada"), @"Bienvenue, Ada");
    check(ktstrings_appleproof_items_count(bundle, @"en", 0), @"0 items");
    check(ktstrings_appleproof_items_count(bundle, @"en", 1), @"1 item");
    check(ktstrings_appleproof_items_count(bundle, @"en", 2), @"2 items");
    check(ktstrings_appleproof_items_count(bundle, @"en", INT32_MAX), @"2,147,483,647 items");
    check(ktstrings_appleproof_items_count(bundle, @"fr", 0), @"0 élément");
    check(ktstrings_appleproof_reordered(bundle, @"en", @"Ada", 1), @"Ada: 1 item (Ada)");
    check(ktstrings_appleproof_reordered(bundle, @"en", @"Ada", 3), @"Ada: 3 items (Ada)");
    check(ktstrings_appleproof_literal(bundle, @"en"), @"100% {braces} \\ \"quote\"\n$({name})");
    for (int value = 0; value < 200; value++) {
        NSString *ar = ktstrings_appleproof_items_count(bundle, @"ar", value);
        if (!ar || [ar isEqualToString:@"items.count"]) return 2;
    }
    if (ktstrings_appleproof_welcome(@"/missing.framework", @"en", @"Ada") != nil) return 3;
    puts("Foundation native proof passed: source/French/Arabic, selector position, repetition, literals, Int.MAX_VALUE, missing bundle");
} return 0; }
''')
binary = output / "proof"
subprocess.run(["xcrun", "clang", "-fobjc-arc", "-framework", "Foundation", "-I", str(apple / "native"), str(apple / "native/Ktstrings_appleproof.m"), str(test), "-o", str(binary)], check=True)
subprocess.run([str(binary), str(bundle)], check=True)
