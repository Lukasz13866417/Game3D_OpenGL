package com.example.game3d_opengl.game.util;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

public final class GameVersion {
    private static volatile String displayString = "unknown";

    private GameVersion() {}

    public static void initialize(Context context) {
        if (context == null) {
            return;
        }
        Context appContext = context.getApplicationContext();
        Context resolvedContext = appContext != null ? appContext : context;
        String packageName = resolvedContext.getPackageName();
        try {
            PackageManager packageManager = resolvedContext.getPackageManager();
            PackageInfo packageInfo = packageManager.getPackageInfo(packageName, 0);
            String versionName = packageInfo.versionName != null ? packageInfo.versionName : "unknown";
            long versionCode = packageInfo.getLongVersionCode();
            displayString = "v" + versionName + " (" + versionCode + ")";
        } catch (Exception ignored) {
            displayString = packageName;
        }
    }

    public static String displayString() {
        return displayString;
    }
}
