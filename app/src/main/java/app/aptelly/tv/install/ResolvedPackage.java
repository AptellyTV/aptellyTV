package app.aptelly.tv.install;

public final class ResolvedPackage {
    public final String downloadUrl;
    public final long versionCode;
    public final String versionName;
    public final String expectedCertificateSha256;

    public ResolvedPackage(
            String downloadUrl,
            long versionCode,
            String versionName,
            String expectedCertificateSha256
    ) {
        this.downloadUrl = downloadUrl;
        this.versionCode = versionCode;
        this.versionName = versionName;
        this.expectedCertificateSha256 = expectedCertificateSha256;
    }
}
