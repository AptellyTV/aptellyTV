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

    public static boolean open(Activity activity, CatalogApp app) {
        if (InstalledAppResolver.installedPackage(activity, app.packageName) != null) {
            return false;
        }

        if (hasEnabledPackage(activity, "com.amazon.venezia")) {
            String amazonPackage = isYouTubeProduct(app.packageName)
                    ? "com.amazon.firetv.youtube"
                    : app.packageName;
            if (start(activity, new Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("amzn://apps/android?p=" + amazonPackage)
            ).setPackage("com.amazon.venezia"))) {
                return true;
            }
        }

        String storePackage = "com.google.android.youtube.tvkids".equals(app.packageName)
                ? "com.google.android.youtube.tv"
                : app.packageName;
        if (hasEnabledPackage(activity, "com.aurora.store")
                && start(activity, marketIntent(storePackage, "com.aurora.store"))) {
            return true;
        }

        if (hasEnabledPackage(activity, "com.android.vending")
                && start(activity, marketIntent(storePackage, "com.android.vending"))) {
            return true;
        }

        if (hasEnabledPackage(activity, "com.xiaomi.mitv.appstore")) {
            Intent xiaomi = new Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse(
                            "market://appstore.mitv.xiaomi.com/details?package="
                                    + Uri.encode(storePackage)
                    )
            ).setPackage("com.xiaomi.mitv.appstore");
            if (start(activity, xiaomi)) {
                return true;
            }
        }

        String dangbeiId = DANGBEI_APP_IDS.get(app.packageName);
        if (dangbeiId != null
                && hasEnabledPackage(activity, "com.overseas.store.appstore")) {
            Intent dangbei = new Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("dbstore://appstoredetail?appid=" + dangbeiId)
            ).setPackage("com.overseas.store.appstore");
            return start(activity, dangbei);
        }
        return false;
    }

    private static boolean isYouTubeProduct(String packageName) {
        return "com.google.android.youtube.tv".equals(packageName)
                || "com.google.android.youtube.tvkids".equals(packageName);
    }

    private static Intent marketIntent(String packageName, String storePackage) {
        return new Intent(
                Intent.ACTION_VIEW,
                Uri.parse("market://details?id=" + Uri.encode(packageName))
        ).setPackage(storePackage);
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
