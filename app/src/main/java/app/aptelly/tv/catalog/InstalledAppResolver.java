package app.aptelly.tv.catalog;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Build;

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
        // Netflix package variants are governed by ProductVariantPolicy. The
        // old 7.84.1 runtime remains rejected, while 9.78.0 build 7 is the
        // current login-qualified compatibility floor. Playback/DRM evidence
        // remains a separate server qualification and is never inferred from
        // package presence alone.
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
        Resolution resolution = resolve(context, catalogPackage);
        return resolution.status == ProductVariantPolicy.Status.READY
                ? resolution.packageName
                : null;
    }

    /** Returns any recognized installed variant, including one requiring repair. */
    public static Resolution resolve(Context context, String catalogPackage) {
        PackageManager manager = context.getPackageManager();
        Resolution repair = Resolution.absent();
        for (String candidate : candidates(catalogPackage)) {
            try {
                ApplicationInfo info = manager.getApplicationInfo(candidate, 0);
                if ((info.flags & ApplicationInfo.FLAG_INSTALLED) != 0) {
                    long version = versionCode(manager, candidate);
                    ProductVariantPolicy.Status status = ProductVariantPolicy.assess(
                            catalogPackage,
                            candidate,
                            version,
                            isGoogleReady(manager),
                            isFireTv(manager)
                    );
                    Resolution found = new Resolution(candidate, version, status);
                    if (status == ProductVariantPolicy.Status.READY) return found;
                    if (repair.packageName == null) repair = found;
                }
            } catch (PackageManager.NameNotFoundException ignored) {
                // Try the next known package variant.
            }
        }

        String resolved = matchingActivityPackage(manager, catalogPackage, true);
        if (resolved != null) {
            Resolution found = assessInstalled(manager, catalogPackage, resolved);
            if (found.status == ProductVariantPolicy.Status.READY) return found;
            if (repair.packageName == null) repair = found;
        }
        resolved = matchingActivityPackage(manager, catalogPackage, false);
        if (resolved != null) {
            Resolution found = assessInstalled(manager, catalogPackage, resolved);
            if (found.status == ProductVariantPolicy.Status.READY) return found;
            if (repair.packageName == null) repair = found;
        }
        return repair;
    }

    public static Intent launchIntent(Context context, String catalogPackage) {
        PackageManager manager = context.getPackageManager();
        String installed = installedPackage(context, catalogPackage);
        if (installed == null) return null;

        Intent launch = manager.getLeanbackLaunchIntentForPackage(installed);
        if (launch == null) launch = manager.getLaunchIntentForPackage(installed);
        if (launch != null) return launch;

        Intent leanback = launcherQuery(true);
        for (ResolveInfo info : manager.queryIntentActivities(leanback, 0)) {
            if (info.activityInfo != null
                    && installed.equals(info.activityInfo.packageName)) {
                Intent resolvedLaunch = new Intent(leanback);
                resolvedLaunch.setComponent(new ComponentName(
                        info.activityInfo.packageName,
                        info.activityInfo.name
                ));
                resolvedLaunch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                return resolvedLaunch;
            }
        }
        return null;
    }

    public static boolean isAcceptedVariant(
            String catalogPackage,
            String actualPackage
    ) {
        if (ProductVariantPolicy.isKnownVariant(catalogPackage, actualPackage)) {
            return ProductVariantPolicy.isInstallableVariant(
                    catalogPackage,
                    actualPackage
            );
        }
        return matches(catalogPackage, actualPackage);
    }

    public static boolean isKnownProductVariant(
            String catalogPackage,
            String actualPackage
    ) {
        return ProductVariantPolicy.isKnownVariant(catalogPackage, actualPackage)
                || matches(catalogPackage, actualPackage);
    }

    public static ProductVariantPolicy.Status artifactStatus(
            Context context,
            String catalogPackage,
            String actualPackage,
            long versionCode
    ) {
        PackageManager manager = context.getPackageManager();
        ProductVariantPolicy.Status status = ProductVariantPolicy.assess(
                catalogPackage,
                actualPackage,
                versionCode,
                isGoogleReady(manager),
                isFireTv(manager)
        );
        if (status == ProductVariantPolicy.Status.NOT_PRODUCT_VARIANT
                && matches(catalogPackage, actualPackage)) {
            return ProductVariantPolicy.Status.READY;
        }
        return status;
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
        result.add(catalogPackage);
        List<String> aliases = PACKAGE_ALIASES.get(catalogPackage);
        if (aliases != null) {
            result.addAll(aliases);
        }
        for (String variant : ProductVariantPolicy.variants(catalogPackage)) {
            if (!result.contains(variant)) result.add(variant);
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

    @SuppressWarnings("deprecation")
    private static long versionCode(PackageManager manager, String packageName) {
        try {
            android.content.pm.PackageInfo info = manager.getPackageInfo(packageName, 0);
            return Build.VERSION.SDK_INT >= 28
                    ? info.getLongVersionCode()
                    : info.versionCode;
        } catch (PackageManager.NameNotFoundException ignored) {
            return 0;
        }
    }

    private static boolean isGoogleReady(PackageManager manager) {
        return enabled(manager, "com.android.vending")
                && enabled(manager, "com.google.android.gms");
    }

    private static boolean isFireTv(PackageManager manager) {
        return manager.hasSystemFeature("amazon.hardware.fire_tv")
                || "amazon".equalsIgnoreCase(Build.MANUFACTURER);
    }

    private static boolean enabled(PackageManager manager, String packageName) {
        try {
            return manager.getApplicationInfo(packageName, 0).enabled;
        } catch (PackageManager.NameNotFoundException ignored) {
            return false;
        }
    }

    private static Resolution assessInstalled(
            PackageManager manager,
            String catalogPackage,
            String actualPackage
    ) {
        long version = versionCode(manager, actualPackage);
        ProductVariantPolicy.Status status = ProductVariantPolicy.assess(
                catalogPackage,
                actualPackage,
                version,
                isGoogleReady(manager),
                isFireTv(manager)
        );
        if (status == ProductVariantPolicy.Status.NOT_PRODUCT_VARIANT
                && matches(catalogPackage, actualPackage)) {
            status = ProductVariantPolicy.Status.READY;
        }
        return new Resolution(
                actualPackage,
                version,
                status
        );
    }

    public static final class Resolution {
        public final String packageName;
        public final long versionCode;
        public final ProductVariantPolicy.Status status;

        Resolution(
                String packageName,
                long versionCode,
                ProductVariantPolicy.Status status
        ) {
            this.packageName = packageName;
            this.versionCode = versionCode;
            this.status = status;
        }

        static Resolution absent() {
            return new Resolution(
                    null,
                    0,
                    ProductVariantPolicy.Status.NOT_PRODUCT_VARIANT
            );
        }
    }
}
