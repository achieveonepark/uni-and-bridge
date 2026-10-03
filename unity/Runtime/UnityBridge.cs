using System;
using UnityEngine;
using UnityEngine.Scripting;

namespace Achieve.UnityBridge
{
    [Serializable]
    internal sealed class Envelope
    {
        public int version = 1;
        public string sessionId;
        public string kind;
        public string name;
        public string payloadJson;
    }

    public sealed class BridgeMessage
    {
        public string Name { get; }
        public string PayloadJson { get; }
        internal BridgeMessage(string name, string payloadJson)
        {
            Name = name;
            PayloadJson = payloadJson;
        }
        public T Read<T>() => JsonUtility.FromJson<T>(PayloadJson);
    }

    public static class UnityBridge
    {
        private const string NativeClass = "com.achieve.unitybridge.UnityBridge";
        public static string SessionId { get; private set; }
        public static string InitialDataJson { get; private set; }
        public static bool IsOpen => !string.IsNullOrEmpty(SessionId);
        public static event Action<string> InitialDataReceived;
        public static event Action<BridgeMessage> MessageReceived;

        [RuntimeInitializeOnLoadMethod(RuntimeInitializeLoadType.SubsystemRegistration)]
        private static void Reset()
        {
            SessionId = null;
            InitialDataJson = null;
            InitialDataReceived = null;
            MessageReceived = null;
        }

        [RuntimeInitializeOnLoadMethod(RuntimeInitializeLoadType.BeforeSceneLoad)]
        private static void Install()
        {
#if UNITY_ANDROID && !UNITY_EDITOR
            var receiver = new GameObject("__AndroidUnityBridge");
            UnityEngine.Object.DontDestroyOnLoad(receiver);
            receiver.AddComponent<UnityBridgeReceiver>();
#endif
        }

        public static T ReadInitialData<T>()
        {
            if (InitialDataJson == null) throw new InvalidOperationException("Initial data has not arrived yet.");
            return JsonUtility.FromJson<T>(InitialDataJson);
        }

        public static void Send(string name, object payload = null)
        {
            if (string.IsNullOrWhiteSpace(name)) throw new ArgumentException("Event name must not be blank.", nameof(name));
            SendEnvelope("event", name, payload);
        }

        /// <summary>Returns a result and brings Android forward. Unity remains loaded for reuse.</summary>
        public static void Complete(object result = null)
        {
            SendEnvelope("complete", "", result);
            SessionId = null;
            InitialDataJson = null;
        }

        public static void Cancel()
        {
            if (!IsOpen) return;
#if UNITY_ANDROID && !UNITY_EDITOR
            using (var native = new AndroidJavaClass(NativeClass)) native.CallStatic("cancelFromUnity", SessionId);
#endif
            SessionId = null;
            InitialDataJson = null;
        }

        private static void SendEnvelope(string kind, string name, object payload)
        {
            if (!IsOpen) throw new InvalidOperationException("No Android bridge session is open.");
            var envelope = new Envelope
            {
                sessionId = SessionId, kind = kind, name = name,
                payloadJson = payload == null ? "{}" : JsonUtility.ToJson(payload)
            };
#if UNITY_ANDROID && !UNITY_EDITOR
            using (var native = new AndroidJavaClass(NativeClass))
                native.CallStatic("receiveFromUnity", JsonUtility.ToJson(envelope));
#endif
        }

        internal static void NotifyReady()
        {
#if UNITY_ANDROID && !UNITY_EDITOR
            using (var native = new AndroidJavaClass(NativeClass))
            {
                var id = native.CallStatic<string>("getSessionId");
                if (string.IsNullOrEmpty(id))
                {
                    SessionId = null;
                    InitialDataJson = null;
                    return;
                }
                Connect(id);
                native.CallStatic("markReady", id);
            }
#endif
        }

        internal static void Connect(string id)
        {
            if (SessionId == id) return;
            SessionId = id;
            InitialDataJson = null;
        }

        internal static void Disconnect(string id)
        {
            if (SessionId != id) return;
            SessionId = null;
            InitialDataJson = null;
        }

        internal static void Receive(string json)
        {
            Envelope envelope;
            try { envelope = JsonUtility.FromJson<Envelope>(json); }
            catch (ArgumentException error) { Debug.LogWarning($"[UnityBridge] Invalid message: {error.Message}"); return; }
            if (envelope == null || envelope.version != 1 || envelope.sessionId != SessionId || !IsOpen) return;
            if (string.IsNullOrEmpty(envelope.payloadJson)) return;
            switch (envelope.kind)
            {
                case "init":
                    InitialDataJson = envelope.payloadJson;
                    InitialDataReceived?.Invoke(InitialDataJson);
                    break;
                case "message":
                    MessageReceived?.Invoke(new BridgeMessage(envelope.name, envelope.payloadJson));
                    break;
            }
        }
    }
}
