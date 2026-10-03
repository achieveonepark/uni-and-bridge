# Unity package checks

The Android queue checks run with `./gradlew :android:testDebugUnitTest`.

To verify the actual Unity package, use a **separate empty Unity 6 project**. Copy `PackageInstaller.cs` and `UnityChecks.cs` into its `Assets/Editor/` directory. Set `UNITY_BRIDGE_PACKAGE_PATH` to this repository's absolute `unity/` path and install the local UPM package with the Editor binary (without `-quit`):

```sh
UNITY_BRIDGE_PACKAGE_PATH=/absolute/path/to/android-unity-bridge/unity \
  /path/to/Unity -batchmode -nographics -projectPath /path/to/check-project \
  -executeMethod AndroidUnityBridgePackageInstaller.Install -logFile /tmp/unity-bridge-install.log
```

Then run the synchronous checks:

```sh
unity run /path/to/check-project --timeout 180 -- -nographics -executeMethod AndroidUnityBridgeChecks.Run
```

The checks use Unity's real `JsonUtility`, verify session isolation and cached data, and confirm that the Java Activity is imported only for Android. They do not require Play Mode or an Android device.

The checks do not replace running the exported Unity player on a device. After integrating an export, verify initial launch, queued updates, events, completion, Android back navigation and reopening on an Android device.

## Source checks without Gradle or Editor IPC

If the environment blocks the local sockets required by Gradle or Unity, `compile_checks.py` compiles against installed Android/Unity SDKs and runs the queue tests directly. It uses only the Python standard library and existing local compiler/JUnit JARs; it downloads nothing.

Example on macOS (substitute your installed paths):

```sh
python3 verification/compile_checks.py \
  --unity-editor /Applications/Unity/Hub/Editor/6000.3.18f1 \
  --android-sdk "$HOME/Library/Android/sdk" \
  --gradle-home /path/to/unpacked/gradle-9.1.0
```

This checks Kotlin library and sample sources, three JVM queue tests, the Java Activity against Unity's actual player API, the Android C# branch, the C# sample, and package metadata. It does **not** verify the Gradle build scripts, produce an AAR, run Unity's `JsonUtility`, or run the app on a device.
