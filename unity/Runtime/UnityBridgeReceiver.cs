using UnityEngine;
using UnityEngine.Scripting;

namespace Achieve.UnityBridge
{
    [Preserve]
    internal sealed class UnityBridgeReceiver : MonoBehaviour
    {
        private bool started;
        private void Start() { started = true; UnityBridge.NotifyReady(); }
        private void OnApplicationFocus(bool focused)
        {
            if (focused && started) UnityBridge.NotifyReady();
        }

        [Preserve]
        public void OnNativeMessage(string json) => UnityBridge.Receive(json);

        // On resume this also reconnects a retained Unity runtime to a fresh Android session.
        [Preserve]
        public void OnNativeConnect(string unused) { if (started) UnityBridge.NotifyReady(); }

        [Preserve]
        public void OnNativeClosed(string id) => UnityBridge.Disconnect(id);
    }
}
