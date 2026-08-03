package app.aptelly.tv.catalog;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Resolves catalog package names to packages that are actually installed on the TV.
 *
 * Some television vendors ship a working service under a package variant rather than
 * the package used by Google Play. Catalog state and launch behavior must follow the
 * installed TV entry instead of offering a duplicate or incompatible installation.
 */
public final class InstalledAppResolver {
    private static final String YOUTUBE_KIDS_CATALOG_PACKAGE =
            "com.google.android.youtube.tvkids";
    private static final Map<String, List<String>> PACKAGE_ALIASES;

    static {
        Map<String, List<String>> aliases = new LinkedHashMap<>();
        aliases.put(
                "com.google.android.youtube.tv",
                Arrays.asList(
                        "com.google.android.youtube",
                        "com.google.android.youtube.googletv",
                        "com.amazon.firetv.youtube"
                )
        );
        aliases.put(
                YOUTUBE_KIDS_CATALOG_PACKAGE,
                Arrays.asList(
                        "com.amazon.firetv.youtube",
                        "com.google.android.youtube.tv",
                        "com.google.android.youtube",
                        "com.google.android.youtube.googletv"
                )
        );
        // Do not alias the generic phone/tablet Netflix package to the TV
        // catalog product.  com.netflix.mediaclient 7.84.1 is present on the
        // Xiaomi MFTR0 test set, but real playback fails with error -16.  A
        // package being installed is not evidence that it is a certified TV
        // runtime, so it must not turn the Netflix card into an "Open" card.
        aliases.put(
                "org.smarttube.stable",
                Arrays.asList(
                        "org.smarttube.beta",
                        "app.smarttube.fdroid",
                        "com.liskovsoft.smarttubetv.beta",
                        "com.teamsmart.videomanager.tv"
                )
        );
        aliases.put(
                "com.github.metacubex.clash.meta",
                Collections.singletonList("com.github.kr328.clash")
        );
        PACKAGE_ALIASES = Collections.unmodifiableMap(aliases);
    }

    private InstalledAppResolver() {
    }

    public static String installedPackage(Context context, String catalogPackage) {
        PackageManager manager = context.getPackageManager();
        for (String candidate : candidates(catalogPackage)) {
            try {
                ApplicationInfo info = manager.getApplicationInfo(candidate, 0);
                if ((info.flags & ApplicationInfo.FLAG_INSTALLED) != 0) {
                    return candidate;
                }
            } catch (PackageManager.NameNotFoundException ignored) {
                // Try the next known package variant.
            }
        }

        String resolved = matchingActivityPackage(manager, catalogPackage, true);
        if (resolved != null) {
            return resolved;
        }
        return matchingActivityPackage(manager, catalogPackage, false);
    }

    public static Intent launchIntent(Context context, String catalogPackage) {
        PackageManager manager = context.getPackageManager();
        List<String> ordered = candidates(catalogPackage);
        String installed = installedPackage(context, catalogPackage);
        if (installed != null && !ordered.contains(installed)) {
            ordered.add(0, installed);
        }

        for (String candidate : ordered) {
            Intent launch = manager.getLeanbackLaunchIntentForPackage(candidate);
            if (launch == null) {
                launch = manager.getLaunchIntentForPackage(candidate);
            }
            if (launch != null) {
                return launch;
            }
        }

        Intent leanback = launcherQuery(true);
        for (ResolveInfo info : manager.queryIntentActivities(leanback, 0)) {
            if (info.activityInfo != null
                    && matches(catalogPackage, info.activityInfo.packageName)) {
                Intent launch = new Intent(leanback);
                launch.setComponent(new ComponentName(
                        info.activityInfo.packageName,
                        info.activityInfo.name
                ));
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                return launch;
            }
        }
        return null;
    }

    public static boolean isAcceptedVariant(
            String catalogPackage,
            String actualPackage
    ) {
        return matches(catalogPackage, actualPackage);
    }

    private static String matchingActivityPackage(
            PackageManager manager,
            String catalogPackage,
            boolean television
    ) {
        for (ResolveInfo info : manager.queryIntentActivities(
                launcherQuery(television),
                0
        )) {
            if (info.activityInfo != null
                    && matches(catalogPackage, info.activityInfo.packageName)) {
                return info.activityInfo.packageName;
            }
        }
        return null;
    }

    private static Intent launcherQuery(boolean television) {
        Intent query = new Intent(Intent.ACTION_MAIN);
        query.addCategory(television
                ? Intent.CATEGORY_LEANBACK_LAUNCHER
                : Intent.CATEGORY_LAUNCHER);
        return query;
    }

    private static List<String> candidates(String catalogPackage) {
        List<String> result = new ArrayList<>();
        // Google retired the standalone TV Kids client. The logical Kids card
        // now opens the supported child-profile flow inside the regular TV
        // YouTube client. Never treat either retired standalone package as a
        // usable installed variant merely because it can show a splash screen.
        if (!YOUTUBE_KIDS_CATALOG_PACKAGE.equals(catalogPackage)) {
            result.add(catalogPackage);
        }
        List<String> aliases = PACKAGE_ALIASES.get(catalogPackage);
        if (aliases != null) {
            result.addAll(aliases);
        }
        return result;
    }

    private static boolean matches(String catalogPackage, String actualPackage) {
        if (candidates(catalogPackage).contains(actualPackage)) {
            return true;
        }
        return "com.google.android.youtube.tv".equals(catalogPackage)
                && actualPackage.startsWith("com.google.android.youtube");
    }
}
