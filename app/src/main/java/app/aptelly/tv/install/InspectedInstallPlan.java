package app.aptelly.tv.install;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.os.Build;
import java.io.File;
import java.io.IOException;
import java.util.List;

/** Bind bundled/locally discovered plans to the APK's actual Android version before commit. */
public final class InspectedInstallPlan {
    private InspectedInstallPlan() { }

    @SuppressWarnings("deprecation")
    public static InstallPlan read(Context context, InstallPlan plan, List<File> files) throws IOException {
        int base = -1;
        for (int i = 0; i < plan.artifacts.size(); i++) {
            if (plan.artifacts.get(i).kind == ArtifactFile.Kind.BASE) { base = i; break; }
        }
        if (base < 0 || files.size() != plan.artifacts.size()) throw new IOException("INSTALL_PLAN_MISSING_BASE");
        PackageInfo info = context.getPackageManager().getPackageArchiveInfo(files.get(base).getAbsolutePath(), 0);
        if (info == null) throw new IOException("APK_MANIFEST_UNREADABLE");
        long code = Build.VERSION.SDK_INT >= 28 ? info.getLongVersionCode() : info.versionCode;
        if (!InstallTarget.acceptsArchive(plan.packageName, plan.versionCode, info.packageName, code)) {
            throw new IOException("APK_PACKAGE_OR_VERSION_MISMATCH");
        }
        return new InstallPlan(plan.appName, plan.catalogPackageName, info.packageName, code,
                info.versionName, plan.expectedCertificateSha256, plan.deviceProfileId,
                plan.environmentRevision, plan.correlationId, plan.sourceKind, plan.releaseId,
                plan.installationId, plan.evidence, plan.artifacts);
    }
}
