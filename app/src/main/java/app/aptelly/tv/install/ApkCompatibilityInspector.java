package app.aptelly.tv.install;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.FeatureInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

final class ApkCompatibilityInspector {
    private ApkCompatibilityInspector() {
    }

    static void requireCompatible(
            Context context,
            InstallPlan plan,
            List<File> files
    ) throws IOException {
        int baseIndex = -1;
        for (int index = 0; index < plan.artifacts.size(); index++) {
            if (plan.artifacts.get(index).kind == ArtifactFile.Kind.BASE) {
                baseIndex = index;
                break;
            }
        }
        if (baseIndex < 0) {
            throw new IOException("INSTALL_PLAN_MISSING_BASE");
        }
        PackageManager manager = context.getPackageManager();
        PackageInfo archive = manager.getPackageArchiveInfo(
                files.get(baseIndex).getAbsolutePath(),
                PackageManager.GET_ACTIVITIES | PackageManager.GET_CONFIGURATIONS
        );
        if (archive == null || archive.applicationInfo == null) {
            throw new IOException("APK_MANIFEST_UNREADABLE");
        }
        ApplicationInfo application = archive.applicationInfo;
        if (application.minSdkVersion > Build.VERSION.SDK_INT) {
            throw new IOException(
                    "APK_REQUIRES_API_" + application.minSdkVersion
                            + "_DEVICE_API_" + Build.VERSION.SDK_INT
            );
        }
        if (archive.reqFeatures != null) {
            for (FeatureInfo feature : archive.reqFeatures) {
                if ((feature.flags & FeatureInfo.FLAG_REQUIRED) != 0
                        && feature.name != null
                        && !manager.hasSystemFeature(feature.name)) {
                    throw new IOException("APK_MISSING_FEATURE_" + feature.name);
                }
            }
        }
        requireCompatibleNativeCode(files);
        try {
            PackageInfo installed = manager.getPackageInfo(plan.packageName, 0);
            long installedCode = Build.VERSION.SDK_INT >= 28
                    ? installed.getLongVersionCode()
                    : installed.versionCode;
            if (plan.versionCode > 0 && plan.versionCode < installedCode) {
                throw new IOException(
                        "APK_VERSION_DOWNGRADE_"
                                + plan.versionCode + "_BELOW_" + installedCode
                );
            }
        } catch (PackageManager.NameNotFoundException ignored) {
            // A first install has no downgrade constraint.
        }
    }

    private static void requireCompatibleNativeCode(List<File> files) throws IOException {
        Set<String> archiveAbis = new HashSet<>();
        for (File file : files) {
            try (ZipFile zip = new ZipFile(file)) {
                zip.stream().map(ZipEntry::getName).forEach(name -> {
                    if (!name.startsWith("lib/") || !name.endsWith(".so")) {
                        return;
                    }
                    String[] parts = name.split("/");
                    if (parts.length >= 3) {
                        archiveAbis.add(parts[1]);
                    }
                });
            }
        }
        if (archiveAbis.isEmpty()) {
            return;
        }
        for (String deviceAbi : Build.SUPPORTED_ABIS) {
            if (archiveAbis.contains(deviceAbi)) {
                return;
            }
        }
        throw new IOException(
                "APK_ABI_MISMATCH_DEVICE_"
                        + String.join(",", Build.SUPPORTED_ABIS)
                        + "_APK_" + String.join(",", archiveAbis)
        );
    }
}
