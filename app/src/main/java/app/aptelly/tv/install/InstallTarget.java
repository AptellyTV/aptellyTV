package app.aptelly.tv.install;

/** Package identity and Android version codes decide success and updates, never display names. */
public final class InstallTarget {
    public final String packageName;
    public final long versionCode;
    public final String certificateSha256;
    public final int requiredSplits;

    public InstallTarget(String packageName, long versionCode, String certificateSha256, int requiredSplits) {
        this.packageName = packageName;
        this.versionCode = versionCode;
        this.certificateSha256 = certificateSha256;
        this.requiredSplits = requiredSplits;
    }

    public boolean matches(String installedPackage, long installedCode, String signer, int splits) {
        return versionCode > 0 && certificateSha256 != null
                && certificateSha256.matches("(?i)[0-9a-f]{64}")
                && packageName != null && packageName.equals(installedPackage)
                && installedCode >= versionCode && certificateSha256.equalsIgnoreCase(signer)
                && (installedCode > versionCode || splits >= requiredSplits);
    }

    public static boolean acceptsArchive(String expectedPackage, long expectedCode,
            String actualPackage, long actualCode) {
        return expectedPackage != null && expectedPackage.equals(actualPackage)
                && actualCode > 0 && (expectedCode == 0 || expectedCode == actualCode);
    }

    public static boolean requiresInstall(String installedPackage, long installedCode,
            String targetPackage, long targetCode) {
        return targetCode > 0 && (!targetPackage.equals(installedPackage) || targetCode > installedCode);
    }
}
