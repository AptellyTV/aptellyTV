package app.aptelly.tv.device;

import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.FeatureInfo;
import android.media.MediaDrm;
import android.os.Build;
import android.util.DisplayMetrics;
import android.view.WindowManager;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public final class StaticDeviceProfile {
    private static final UUID WIDEVINE_UUID = new UUID(
            0xedef8ba979d64aceL,
            0xa3c827dcd51d21edL
    );

    public final int androidApi;
    public final String androidRelease;
    public final String buildId;
    public final String manufacturer;
    public final String brand;
    public final String model;
    public final String device;
    public final String product;
    public final String buildFingerprint;
    public final String securityPatch;
    public final String[] supportedAbis;
    public final String primaryAbi;
    public final boolean television;
    public final int widthPixels;
    public final int heightPixels;
    public final int densityDpi;
    public final long totalMemoryBytes;
    public final String widevineLevel;
    public final String[] systemFeatures;
    public final String hardwareProfileId;
    public final String environmentRevision;
    /** Compatibility alias for schema v1 callers. */
    public final String stableId;

    private StaticDeviceProfile(
            int androidApi,
            String androidRelease,
            String buildId,
            String manufacturer,
            String brand,
            String model,
            String device,
            String product,
            String buildFingerprint,
            String securityPatch,
            String[] supportedAbis,
            boolean television,
            int widthPixels,
            int heightPixels,
            int densityDpi,
            long totalMemoryBytes,
            String widevineLevel,
            String[] systemFeatures
    ) {
        this.androidApi = androidApi;
        this.androidRelease = androidRelease;
        this.buildId = buildId;
        this.manufacturer = manufacturer;
        this.brand = brand;
        this.model = model;
        this.device = device;
        this.product = product;
        this.buildFingerprint = buildFingerprint;
        this.securityPatch = securityPatch;
        this.supportedAbis = supportedAbis.clone();
        this.primaryAbi = supportedAbis.length == 0 ? "" : supportedAbis[0];
        this.television = television;
        this.widthPixels = widthPixels;
        this.heightPixels = heightPixels;
        this.densityDpi = densityDpi;
        this.totalMemoryBytes = totalMemoryBytes;
        this.widevineLevel = widevineLevel;
        this.systemFeatures = systemFeatures.clone();
        this.hardwareProfileId = hash(String.join(
                "|",
                manufacturer,
                brand,
                model,
                device,
                product,
                String.join(",", supportedAbis),
                widthPixels + "x" + heightPixels + "@" + densityDpi,
                Long.toString(totalMemoryBytes)
        ));
        this.environmentRevision = hash(String.join(
                "|",
                hardwareProfileId,
                Integer.toString(androidApi),
                androidRelease,
                buildId,
                buildFingerprint,
                securityPatch,
                String.join(",", systemFeatures),
                widevineLevel
        ));
        this.stableId = hardwareProfileId;
    }

    public static StaticDeviceProfile collect(Context context) {
        DisplayMetrics metrics = new DisplayMetrics();
        WindowManager windowManager =
                (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        if (windowManager != null) {
            windowManager.getDefaultDisplay().getRealMetrics(metrics);
        }
        ActivityManager activityManager =
                (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();
        if (activityManager != null) {
            activityManager.getMemoryInfo(memoryInfo);
        }
        PackageManager packageManager = context.getPackageManager();
        String[] features = collectFeatures(packageManager);
        return new StaticDeviceProfile(
                Build.VERSION.SDK_INT,
                safe(Build.VERSION.RELEASE),
                safe(Build.ID),
                safe(Build.MANUFACTURER),
                safe(Build.BRAND),
                safe(Build.MODEL),
                safe(Build.DEVICE),
                safe(Build.PRODUCT),
                safe(Build.FINGERPRINT),
                safe(Build.VERSION.SECURITY_PATCH),
                Build.SUPPORTED_ABIS,
                packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
                        || packageManager.hasSystemFeature(PackageManager.FEATURE_TELEVISION),
                metrics.widthPixels,
                metrics.heightPixels,
                metrics.densityDpi,
                memoryInfo.totalMem,
                detectWidevineLevel(),
                features
        );
    }

    private static String[] collectFeatures(PackageManager packageManager) {
        List<String> names = new ArrayList<>();
        FeatureInfo[] available = packageManager.getSystemAvailableFeatures();
        if (available != null) {
            for (FeatureInfo feature : available) {
                if (feature != null && feature.name != null && !feature.name.isEmpty()) {
                    names.add(feature.name);
                }
            }
        }
        Collections.sort(names);
        return names.toArray(new String[0]);
    }

    private static String detectWidevineLevel() {
        if (!MediaDrm.isCryptoSchemeSupported(WIDEVINE_UUID)) {
            return "Unavailable";
        }
        MediaDrm mediaDrm = null;
        try {
            mediaDrm = new MediaDrm(WIDEVINE_UUID);
            String level = mediaDrm.getPropertyString("securityLevel");
            return level == null || level.isEmpty() ? "Unknown" : level;
        } catch (Exception ignored) {
            return "Unknown";
        } finally {
            if (mediaDrm != null) {
                mediaDrm.release();
            }
        }
    }

    private static String hash(String value) {
        try {
            byte[] result = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder text = new StringBuilder();
            for (int index = 0; index < 12; index++) {
                text.append(String.format(Locale.ROOT, "%02x", result[index] & 0xff));
            }
            return text.toString();
        } catch (Exception ignored) {
            return Integer.toHexString(value.hashCode());
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
