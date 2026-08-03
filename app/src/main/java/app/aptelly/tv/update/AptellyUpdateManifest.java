package app.aptelly.tv.update;

public final class AptellyUpdateManifest {
    public final int versionCode;
    public final String versionName;
    public final int minSdk;
    public final String apkUrl;
    public final long sizeBytes;
    public final String sha256;
    public final boolean mandatory;

    AptellyUpdateManifest(
            int versionCode,
            String versionName,
            int minSdk,
            String apkUrl,
            long sizeBytes,
            String sha256,
            boolean mandatory
    ) {
        this.versionCode = versionCode;
        this.versionName = versionName;
        this.minSdk = minSdk;
        this.apkUrl = apkUrl;
        this.sizeBytes = sizeBytes;
        this.sha256 = sha256;
        this.mandatory = mandatory;
    }
}
