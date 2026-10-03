# Android Unity Bridge — Unity package

Install `package.json` with **Package Manager → Add package from disk**.

Requires Unity 6 with the Android **Application Entry Point set to Activity**, and the companion Android library from `../android/`. Export the Unity project as an Android Gradle project and integrate its `unityLibrary` into the host app.

The receiver is installed automatically and survives scene changes. Subscribe to `UnityBridge.InitialDataReceived` and `UnityBridge.MessageReceived`; unsubscribe when your component is disabled. Read cached initial data with `UnityBridge.ReadInitialData<T>()`. Call `UnityBridge.Send(name, serializablePayload)`, `UnityBridge.Complete(serializableResult)` or `UnityBridge.Cancel()` from the Unity main thread.

Import the **Bridge Example** sample for a component you can attach to a scene object. It logs initial data, sends a `sceneLoaded` event, and exposes `FinishExample()` for an existing button or game completion event.

Payloads are JSON objects using `JsonUtility`; use `[Serializable]` classes/structs, not dictionaries or top-level arrays. Returning to Android pauses and retains Unity. Reopening delivers fresh initial data to the same scene; reset game state in your initial-data handler if needed.

See the repository README for Android integration, local builds, and current limitations.
