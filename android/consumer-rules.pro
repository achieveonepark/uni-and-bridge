# Unity calls these Java names through JNI, and the bridge calls Unity reflectively.
-keep class com.achieve.unitybridge.UnityBridge { public *; }
-keep class com.achieve.unitybridge.BridgeUnityActivity { *; }
-keepclassmembers class com.unity3d.player.UnityPlayer {
    public static void UnitySendMessage(java.lang.String, java.lang.String, java.lang.String);
}
