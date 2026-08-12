package app.aptelly.tv.install;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.Uri;

import app.aptelly.tv.catalog.CatalogApp;
import app.aptelly.tv.catalog.InstalledAppResolver;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Opens a package-specific managed-store page only after the verified direct
 * matcher has no compatible APK. Store pages remain store-managed installs;
 * they are never reported as Aptelly-verified artifacts.
 */
public final class StoreInstallRouter {
    private static final Map<String, String> DANGBEI_APP_IDS;

    static {
        Map<String, String> ids = new HashMap<>();
        ids.put("com.disney.disneyplus", "4085");
        ids.put("com.hulu.livingroomplus", "3953");
        ids.put("com.spotify.tv.android", "1876");
        ids.put("tv.twitch.android.app", "4100");
        DANGBEI_APP_IDS = Collections.unmodifiableMap(ids);
    }

    private StoreInstallRouter() {
    }

    /** Returns whether this television has a package-specific managed-store route. */
    public static boolean canOpen(Activity activity, CatalogApp app) {
        if (InstalledAppResolver.installedPackage(activity, app.packageName) != null) {
            return false;
        }
        if (hasEnabledPackage(activity, "com.android.vending")
                && resolves(activity, marketIntent(app.packageName, "com.android.vending"))) {
            return true;
        }
        if (hasEnabledPackage(activity, "com.amazon.venezia")) {
            String amazonPackage = isYouTubeProduct(app.packageName)
                    ? "com.amazon.firetv.youtube"
                    : app.packageName;
            if (resolves(activity, amazonIntent(amazonPackage))) {
                return true;
            }
        }
        if (!isYouTubeProduct(app.packageName)
                && hasEnabledPackage(activity, "com.aurora.store")
                && resolves(activity, marketIntent(app.packageName, "com.aurora.store"))) {
            return true;
        }
        if (hasEnabledPackage(activity, "com.xiaomi.mitv.appstore")
                && resolves(activity, xiaomiIntent(app.packageName))) {
            return true;
        }
        String dangbeiId = DANGBEI_APP_IDS.get(app.packageName);
        return dangbeiId != null
                && hasEnabledPackage(activity, "com.overseas.store.appstore")
                && resolves(activity, dangbeiIntent(dangbeiId));
    }

    public static boolean open(Activity activity, CatalogApp app) {
        if (InstalledAppResolver.installedPackage(activity, app.packageName) != null) {
            return false;
        }

        // Publisher-operated Google Play is the first managed source.
        if (hasEnabledPackage(activity, "com.android.vending")
                && start(activity, marketIntent(app.packageName, "com.android.vending"))) {
            return true;
        }

        // The Amazon/Fire TV package is the second YouTube source. Never send
        // a non-GMS television to Aurora for the Google TV package when the
        // qualified Fire TV variant is the intended fallback.
        if (hasEnabledPackage(activity, "com.amazon.venezia")) {
            String amazonPackage = isYouTubeProduct(app.packageName)
                    ? "com.amazon.firetv.youtube"
                    : app.packageName;
            if (start(activity, amazonIntent(amazonPackage))) {
                return true;
            }
        }

        if (isYouTubeProduct(app.packageName)) {
            return false;
        }

        String storePackage = app.packageName;
        if (hasEnabledPackage(activity, "com.aurora.store")
                && start(activity, marketIntent(storePackage, "com.aurora.store"))) {
            return true;
        }

        if (hasEnabledPackage(activity, "com.xiaomi.mitv.appstore")) {
            if (start(activity, xiaomiIntent(storePackage))) {
                return true;
            }
        }

        String dangbeiId = DANGBEI_APP_IDS.get(app.packageName);
        if (dangbeiId != null
                && hasEnabledPackage(activity, "com.overseas.store.appstore")) {
            return start(activity, dangbeiIntent(dangbeiId));
        }
        return false;
    }

    private static boolean isYouTubeProduct(String packageName) {
        return "com.google.android.youtube.tv".equals(packageName);
    }

    private static Intent marketIntent(String packageName, String storePackage) {
        return new Intent(
                Intent.ACTION_VIEW,
                Uri.parse("market://details?id=" + Uri.encode(packageName))
        ).setPackage(storePackage);
    }

    private static Intent amazonIntent(String packageName) {
        return new Intent(
                Intent.ACTION_VIEW,
                Uri.parse("amzn://apps/android?p=" + Uri.encode(packageName))
        ).setPackage("com.amazon.venezia");
    }

    private static Intent xiaomiIntent(String packageName) {
        return new Intent(
                Intent.ACTION_VIEW,
                Uri.parse(
                        "market://appstore.mitv.xiaomi.com/details?package="
                                + Uri.encode(packageName)
                )
        ).setPackage("com.xiaomi.mitv.appstore");
    }

    private static Intent dangbeiIntent(String appId) {
        return new Intent(
                Intent.ACTION_VIEW,
                Uri.parse("dbstore://appstoredetail?appid=" + Uri.encode(appId))
        ).setPackage("com.overseas.store.appstore");
    }

    private static boolean resolves(Activity activity, Intent intent) {
        return intent.resolveActivity(activity.getPackageManager()) != null;
    }

    private static boolean start(Activity activity, Intent intent) {
        PackageManager manager = activity.getPackageManager();
        if (intent.resolveActivity(manager) == null) {
            return false;
        }
        try {
            activity.startActivity(intent);
            return true;
        } catch (ActivityNotFoundException | SecurityException ignored) {
            return false;
        }
    }

    private static boolean hasEnabledPackage(Activity activity, String packageName) {
        try {
            ApplicationInfo info = activity.getPackageManager()
                    .getApplicationInfo(packageName, 0);
            return info.enabled;
        } catch (PackageManager.NameNotFoundException ignored) {
            return false;
        }
    }
}
