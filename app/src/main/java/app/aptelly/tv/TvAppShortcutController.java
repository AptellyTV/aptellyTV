package app.aptelly.tv;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Build;

import java.util.Arrays;
import java.util.List;

/** Retires shared launcher shims; installed TV-only apps stay visible inside Aptelly. */
public final class TvAppShortcutController {
    private static final String XIAOMI_HOME_PACKAGE = "com.mitv.tvhome";

    private static final List<Entry> ENTRIES = Arrays.asList(
            entry(TvAppShortcutActivity.Plex.class, "com.plexapp.android"),
            entry(TvAppShortcutActivity.Fawesome.class,
                    "com.future.moviesByFawesomeAndroidTV"),
            entry(TvAppShortcutActivity.Crunchyroll.class,
                    "com.crunchyroll.crunchyroid"),
            entry(TvAppShortcutActivity.Espn.class, "com.espn.score_center"),
            entry(TvAppShortcutActivity.F1.class, "com.formulaone.production"),
            entry(TvAppShortcutActivity.BritBox.class, "com.britbox.tv"),
            entry(TvAppShortcutActivity.Globoplay.class, "com.globo.globotv"),
            entry(TvAppShortcutActivity.JioHotstar.class, "in.startv.hotstar"),
            entry(TvAppShortcutActivity.DiscoveryPlus.class,
                    "com.discoveryplus.tv.android",
                    "com.discovery.discoveryplus.mobile",
                    "com.discovery.discoveryplus.firetv")
    );

    private TvAppShortcutController() {
    }

    public static void sync(Context context) {
        Context application = context.getApplicationContext();
        PackageManager manager = application.getPackageManager();
        for (Entry entry : ENTRIES) {
            ComponentName shortcut = new ComponentName(application, entry.activity);
            int desired = PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
            if (manager.getComponentEnabledSetting(shortcut) != desired) {
                manager.setComponentEnabledSetting(
                        shortcut,
                        desired,
                        PackageManager.DONT_KILL_APP
                );
            }
        }
    }

    static String installedPackage(Context context, String... packages) {
        return installedPackage(context.getPackageManager(), packages);
    }

    private static String installedPackage(PackageManager manager, String[] packages) {
        for (String packageName : packages) {
            try {
                ApplicationInfo info = manager.getApplicationInfo(packageName, 0);
                if ((info.flags & ApplicationInfo.FLAG_INSTALLED) != 0) {
                    return packageName;
                }
            } catch (PackageManager.NameNotFoundException ignored) {
                // Try the next known official package identity.
            }
        }
        return null;
    }

    private static boolean hasLauncherEntry(
            PackageManager manager,
            String packageName,
            String category
    ) {
        Intent query = new Intent(Intent.ACTION_MAIN);
        query.addCategory(category);
        query.setPackage(packageName);
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

    private static Entry entry(
            Class<? extends TvAppShortcutActivity> activity,
            String... packages
    ) {
        return new Entry(activity, packages);
    }

    private static final class Entry {
        final Class<? extends TvAppShortcutActivity> activity;
        final String[] packages;

        Entry(Class<? extends TvAppShortcutActivity> activity, String[] packages) {
            this.activity = activity;
            this.packages = packages;
        }
    }
}
