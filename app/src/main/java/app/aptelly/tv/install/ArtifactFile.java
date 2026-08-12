package app.aptelly.tv.install;

public final class ArtifactFile {
    public enum Kind {
        BASE,
        SPLIT
    }

    public final Kind kind;
    public final String fileName;
    public final String downloadUrl;
    public final String assetName;
    public final String sha256;
    public final long sizeBytes;

    private ArtifactFile(
            Kind kind,
            String fileName,
            String downloadUrl,
            String assetName,
            String sha256,
            long sizeBytes
    ) {
        this.kind = kind;
        this.fileName = fileName;
        this.downloadUrl = downloadUrl;
        this.assetName = assetName;
        this.sha256 = sha256;
        this.sizeBytes = sizeBytes;
    }

    public static ArtifactFile remote(
            Kind kind,
            String fileName,
            String downloadUrl,
            String sha256,
            long sizeBytes
    ) {
        return new ArtifactFile(kind, fileName, downloadUrl, "", sha256, sizeBytes);
    }

    public static ArtifactFile bundled(
            Kind kind,
            String fileName,
            String assetName,
            String sha256,
            long sizeBytes
    ) {
        return new ArtifactFile(kind, fileName, "", assetName, sha256, sizeBytes);
    }

    public boolean isBundled() {
        return assetName != null && !assetName.isEmpty();
    }
}
