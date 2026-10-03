using System;
using Achieve.UnityBridge;
using UnityEngine;

public sealed class BridgeExample : MonoBehaviour
{
    [Serializable] public sealed class UserData { public string userName; public int level; }
    [Serializable] public sealed class Result { public bool completed; public int score; }

    private void OnEnable()
    {
        UnityBridge.InitialDataReceived += OnInitialData;
        UnityBridge.MessageReceived += OnMessage;
        if (UnityBridge.InitialDataJson != null) OnInitialData(UnityBridge.InitialDataJson);
    }

    private void OnDisable()
    {
        UnityBridge.InitialDataReceived -= OnInitialData;
        UnityBridge.MessageReceived -= OnMessage;
    }

    private void OnInitialData(string json)
    {
        var user = JsonUtility.FromJson<UserData>(json);
        Debug.Log($"[BridgeExample] Hello {user.userName}, level {user.level}");
        UnityBridge.Send("sceneLoaded", new Result { completed = false, score = 0 });
    }

    private void OnMessage(BridgeMessage message)
    {
        Debug.Log($"[BridgeExample] {message.Name}: {message.PayloadJson}");
    }

    // Connect this public method to your scene's existing button or game completion event.
    public void FinishExample() => UnityBridge.Complete(new Result { completed = true, score = 100 });
}
