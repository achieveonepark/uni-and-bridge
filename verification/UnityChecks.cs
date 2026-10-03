using System;
using System.Reflection;
using UnityEditor;
using UnityEngine;

public static class AndroidUnityBridgeChecks
{
    [Serializable] private sealed class Payload { public string userName; public int level; }
    [Serializable] private sealed class Wire
    {
        public int version;
        public string sessionId;
        public string kind;
        public string name;
        public string payloadJson;
    }

    public static void Run()
    {
        var bridge = Type.GetType("Achieve.UnityBridge.UnityBridge, Achieve.UnityBridge", true);
        Call(bridge, "Reset");
        Call(bridge, "Connect", "first");
        var payload = JsonUtility.ToJson(new Payload { userName = "소미리", level = 7 });
        var initial = new Wire { version = 1, sessionId = "first", kind = "init", name = "", payloadJson = payload };
        Call(bridge, "Receive", JsonUtility.ToJson(initial));
        Check((string)bridge.GetProperty("InitialDataJson").GetValue(null) == payload, "initial data is cached");
        var read = bridge.GetMethod("ReadInitialData").MakeGenericMethod(typeof(Payload)).Invoke(null, null) as Payload;
        Check(read != null && read.userName == "소미리" && read.level == 7, "JSON survives the wire round trip");

        // Reconnecting to the same session must not erase its cached initial data.
        Call(bridge, "Connect", "first");
        Check((string)bridge.GetProperty("InitialDataJson").GetValue(null) == payload, "duplicate readiness preserves data");
        Call(bridge, "Connect", "second");
        Check(bridge.GetProperty("InitialDataJson").GetValue(null) == null, "fresh session clears old data");
        Call(bridge, "Receive", JsonUtility.ToJson(initial));
        Check(bridge.GetProperty("InitialDataJson").GetValue(null) == null, "stale session data is ignored");
        initial.sessionId = "second";
        initial.version = 99;
        Call(bridge, "Receive", JsonUtility.ToJson(initial));
        Check(bridge.GetProperty("InitialDataJson").GetValue(null) == null, "unknown protocol version is ignored");
        initial.version = 1;
        Call(bridge, "Receive", JsonUtility.ToJson(initial));
        Call(bridge, "Disconnect", "first");
        Check((bool)bridge.GetProperty("IsOpen").GetValue(null), "late closure cannot cancel a new session");
        Call(bridge, "Disconnect", "second");
        Check(!(bool)bridge.GetProperty("IsOpen").GetValue(null), "closure clears current session");

        var importer = AssetImporter.GetAtPath("Packages/com.achieve.unitybridge/Runtime/Plugins/Android/BridgeUnityActivity.java") as PluginImporter;
        Check(importer != null && importer.GetCompatibleWithPlatform(BuildTarget.Android), "Java Activity is an Android plug-in");
        Check(importer != null && !importer.GetCompatibleWithAnyPlatform(), "Java Activity is limited to Android");
        Call(bridge, "Reset");
        Debug.Log("[UnityBridge] Unity checks passed (10 checks).");
    }

    private static void Call(Type type, string name, params object[] args)
        => type.GetMethod(name, BindingFlags.Static | BindingFlags.NonPublic).Invoke(null, args);

    private static void Check(bool condition, string description)
    {
        if (!condition) throw new InvalidOperationException("Unity bridge check failed: " + description);
    }
}
