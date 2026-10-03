#!/usr/bin/env python3
"""Compile against installed SDKs and run JVM checks without a Gradle daemon.

This checks sources, not an AAR build or device integration. No downloads are performed.
"""

import argparse
import json
import os
from pathlib import Path
import subprocess
import xml.etree.ElementTree as ET


def run(*args):
    subprocess.run([str(arg) for arg in args], check=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--unity-editor', type=Path, required=True, help='Unity installation directory, containing Unity.app and PlaybackEngines on macOS')
    parser.add_argument('--android-sdk', type=Path, required=True)
    parser.add_argument('--gradle-home', type=Path, required=True, help='Unpacked Gradle distribution containing lib/')
    parser.add_argument('--dependency-cache', type=Path, default=Path.home() / '.gradle/caches/modules-2/files-2.1')
    args = parser.parse_args()
    root = Path(__file__).resolve().parent.parent
    output = root / '.verification/compile'
    output.mkdir(parents=True, exist_ok=True)
    android = args.android_sdk / 'platforms/android-36/android.jar'
    player = args.unity_editor / 'PlaybackEngines/AndroidPlayer'
    java = player / 'OpenJDK/bin/java'
    javac = player / 'OpenJDK/bin/javac'
    kotlin_libs = args.gradle_home / 'lib'
    stdlib = next(kotlin_libs.glob('kotlin-stdlib-[0-9]*.jar'))
    junit = next(args.dependency_cache.glob('junit/junit/4.13.2/*/junit-4.13.2.jar'))
    hamcrest = next(args.dependency_cache.glob('org.hamcrest/hamcrest-core/1.3/*/hamcrest-core-1.3.jar'))
    classes = output / 'kotlin'
    classes.mkdir(exist_ok=True)
    kotlin_sources = sorted((root / 'android/src').rglob('*.kt')) + sorted((root / 'samples/android/src').rglob('*.kt'))
    run(java, '-cp', kotlin_libs / '*', 'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler',
        '-no-stdlib', '-no-reflect', '-jvm-target', '17',
        '-classpath', os.pathsep.join(map(str, [android, stdlib, junit, hamcrest])),
        '-d', classes, *kotlin_sources)
    run(java, '-cp', os.pathsep.join(map(str, [classes, stdlib, junit, hamcrest])),
        'org.junit.runner.JUnitCore', 'com.achieve.unitybridge.BridgeSessionTest')

    java_classes = output / 'java'
    java_classes.mkdir(exist_ok=True)
    unity_classes = player / 'Variations/il2cpp/Release/Classes/classes.jar'
    run(javac, '--release', '17', '-classpath', os.pathsep.join(map(str, [android, unity_classes])),
        '-d', java_classes,
        player / 'Source/com/unity3d/player/UnityPlayerActivity.java',
        root / 'unity/Runtime/Plugins/Android/BridgeUnityActivity.java')

    scripting = args.unity_editor / 'Unity.app/Contents/Resources/Scripting'
    dotnet = scripting / 'NetCoreRuntime/dotnet'
    compiler = scripting / 'DotNetSdkRoslyn/csc.dll'
    references = sorted((scripting / 'NetStandard/ref/2.1.0').glob('*.dll'))
    references += sorted((scripting / 'Managed/UnityEngine').glob('*.dll'))
    runtime_output = output / 'Achieve.UnityBridge.dll'
    response = output / 'runtime.rsp'
    response.write_text('\n'.join([
        '-nologo', '-target:library', '-nostdlib+', '-define:UNITY_ANDROID',
        f'-out:"{runtime_output}"',
        *[f'-r:"{ref}"' for ref in references],
        *[f'"{source}"' for source in sorted((root / 'unity/Runtime').glob('*.cs'))],
    ]))
    run(dotnet, compiler, '@' + str(response))
    response = output / 'sample.rsp'
    response.write_text('\n'.join([
        '-nologo', '-target:library', '-nostdlib+', f'-out:"{output / "BridgeExample.dll"}"',
        *[f'-r:"{ref}"' for ref in [*references, runtime_output]],
        f'"{root / "unity/Samples~/BridgeExample/BridgeExample.cs"}"',
    ]))
    run(dotnet, compiler, '@' + str(response))

    manifest = json.loads((root / 'unity/package.json').read_text())
    assert manifest['name'] == 'com.achieve.unitybridge'
    assert json.loads((root / 'unity/Runtime/Achieve.UnityBridge.asmdef').read_text())['name'] == 'Achieve.UnityBridge'
    for path in root.rglob('AndroidManifest.xml'):
        if '.verification' not in path.parts:
            ET.parse(path)
    print('Passed: Kotlin library and sample compilation, 3 JVM tests, Java Activity compilation, Android C# compilation, C# sample compilation, package metadata.')


if __name__ == '__main__':
    main()
