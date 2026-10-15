package com.local.a89dialfromhome;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.IBinder;
import android.view.KeyEvent;

import java.util.List;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Feature-phone behaviour of the stock launcher: pressing 0-9, * or # on the home screen
 * opens the dialer with that character pre-filled. Third-party launchers (Nova) would
 * otherwise open their own search box, so do it at the policy level for any home app.
 */
public class Hook implements IXposedHookLoadPackage {
    private static final String PWM = "com.android.server.policy.PhoneWindowManager";

    private static volatile long dialStartedAt = -1;

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (!"android".equals(lpparam.packageName)) return;
        try {
            XposedHelpers.findAndHookMethod(PWM, lpparam.classLoader, "interceptKeyBeforeDispatching",
                    IBinder.class, KeyEvent.class, int.class, new DialFromHome());
            XposedBridge.log("A89DialFromHome: hooked interceptKeyBeforeDispatching");
        } catch (Throwable t) {
            XposedBridge.log("A89DialFromHome: hook failed: " + t);
        }
    }

    // Named class instead of an anonymous one: d8 34 crashes on anonymous subclasses here.
    private static final class DialFromHome extends XC_MethodHook {
        @Override
        protected void beforeHookedMethod(MethodHookParam param) {
            try {
                KeyEvent event = (KeyEvent) param.args[1];
                String text = dialText(event.getKeyCode());
                if (text == null) return;
                if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
                    if (!isHomeOnTop(param.thisObject)) return;
                    Context ctx = (Context) XposedHelpers.getObjectField(param.thisObject, "mContext");
                    Intent intent = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(text)));
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    ctx.startActivity(intent);
                    dialStartedAt = event.getDownTime();
                    param.setResult(Long.valueOf(-1L));
                } else if (event.getDownTime() == dialStartedAt) {
                    // Swallow repeats and the matching key-up of the press that opened the dialer.
                    param.setResult(Long.valueOf(-1L));
                }
            } catch (Throwable t) {
                XposedBridge.log("A89DialFromHome: failed: " + t);
            }
        }

        private static String dialText(int keyCode) {
            if (keyCode >= KeyEvent.KEYCODE_0 && keyCode <= KeyEvent.KEYCODE_9) {
                return String.valueOf(keyCode - KeyEvent.KEYCODE_0);
            }
            if (keyCode == KeyEvent.KEYCODE_STAR) return "*";
            if (keyCode == KeyEvent.KEYCODE_POUND) return "#";
            return null;
        }

        private static boolean isHomeOnTop(Object pwm) {
            Context ctx = (Context) XposedHelpers.getObjectField(pwm, "mContext");
            if (Boolean.TRUE.equals(XposedHelpers.callMethod(pwm, "isKeyguardShowing"))) return false;
            ActivityManager am = (ActivityManager) ctx.getSystemService(Context.ACTIVITY_SERVICE);
            List<ActivityManager.RunningTaskInfo> tasks = am.getRunningTasks(1);
            if (tasks == null || tasks.isEmpty() || tasks.get(0).topActivity == null) return false;
            Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
            ResolveInfo ri = ctx.getPackageManager().resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY);
            return ri != null && ri.activityInfo != null
                    && tasks.get(0).topActivity.getPackageName().equals(ri.activityInfo.packageName);
        }
    }
}
