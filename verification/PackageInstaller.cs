using System;
using UnityEditor;
using UnityEditor.PackageManager;
using UnityEditor.PackageManager.Requests;
using UnityEngine;

public static class AndroidUnityBridgePackageInstaller
{
    private static AddRequest request;
    private static double deadline;

    // Run without -quit: the asynchronous UPM request needs Editor update ticks.
    public static void Install()
    {
        var path = Environment.GetEnvironmentVariable("UNITY_BRIDGE_PACKAGE_PATH");
        if (string.IsNullOrEmpty(path)) throw new InvalidOperationException("Set UNITY_BRIDGE_PACKAGE_PATH to the unity/ directory.");
        request = Client.Add("file:" + path.Replace('\\', '/'));
        deadline = EditorApplication.timeSinceStartup + 180;
        EditorApplication.update += Poll;
    }

    private static void Poll()
    {
        if (!request.IsCompleted && EditorApplication.timeSinceStartup < deadline) return;
        EditorApplication.update -= Poll;
        if (request.IsCompleted && request.Status == StatusCode.Success)
        {
            Debug.Log("[UnityBridge] Installed " + request.Result.name);
            EditorApplication.Exit(0);
        }
        else
        {
            Debug.LogError(request.IsCompleted ? request.Error.message : "Local package installation timed out.");
            EditorApplication.Exit(1);
        }
    }
}
