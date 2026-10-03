# Android Unity Bridge

Android 앱에서 Unity 화면을 열고 데이터를 전달한 뒤, Unity 이벤트와 결과를 받아 앱으로 돌아오는 작은 브리지입니다. 하나의 저장소에서 **Android 라이브러리(AAR)**와 **Unity 패키지(UPM)**를 함께 관리합니다.

- Unity의 수신 오브젝트를 자동으로 만들고, 준비 완료 전 메시지는 순서대로 보관합니다.
- 초기 데이터, Android → Unity 메시지, Unity → Android 이벤트, 완료 결과를 구분합니다.
- 세션 ID로 이전 실행의 늦은 메시지를 무시합니다.
- 뒤로가기와 취소를 처리하고, 기본 30초 동안 준비되지 않으면 오류를 반환합니다.
- 복귀할 때 Unity를 종료하지 않고 일시정지하여 다시 사용할 수 있게 합니다.

## 구성

| 경로 | 내용 |
| --- | --- |
| `android/` | Kotlin Android 라이브러리, AAR 빌드와 로컬 Maven 배포 |
| `unity/` | C# UPM 패키지와 Unity에서 컴파일되는 Android Activity |
| `samples/android/` | Unity export를 연결하면 빌드할 수 있는 Android 샘플 앱 |
| `unity/Samples~/BridgeExample/` | 데이터 수신과 결과 반환을 보여 주는 Unity 컴포넌트 |
| `verification/` | Unity 메시지 처리 검증 코드와 실행 안내 |

## 지원 범위

두 플랫폼의 패키지 이름은 `com.achieve.unitybridge`입니다. 첫 버전은 **Unity 6, Android의 Activity 진입점, Android API 23 이상**을 대상으로 합니다. Unity Player Settings의 Android **Application Entry Point를 Activity로 설정**하세요. GameActivity는 아직 지원하지 않습니다. 라이브러리 빌드는 Android SDK 36과 JDK 17 또는 21이 필요합니다.

앱마다 만든 Unity 장면, 리소스, 엔진은 Unity에서 Gradle 프로젝트로 export하여 별도로 연결합니다. 이 패키지는 Unity 엔진이나 화면을 포함하지 않습니다.

## 1. Unity에 패키지 설치

Unity Package Manager에서 **Install package from disk**(버전에 따라 **Add package from disk**)를 선택하고 이 저장소의 `unity/package.json`을 지정합니다. 로컬 설치는 저장소의 파일을 직접 참조하므로 개발 중 변경 사항이 Unity에 반영됩니다.

Git으로 설치할 때는 패키지가 포함된 커밋을 원격 저장소에 먼저 푸시하고 **Install package from git URL**(또는 **Add package from git URL**)에 다음 주소를 넣습니다.

```text
https://github.com/achieveonepark/uni-and-bridge.git?path=/unity
```

특정 버전을 고정하려면 주소 끝에 `#<tag 또는 commit>`을 붙입니다. 비공개 저장소라면 해당 저장소에 접근할 수 있는 Git 인증이 필요합니다.

샘플을 Import하고, Unity 장면의 오브젝트에 `BridgeExample` 컴포넌트를 추가하세요. 필요하면 기존 버튼이나 게임 완료 이벤트에 `FinishExample()`을 연결합니다. 수신 브리지 오브젝트는 패키지가 자동 생성하므로 직접 추가하지 않습니다.

```csharp
using System;
using Achieve.UnityBridge;
using UnityEngine;

[Serializable] public sealed class UserData { public string userName; public int level; }
[Serializable] public sealed class GameResult { public int score; }

// 컴포넌트의 OnEnable에서 등록하고 OnDisable에서 해제합니다.
void OnInitialData(string json)
{
    UserData user = JsonUtility.FromJson<UserData>(json);
    // user 값을 장면, 캐릭터, UI 등에 반영합니다.
    UnityBridge.Send("sceneLoaded");
}

// 게임이 끝났을 때 호출합니다.
void FinishGame() => UnityBridge.Complete(new GameResult { score = 100 });
```

`UnityBridge.InitialDataReceived`와 `UnityBridge.MessageReceived`에 함수를 등록합니다. 늦게 생성된 컴포넌트는 `InitialDataJson` 또는 `ReadInitialData<T>()`로 이미 도착한 초기 데이터를 읽을 수 있습니다. Android와의 통신은 Unity 메인 스레드에서 호출하세요.

## 2. Android 앱에 라이브러리 설치

로컬 개발은 앱의 `settings.gradle.kts`에 라이브러리 모듈을 추가하고 `implementation(project(":android-unity-bridge"))`로 연결합니다.

```kotlin
// Android 앱의 settings.gradle.kts
include(":android-unity-bridge")
project(":android-unity-bridge").projectDir = file("<bridge 저장소>/android")

// 앱 모듈의 build.gradle.kts dependencies 블록
implementation(project(":android-unity-bridge"))
```

앱의 Gradle 설정에는 `com.android.library`와 `org.jetbrains.kotlin.android` 플러그인도 선언되어 있어야 합니다. 로컬 모듈로 연결할 때는 이 모듈의 Gradle 플러그인과 앱의 플러그인이 같은 버전을 사용합니다.

Android 앱에서 Unity를 열고 결과를 받습니다. 이 API와 콜백은 Android 메인 스레드에서 실행됩니다.

