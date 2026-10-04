#!/usr/bin/env python3
"""Publish two candidates to an isolated repository and resolve them without substitutions."""
import argparse
import json
import pathlib
import shutil
import subprocess
import xml.etree.ElementTree as ET

ROOT = pathlib.Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser()
parser.add_argument('--versions', nargs=2, default=['0.1.0', '0.1.1-candidate-check'])
parser.add_argument('--all-platforms', action='store_true', help='Also build Android and resource-bearing Apple consumer artifacts')
args = parser.parse_args()
repository = ROOT / 'build/candidate-repository'

def run(command, cwd=ROOT):
    subprocess.run([str(value) for value in command], cwd=cwd, check=True)

def verify_pom(path):
    tree=ET.parse(path)
    ns={'m':'http://maven.apache.org/POM/4.0.0'}
    for name in ['name','description','url','licenses','developers','scm']:
        if tree.find('m:'+name,ns) is None: raise AssertionError(f'Missing {name}: {path}')
    text=path.read_text()
    if 'SNAPSHOT' in text or str(ROOT) in text: raise AssertionError(f'Unreleasable metadata: {path}')

for version in args.versions:
    run([ROOT/'gradlew', f'-PVERSION_NAME={version}', 'publishAllPublicationsToCandidateRepository', '--max-workers=4'])
    group=repository/'com/latenighthack/ktstrings'
    for artifact in ['ktstrings','ktstrings-jvm','ktstrings-compiler','ktstrings-gradle-plugin','ktstrings-compose','com.latenighthack.ktstrings.gradle.plugin']:
        pom=group/artifact/version/f'{artifact}-{version}.pom'
        if not pom.is_file(): raise AssertionError(f'Missing published artifact {pom}')
        verify_pom(pom)
    fixture=ROOT/'build/published-consumers'/version
    if fixture.exists(): shutil.rmtree(fixture)
    fixture.mkdir(parents=True)
    # Native compiler/link workers execute inside the consumer daemon; its defaults are too small.
    fixture.joinpath('gradle.properties').write_text('org.gradle.jvmargs=-Xmx3g -XX:MaxMetaspaceSize=1g -Dfile.encoding=UTF-8\norg.gradle.caching=true\nkotlin.mpp.enableCInteropCommonization.nowarn=true\n')
    shutil.copytree(ROOT/'integration/react/localization', fixture/'localization')
    fixture.joinpath('settings.gradle.kts').write_text(f'''pluginManagement {{ repositories {{ maven {{ url = uri("{repository.as_uri()}") }}; google(); mavenCentral(); gradlePluginPortal() }} }}
dependencyResolutionManagement {{ repositories {{ maven {{ url = uri("{repository.as_uri()}") }}; google(); mavenCentral() }} }}
rootProject.name = "ktstrings-published-consumer"
''')
    fixture.joinpath('build.gradle.kts').write_text(f'''plugins {{
    kotlin("multiplatform") version "2.3.10"
    {'id("com.android.library") version "8.13.2"' if args.all_platforms else ''}
    id("com.latenighthack.ktstrings") version "{version}"
}}
version = "1.2.3"
kotlin {{
    jvm()
    js(IR) {{ nodejs() }}
    {'androidTarget(); iosArm64 { binaries.framework { baseName="CandidateShared"; isStatic=true } }; iosSimulatorArm64 { binaries.framework { baseName="CandidateShared"; isStatic=true } }' if args.all_platforms else ''}
}}
{'android { namespace="fixture.consumer"; compileSdk=35; defaultConfig { minSdk=23 } }' if args.all_platforms else ''}
ktstrings {{
    kotlinPackage.set("fixture.messages")
    react {{ enabled.set(true); packageName.set("@ktstrings/acceptance") }}
    {'apple { enabled.set(true); frameworkName.set("CandidateShared"); frameworkBundleIdentifier.set("fixture.CandidateShared") }' if args.all_platforms else ''}
}}
''')
    source=fixture/'src/commonMain/kotlin/Consumer.kt'
    source.parent.mkdir(parents=True)
    source.write_text('package fixture\nimport com.latenighthack.ktstrings.UiText\nval greeting: UiText = fixture.messages.Messages.welcome("Ada")\n')
    if args.all_platforms:
        manifest=fixture/'src/androidMain/AndroidManifest.xml'
        manifest.parent.mkdir(parents=True)
        manifest.write_text('<manifest xmlns:android="http://schemas.android.com/apk/res/android"/>')
    tasks=['compileKotlinJvm','compileKotlinJs','collectKtstringsReact','check','verifyKtstringsPackaging']
    if args.all_platforms: tasks += ['assembleRelease','assembleKtstringsReleaseXCFramework']
    command=[ROOT/'gradlew','-p',fixture,*tasks,'--configuration-cache','--max-workers=4']
    run(command)
    run(command)
    run(['node', ROOT/'integration/react/verify.mjs',fixture/'build/outputs/ktstrings/react'])
    package=json.loads((fixture/'build/outputs/ktstrings/react/package.json').read_text())
    assert package['version']=='1.2.3'
    print(f'Published marker/compiler/runtime consumer passed for {version}')
