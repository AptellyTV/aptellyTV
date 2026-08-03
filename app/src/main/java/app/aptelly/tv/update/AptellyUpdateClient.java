package app.aptelly.tv.update;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import androidx.core.content.FileProvider;

import app.aptelly.tv.BuildConfig;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class AptellyUpdateClient {
    public static final long MAX_APK_BYTES = 300L * 1024L * 1024L;
    private static final int MAX_MANIFEST_BYTES = 128 * 1024;
    private static final int MAX_REDIRECTS = 3;
    private final Activity activity;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    public interface Listener {
        void onStatus(Status status, String detail);
    }

    public enum Status {
        CHECKING,
        NOT_CONFIGURED,
        UP_TO_DATE,
        DOWNLOADING,
        READY_TO_INSTALL,
        FAILED
    }

    public AptellyUpdateClient(Activity activity) {
        this.activity = activity;
    }

    public void checkAndInstall(Listener listener) {
        post(listener, Status.CHECKING, "");
        executor.execute(() -> {
            try {
                String endpoint = BuildConfig.MATCH_API_BASE_URL
                        + "/v1/aptelly/releases/stable";
                HttpURLConnection connection = open(endpoint);
                int code = connection.getResponseCode();
                if (code == 404) {
                    post(listener, Status.NOT_CONFIGURED, "");
                    return;
                }
                if (code != 200) throw new IllegalStateException("HTTP " + code);
                String envelope = readText(connection, MAX_MANIFEST_BYTES);
                AptellyUpdateManifest manifest = AptellyUpdateVerifier.verify(envelope);
                if (manifest.minSdk > Build.VERSION.SDK_INT) {
                    throw new SecurityException("Update requires API " + manifest.minSdk);
                }
                if (manifest.versionCode <= BuildConfig.VERSION_CODE) {
                    post(listener, Status.UP_TO_DATE, manifest.versionName);
                    return;
                }
                post(listener, Status.DOWNLOADING, manifest.versionName);
                File apk = downloadAndVerify(manifest);
                post(listener, Status.READY_TO_INSTALL, manifest.versionName);
                main.post(() -> launchInstaller(apk));
            } catch (Exception error) {
                post(listener, Status.FAILED, String.valueOf(error.getMessage()));
            }
        });
    }

    public void shutdown() {
        executor.shutdownNow();
    }

    private File downloadAndVerify(AptellyUpdateManifest manifest) throws Exception {
        File partial = new File(activity.getCacheDir(), "aptelly-update.apk.part");
        File complete = new File(activity.getCacheDir(), "aptelly-update.apk");
        if (partial.exists() && !partial.delete()) throw new IllegalStateException("Stale update");
        HttpURLConnection connection = open(manifest.apkUrl);
        int code = connection.getResponseCode();
        if (code != 200) throw new IllegalStateException("APK HTTP " + code);
        long declared = connection.getContentLengthLong();
        if (declared > MAX_APK_BYTES || (declared > 0 && declared != manifest.sizeBytes)) {
            throw new SecurityException("Update size mismatch");
        }
        long total = 0;
        try (InputStream input = connection.getInputStream();
             FileOutputStream output = new FileOutputStream(partial)) {
            byte[] buffer = new byte[64 * 1024];
            int count;
            while ((count = input.read(buffer)) >= 0) {
                total += count;
                if (total > MAX_APK_BYTES) throw new SecurityException("Update too large");
                output.write(buffer, 0, count);
            }
            output.getFD().sync();
        } catch (Exception error) {
            partial.delete();
            throw error;
        }
        if (total != manifest.sizeBytes || !manifest.sha256.equals(sha256(partial))) {
            partial.delete();
            throw new SecurityException("Update SHA-256 mismatch");
        }
        validateApk(partial, manifest);
        if (complete.exists() && !complete.delete()) throw new IllegalStateException("Old update");
        if (!partial.renameTo(complete)) throw new IllegalStateException("Finalize update failed");
        return complete;
    }

    private void validateApk(File apk, AptellyUpdateManifest manifest) throws Exception {
        PackageManager manager = activity.getPackageManager();
        int flags = Build.VERSION.SDK_INT >= 28
                ? PackageManager.GET_SIGNING_CERTIFICATES
                : PackageManager.GET_SIGNATURES;
        PackageInfo info = manager.getPackageArchiveInfo(apk.getAbsolutePath(), flags);
        if (info == null || !activity.getPackageName().equals(info.packageName)) {
            throw new SecurityException("Downloaded APK package mismatch");
        }
        long version = Build.VERSION.SDK_INT >= 28 ? info.getLongVersionCode() : info.versionCode;
        if (version != manifest.versionCode || version <= BuildConfig.VERSION_CODE) {
            throw new SecurityException("Downloaded APK version mismatch");
        }
        Signature[] signatures = Build.VERSION.SDK_INT >= 28 && info.signingInfo != null
                ? info.signingInfo.getApkContentsSigners()
                : info.signatures;
        if (signatures == null || signatures.length != 1) {
            throw new SecurityException("Downloaded APK signer mismatch");
        }
        String certificate = hex(MessageDigest.getInstance("SHA-256")
                .digest(signatures[0].toByteArray()));
        if (!AptellyUpdateVerifier.RELEASE_CERTIFICATE_SHA256.equals(certificate)) {
            throw new SecurityException("Downloaded APK certificate mismatch");
        }
    }

    private void launchInstaller(File apk) {
        Uri uri = FileProvider.getUriForFile(
                activity,
                activity.getPackageName() + ".files",
                apk
        );
        Intent intent = new Intent(Intent.ACTION_INSTALL_PACKAGE)
                .setData(uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                .putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
                .putExtra(Intent.EXTRA_RETURN_RESULT, true);
        activity.startActivity(intent);
    }

    private static HttpURLConnection open(String address) throws Exception {
        URL current = new URL(address);
        if (!"https".equalsIgnoreCase(current.getProtocol())) {
            throw new SecurityException("Update URL must use HTTPS");
        }
        for (int redirects = 0; redirects <= MAX_REDIRECTS; redirects++) {
            HttpURLConnection connection = (HttpURLConnection) current.openConnection();
            connection.setConnectTimeout(10_000);
            connection.setReadTimeout(30_000);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty(
                    "Accept",
                    "application/json, application/vnd.android.package-archive"
            );
            int code = connection.getResponseCode();
            if (!isRedirect(code)) return connection;
            if (redirects == MAX_REDIRECTS) {
                connection.disconnect();
                throw new SecurityException("Too many update redirects");
            }
            String location = connection.getHeaderField("Location");
            if (location == null || location.trim().isEmpty()) {
                connection.disconnect();
                throw new SecurityException("Update redirect missing location");
            }
            URL next = new URL(current, location);
            if (!isRedirectAllowed(current, next)) {
                connection.disconnect();
                throw new SecurityException("Unsafe update redirect");
            }
            connection.disconnect();
            current = next;
        }
        throw new SecurityException("Too many update redirects");
    }

    static boolean isRedirectAllowed(URL current, URL next) {
        if (!"https".equalsIgnoreCase(current.getProtocol())
                || !"https".equalsIgnoreCase(next.getProtocol())) {
            return false;
        }
        String currentHost = current.getHost().toLowerCase(Locale.ROOT);
        String nextHost = next.getHost().toLowerCase(Locale.ROOT);
        if (currentHost.equals(nextHost)) return true;
        return ("github.com".equals(currentHost) || isGitHubReleaseAssetHost(currentHost))
                && isGitHubReleaseAssetHost(nextHost);
    }

    private static boolean isGitHubReleaseAssetHost(String host) {
        return "release-assets.githubusercontent.com".equals(host)
                || "objects.githubusercontent.com".equals(host)
                || "github-releases.githubusercontent.com".equals(host);
    }

    private static boolean isRedirect(int code) {
        return code == HttpURLConnection.HTTP_MOVED_PERM
                || code == HttpURLConnection.HTTP_MOVED_TEMP
                || code == HttpURLConnection.HTTP_SEE_OTHER
                || code == 307
                || code == 308;
    }

    private static String readText(HttpURLConnection connection, int maxBytes) throws Exception {
        try (InputStream input = connection.getInputStream();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) >= 0) {
                if (output.size() + count > maxBytes) {
                    throw new SecurityException("Update manifest too large");
                }
                output.write(buffer, 0, count);
            }
            return output.toString("UTF-8");
        }
    }

    private static String sha256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = new FileInputStream(file)) {
            byte[] buffer = new byte[64 * 1024];
            int count;
            while ((count = input.read(buffer)) >= 0) digest.update(buffer, 0, count);
        }
        return hex(digest.digest());
    }

    private static String hex(byte[] bytes) {
        StringBuilder value = new StringBuilder(bytes.length * 2);
        for (byte item : bytes) value.append(String.format(Locale.ROOT, "%02x", item));
        return value.toString();
    }

    private void post(Listener listener, Status status, String detail) {
        main.post(() -> listener.onStatus(status, detail));
    }
}
