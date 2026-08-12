package app.aptelly.tv.install;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class InstallPlan {
    public enum Evidence {
        BUNDLED_TESTED,
        EMULATOR_TESTED,
        DEVICE_FAMILY_TESTED,
        PHYSICAL_DEVICE_TESTED
    }

    public final String appName;
    public final String catalogPackageName;
    public final String packageName;
    public final long versionCode;
    public final String versionName;
    public final String expectedCertificateSha256;
    public final String deviceProfileId;
    public final String environmentRevision;
    public final String correlationId;
    public final String sourceKind;
    public final String releaseId;
    public final String installationId;
    public final Evidence evidence;
    public final List<ArtifactFile> artifacts;

    public InstallPlan(
            String appName,
            String packageName,
            long versionCode,
            String versionName,
            String expectedCertificateSha256,
            String deviceProfileId,
            Evidence evidence,
            List<ArtifactFile> artifacts
    ) {
        this(
                appName,
                packageName,
                packageName,
                versionCode,
                versionName,
                expectedCertificateSha256,
                deviceProfileId,
                "",
                "",
                "",
                versionName,
                "",
                evidence,
                artifacts
        );
    }

    public InstallPlan(
            String appName,
            String catalogPackageName,
            String packageName,
            long versionCode,
            String versionName,
            String expectedCertificateSha256,
            String deviceProfileId,
            String environmentRevision,
            String correlationId,
            String sourceKind,
            String releaseId,
            String installationId,
            Evidence evidence,
            List<ArtifactFile> artifacts
    ) {
        if (artifacts == null || artifacts.isEmpty()) {
            throw new IllegalArgumentException("Install plan requires at least one artifact");
        }
        this.appName = appName;
        this.catalogPackageName = catalogPackageName;
        this.packageName = packageName;
        this.versionCode = versionCode;
        this.versionName = versionName;
        this.expectedCertificateSha256 = expectedCertificateSha256;
        this.deviceProfileId = deviceProfileId;
        this.environmentRevision = environmentRevision;
        this.correlationId = correlationId;
        this.sourceKind = sourceKind;
        this.releaseId = releaseId;
        this.installationId = installationId;
        this.evidence = evidence;
        this.artifacts = Collections.unmodifiableList(new ArrayList<>(artifacts));
    }
}
