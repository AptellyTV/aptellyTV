package app.aptelly.tv;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Build;

import java.util.List;

/** Retires the unsafe launcher shim; TV-only apps remain available inside Aptelly. */
public final class PrimeVideoShortcutController {
    public static final String PRIME_PACKAGE = "com.amazon.amazonvideo.livingroom";
    private static final String XIAOMI_HOME_PACKAGE = "com.mitv.tvhome";

    private PrimeVideoShortcutController() {
    }

    public static void sync(Context context) {
        Context application = context.getApplicationContext();
        PackageManager manager = application.getPackageManager();
        ComponentName shortcut = new ComponentName(
                application,
                PrimeVideoShortcutActivity.class
        );
        int desired = PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
        if (manager.getComponentEnabledSetting(shortcut) != desired) {
            manager.setComponentEnabledSetting(
                    shortcut,
                    desired,
                    PackageManager.DONT_KILL_APP
            );
        }
    }

    private static boolean isInstalled(PackageManager manager) {
        try {
            ApplicationInfo info = manager.getApplicationInfo(PRIME_PACKAGE, 0);
            return (info.flags & ApplicationInfo.FLAG_INSTALLED) != 0;
        } catch (PackageManager.NameNotFoundException ignored) {
            return false;
        }
    }

    private static boolean hasLauncherEntry(PackageManager manager, String category) {
        Intent query = new Intent(Intent.ACTION_MAIN);
        query.addCategory(category);
        query.setPackage(PRIME_PACKAGE);
        List<ResolveInfo> activities = manager.queryIntentActivities(query, 0);
        return activities != null && !activities.isEmpty();
    }

    private static boolean usesAffectedXiaomiLauncher(PackageManager manager) {
        if (!"xiaomi".equalsIgnoreCase(Build.MANUFACTURER)
                || !"mitv-mftr0".equalsIgnoreCase(Build.MODEL)) {
            return false;
        }
        Intent home = new Intent(Intent.ACTION_MAIN);
        home.addCategory(Intent.CATEGORY_HOME);
        ResolveInfo resolved = manager.resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY);
        return resolved != null
                && resolved.activityInfo != null
                && XIAOMI_HOME_PACKAGE.equals(resolved.activityInfo.packageName);
    }
}