```kotlin
UnityBridge.open(
    host = this,
    initialData = JSONObject().put("userName", "Somiri").put("level", 7),
    listener = object : UnityBridge.Listener {
        override fun onEvent(message: UnityMessage) {
            // UnityBridge.send("update", JSONObject().put("level", 8))
        }
        override fun onClosed(result: UnityResult) {
            if (!result.cancelled) println(result.payload)
        }
        override fun onError(error: Exception) {
            // 오류를 기록하거나 사용자에게 표시합니다.
        }
    },
)
```

`open()` 직후 `send()`를 호출해도 준비될 때까지 큐에 보관합니다. 준비 전 큐는 초기 데이터를 포함하여 최대 128개입니다. `UnityBridge.close()`나 Unity의 `Cancel()`로 취소할 수 있습니다. 한 프로세스에서 동시에 하나의 세션을 지원합니다.

## 3. Unity export 연결

Unity의 Android Build Settings에서 **Export Project**를 켜고 Gradle 프로젝트를 생성합니다. 생성된 `unityLibrary` 모듈과 그 안의 Android 플러그인 모듈을 Android 앱의 Gradle 설정에 포함하고, 앱에서 `implementation(project(":unityLibrary"))`로 의존합니다.

이 저장소의 샘플은 export 경로를 전달하면 해당 모듈들을 연결합니다.

```sh
./gradlew -PunityExport=/absolute/path/to/unity-export :sample:assembleDebug
```

샘플 설정은 export의 `gradle.properties`에 있는 `unityStreamingAssets`도 함께 읽습니다. 별도의 앱에 직접 연결할 때도 이 속성을 옮겨 주세요.

Unity export의 **Compile SDK도 36**으로 맞추세요. Unity 플러그인이 추가한 Maven 저장소나 export의 별도 Gradle 설정이 있다면 앱 설정에도 반영해야 합니다. 실제 Unity 장면과 Android 네이티브 라이브러리가 들어 있는 export가 있어야 APK를 만들 수 있습니다.

자신의 앱을 연결할 때는 샘플 manifest처럼 Unity가 생성한 `UnityPlayerActivity` 선언을 `tools:node="remove"`로 제거하여 앱의 launcher를 하나로 유지하세요. 브리지 Activity 선언은 Android 라이브러리의 manifest에서 제공됩니다.

## 빌드와 배포

SDK 경로는 커밋하지 않는 `local.properties`의 `sdk.dir` 또는 `ANDROID_HOME`으로 지정합니다.

```sh
./gradlew :android:testDebugUnitTest :android:assembleRelease :android:lintRelease
./gradlew :android:publishReleasePublicationToLocalBuildRepository
```

AAR은 `android/build/outputs/aar/`, 로컬 Maven 저장소는 `android/build/repository/`에 생성됩니다. Maven 좌표는 `com.achieve:android-unity-bridge:0.1.0`입니다. 외부 Maven 저장소에는 아직 게시하지 않았습니다. Unity 패키지는 `unity/` 폴더 자체를 UPM으로 배포합니다.

## 데이터 규칙과 수명

프로토콜은 `version`, `sessionId`, `kind`, `name`, `payloadJson`을 가진 JSON 메시지입니다. `payloadJson`은 JSON **오브젝트를 직렬화한 문자열**입니다. Android에서는 `JSONObject`, Unity에서는 `[Serializable]` 클래스/구조체와 `JsonUtility`를 사용합니다. Unity의 `JsonUtility`가 지원하지 않는 Dictionary나 최상위 배열은 지원하지 않습니다. 큰 파일과 이미지 자체보다 파일 경로, URL, 식별자를 전달하세요.

Unity는 별도의 앱 내부 task에서 실행되며 최근 앱 목록에는 별도로 표시하지 않습니다. 복귀하면 호스트 task를 앞에 가져오고 Unity task는 일시정지합니다. Unity의 장면 상태와 메모리는 유지되며 다음 실행에는 새 초기 데이터가 전달됩니다. 재실행 시 장면을 초기화하려면 초기 데이터를 받은 코드에서 처리하세요. `Application.Quit()`, `finish()`로 Unity를 파괴하거나 Unity를 unload하는 흐름은 이 버전에서 지원하지 않습니다.

세션과 콜백은 메모리에만 있습니다. 프로세스 재시작, 호스트 Activity 재생성 후 콜백 재등록, 영구 저장은 앱이 별도로 처리해야 합니다. standalone Unity 실행 대신 Android 호스트에서 브리지 Activity를 여는 흐름을 사용하세요.

## 참고

- [Unity as a Library](https://docs.unity3d.com/6000.0/Documentation/Manual/UnityasaLibrary-Android.html)
- [Java/Kotlin에서 C# 호출](https://docs.unity3d.com/6000.0/Documentation/Manual/android-plugins-csharp-code-from-java-kotlin.html)
- [Unity 패키지 구조](https://docs.unity3d.com/6000.0/Documentation/Manual/cus-layout.html)
- [로컬 Unity 패키지 설치](https://docs.unity3d.com/6000.0/Documentation/Manual/upm-ui-local.html)
- [Git Unity 패키지 설치](https://docs.unity3d.com/6000.0/Documentation/Manual/upm-git.html)
- [Android 라이브러리](https://developer.android.com/studio/projects/android-library)
