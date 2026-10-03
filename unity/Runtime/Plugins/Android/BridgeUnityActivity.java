package com.achieve.unitybridge;

import android.annotation.TargetApi;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.window.OnBackInvokedCallback;
import android.window.OnBackInvokedDispatcher;
import com.unity3d.player.UnityPlayer;
import com.unity3d.player.UnityPlayerActivity;

/** Compiled inside the Unity export, where the Unity player classes already exist. */
public final class BridgeUnityActivity extends UnityPlayerActivity {
    private Object backCallback;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (Build.VERSION.SDK_INT >= 33) {
            backCallback = BackApi33.register(this, this::returnToHost);
        }
    }

    @Override protected void onResume() {
        super.onResume();
        UnityPlayer.UnitySendMessage("__AndroidUnityBridge", "OnNativeConnect", "");
    }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
            if (event.getAction() == KeyEvent.ACTION_UP) returnToHost();
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    @Override public void onBackPressed() { returnToHost(); }

    @Override protected void onDestroy() {
        if (Build.VERSION.SDK_INT >= 33 && backCallback != null) {
            BackApi33.unregister(this, backCallback);
        }
        super.onDestroy();
    }

    private void returnToHost() {
        try {
            Class.forName("com.achieve.unitybridge.UnityBridge")
                .getMethod("cancelFromActivity").invoke(null);
        } catch (ReflectiveOperationException error) {
            Log.e("UnityBridge", "Install the companion Android library before launching this Activity.", error);
        }
    }

    // Keep API 33 types out of the Activity's fields and method signatures on older Android.
    @TargetApi(33)
    private static final class BackApi33 {
        static Object register(BridgeUnityActivity activity, Runnable onBack) {
            OnBackInvokedCallback callback = onBack::run;
            activity.getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_DEFAULT, callback);
            return callback;
        }

        static void unregister(BridgeUnityActivity activity, Object token) {
            activity.getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback((OnBackInvokedCallback) token);
        }
    }
}
