package app.aptelly.tv.install;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Build;
import java.security.MessageDigest;
import java.util.Locale;

public final class InstalledTargetVerifier {
    private InstalledTargetVerifier() { }

    @SuppressWarnings("deprecation")
    public static boolean matches(Context context, InstallTarget target) {
        if (target == null || target.packageName == null || target.packageName.isEmpty()) return false;
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(target.packageName,
                    Build.VERSION.SDK_INT >= 28 ? PackageManager.GET_SIGNING_CERTIFICATES
                            : PackageManager.GET_SIGNATURES);
            Signature[] signers = Build.VERSION.SDK_INT >= 28 && info.signingInfo != null
                    ? info.signingInfo.getApkContentsSigners() : info.signatures;
            if (signers == null || signers.length != 1) return false;
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(signers[0].toByteArray());
            StringBuilder hash = new StringBuilder();
            for (byte b : digest) hash.append(String.format(Locale.ROOT, "%02x", b & 255));
            return target.matches(info.packageName,
                    Build.VERSION.SDK_INT >= 28 ? info.getLongVersionCode() : info.versionCode,
                    hash.toString(), info.splitNames == null ? 0 : info.splitNames.length);
        } catch (Exception ignored) { return false; }
    }

    public static InstallTarget target(InstallPlan plan) {
        int splits = 0;
        for (ArtifactFile file : plan.artifacts) if (file.kind == ArtifactFile.Kind.SPLIT) splits++;
        return new InstallTarget(plan.packageName, plan.versionCode, plan.expectedCertificateSha256, splits);
    }
}
