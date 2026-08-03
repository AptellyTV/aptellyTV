package app.aptelly.tv.device;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.StatFs;
import android.provider.Settings;
import android.webkit.WebView;

public final class DynamicDeviceProfile {
    public final boolean googlePlay;
    public final boolean googleServices;
    public final boolean amazonStore;
    public final boolean auroraStore;
    public final boolean xiaomiStore;
    public final boolean packageInstaller;
    public final boolean unknownSourcesAllowed;
    public final boolean systemWebView;
    public final long availableStorageBytes;
    public final NetworkStatus networkStatus;

    private DynamicDeviceProfile(
            boolean googlePlay,
            boolean googleServices,
            boolean amazonStore,
            boolean auroraStore,
            boolean xiaomiStore,
            boolean packageInstaller,
            boolean unknownSourcesAllowed,
            boolean systemWebView,
            long availableStorageBytes,
            NetworkStatus networkStatus
    ) {
        this.googlePlay = googlePlay;
        this.googleServices = googleServices;
        this.amazonStore = amazonStore;
        this.auroraStore = auroraStore;
        this.xiaomiStore = xiaomiStore;
        this.packageInstaller = packageInstaller;
        this.unknownSourcesAllowed = unknownSourcesAllowed;
        this.systemWebView = systemWebView;
        this.availableStorageBytes = availableStorageBytes;
        this.networkStatus = networkStatus;
    }

    public static DynamicDeviceProfile collect(Context context) {
        StatFs statFs = new StatFs(context.getFilesDir().getAbsolutePath());
        return new DynamicDeviceProfile(
                hasPackage(context, "com.android.vending"),
                hasPackage(context, "com.google.android.gms"),
                hasPackage(context, "com.amazon.venezia"),
                hasPackage(context, "com.aurora.store"),
                hasPackage(context, "com.xiaomi.mitv.appstore"),
                hasPackageInstaller(context),
                unknownSourcesAllowed(context),
                hasSystemWebView(),
                statFs.getAvailableBytes(),
                NetworkPreflight.inspect(context)
        );
    }

    private static boolean unknownSourcesAllowed(Context context) {
        if (Build.VERSION.SDK_INT < 26
                || context.getPackageManager().canRequestPackageInstalls()) {
            return true;
        }
        // Xiaomi TV and some other OEM TV ROMs retain one global switch instead
        // of Android O's per-caller unknown-source permission.
        return Settings.Secure.getInt(
                context.getContentResolver(),
                "install_non_market_apps",
                0
        ) == 1;
    }

    private static boolean hasPackageInstaller(Context context) {
        Intent install = new Intent(Intent.ACTION_INSTALL_PACKAGE);
        install.setDataAndType(
                Uri.parse("content://app.aptelly.tv/install-check.apk"),
                "application/vnd.android.package-archive"
        );
        install.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        if (context.getPackageManager().resolveActivity(
                install,
                PackageManager.MATCH_DEFAULT_ONLY
        ) != null) {
            return true;
        }
        Intent view = new Intent(Intent.ACTION_VIEW);
        view.setDataAndType(
                Uri.parse("content://app.aptelly.tv/install-check.apk"),
                "application/vnd.android.package-archive"
        );
        view.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        return context.getPackageManager().resolveActivity(
                view,
                PackageManager.MATCH_DEFAULT_ONLY
        ) != null;
    }

    private static boolean hasSystemWebView() {
        try {
            return WebView.getCurrentWebViewPackage() != null;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static boolean hasPackage(Context context, String packageName) {
        try {
            ApplicationInfo info = context.getPackageManager()
                    .getApplicationInfo(packageName, 0);
            return info.enabled;
        } catch (PackageManager.NameNotFoundException ignored) {
            return false;
        }
    }
}
