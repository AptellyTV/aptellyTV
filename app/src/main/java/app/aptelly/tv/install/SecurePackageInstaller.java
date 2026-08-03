package app.aptelly.tv.install;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;
import android.util.Log;

import app.aptelly.tv.BuildConfig;
import app.aptelly.tv.R;
import app.aptelly.tv.catalog.CatalogApp;
import app.aptelly.tv.catalog.InstalledAppResolver;
import app.aptelly.tv.device.NetworkPreflight;
import app.aptelly.tv.device.NetworkStatus;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class SecurePackageInstaller {
    private static final String LOG_TAG = "AptellyInstaller";
    private static final long STALE_SESSION_AGE_MS = 5L * 60L * 1000L;
    public interface Listener {
        void onStatus(String status);

        void onError(String message);
    }

    private final Activity activity;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final PendingInstallStore pendingInstallStore;
    private final Set<String> inFlightPackages = ConcurrentHashMap.newKeySet();
    private final Map<String, InstallPlan> inFlightPlans = new ConcurrentHashMap<>();
    private final Map<String, List<File>> inFlightFiles = new ConcurrentHashMap<>();
    private Runnable pendingPermissionAction;
    private boolean pendingOemPermissionFlow;
    private boolean oemPermissionBypassOnce;
    private static final String BUNDLED_CLASH_ASSET =
            "bootstrap/clash-meta-universal.apk";

    public SecurePackageInstaller(Activity activity) {
        this.activity = activity;
        this.pendingInstallStore = new PendingInstallStore(activity);
    }

    public void installIfMissing(CatalogApp app, Listener listener) {
        String installedPackage = InstalledAppResolver.installedPackage(
                activity,
                app.packageName
        );
        if (installedPackage != null) {
            Intent launch = InstalledAppResolver.launchIntent(activity, app.packageName);
            if (launch != null) {
                activity.startActivity(launch);
                listener.onStatus(activity.getString(
                        R.string.app_already_installed,
                        app.name
                ));
            } else {
                listener.onError(activity.getString(R.string.install_verified_no_entry));
            }
            return;
        }
        install(app, listener);
    }

    public void install(CatalogApp app, Listener listener) {
        reconcileCompletedInstalls();
        if (!app.supportsOneClickInstall()) {
            listener.onError(activity.getString(R.string.install_source_unverified));
            return;
        }
        pendingInstallStore.save(
                app.packageName,
                app.name,
                PendingInstallStore.State.REQUESTED,
                ""
        );
        if (!isInstalled(app.packageName)) {
            if (app.source == CatalogApp.Source.OFFICIAL_CLASH_META) {
                installBundledPackage(
                        app,
                        BUNDLED_CLASH_ASSET,
                        PackageResolver.clashCertificateSha256(),
                        listener
                );
                return;
            }
        }

        if (!ensureInstallPermission(() -> install(app, listener), listener)) {
            return;
        }
        if (!ensureNetwork(listener)) {
            return;
        }
        if (!inFlightPackages.add(app.packageName)) {
            listener.onStatus(activity.getString(R.string.install_in_progress, app.name));
            return;
        }

        listener.onStatus(activity.getString(R.string.downloading, app.name));
        pendingInstallStore.save(
                app.packageName,
                app.name,
                PendingInstallStore.State.DOWNLOADING,
                ""
        );
        executor.execute(() -> {
            List<File> files = new ArrayList<>();
            try {
                InstallPlan plan = MatchingApiClient.resolve(activity, app);
                for (ArtifactFile artifact : plan.artifacts) {
                    files.add(download(
                            app.packageName + "-" + files.size(),
                            artifact.downloadUrl,
                            null,
                            null
                    ));
                }
                postStatus(listener, activity.getString(R.string.verifying));
                ApkCompatibilityInspector.requireCompatible(activity, plan, files);
                if (!verifyArtifacts(files, plan)) {
                    deleteAll(files);
                    inFlightPackages.remove(app.packageName);
                    pendingInstallStore.save(
                            plan.packageName,
                            plan.appName,
                            PendingInstallStore.State.FAILED,
                            activity.getString(R.string.signature_failed)
                    );
                    postError(listener, activity.getString(R.string.signature_failed));
                    return;
                }
                activity.runOnUiThread(() -> commitInstall(
                        plan,
                        files,
                        app.packageName,
                        listener
                ));
            } catch (Exception exception) {
                deleteAll(files);
                inFlightPackages.remove(app.packageName);
                if (exception instanceof NoMatchingArtifactException) {
                    pendingInstallStore.clear();
                    NoMatchingArtifactException noMatch =
                            (NoMatchingArtifactException) exception;
                    if ("UP_TO_DATE".equals(noMatch.reasonCode)) {
                        postError(listener, activity.getString(R.string.up_to_date, app.name));
                        return;
                    }
                    activity.runOnUiThread(() -> {
                        if (StoreInstallRouter.open(activity, app)) {
                            listener.onStatus(activity.getString(
                                    R.string.opened_managed_store,
                                    app.name
                            ));
                            return;
                        }
                        int message = "AMAZON_VARIANT_NOT_DIRECT".equals(noMatch.reasonCode)
                                ? R.string.amazon_variant_not_direct
                                : R.string.install_source_unverified;
                        listener.onError(activity.getString(message));
                    });
                    return;
                }
                String failureMessage = shouldSuggestStartingClash(exception)
                        ? activity.getString(R.string.matcher_unreachable_clash_stopped)
                        : activity.getString(
                                R.string.download_failed,
                                exception.getMessage() == null
                                        ? exception.getClass().getSimpleName()
                                        : exception.getMessage()
                        );
                pendingInstallStore.save(
                        app.packageName,
                        app.name,
                        PendingInstallStore.State.FAILED,
                        failureMessage
                );
                postError(
                        listener,
                        failureMessage
                );
            }
        });
    }

    private boolean shouldSuggestStartingClash(Exception exception) {
        if (!(exception instanceof IOException)
                || NetworkPreflight.inspect(activity).kind == NetworkStatus.Kind.VPN_READY) {
            return false;
        }
        return InstalledAppResolver.installedPackage(
                activity,
                "com.github.metacubex.clash.meta"
        ) != null;
    }

    private void installBundledPackage(
            CatalogApp app,
            String assetName,
            String expectedCertificateSha256,
            Listener listener
    ) {
        if (isInstalled(app.packageName)) {
            listener.onError(activity.getString(
                    R.string.app_already_installed,
                    app.name
            ));
            return;
        }
        if (!ensureInstallPermission(
                () -> installBundledPackage(
                        app,
                        assetName,
                        expectedCertificateSha256,
                        listener
                ),
                listener
        )) {
            return;
        }
        if (!inFlightPackages.add(app.packageName)) {
            listener.onStatus(activity.getString(R.string.install_in_progress, app.name));
            return;
        }

        listener.onStatus(activity.getString(R.string.preparing_offline, app.name));
        pendingInstallStore.save(
                app.packageName,
                app.name,
                PendingInstallStore.State.DOWNLOADING,
                ""
        );
        executor.execute(() -> {
            File apk = null;
            try {
                apk = copyBundledAsset(
                        assetName,
                        app.packageName + "-bundled.apk"
                );
                postStatus(listener, activity.getString(R.string.verifying));
                InstallPlan compatibilityPlan = new InstallPlan(
                        app.name,
                        app.packageName,
                        0,
                        BuildConfig.BUNDLED_CLASH_VERSION,
                        expectedCertificateSha256,
                        "bundled",
                        InstallPlan.Evidence.BUNDLED_TESTED,
                        Collections.singletonList(ArtifactFile.bundled(
                                ArtifactFile.Kind.BASE,
                                "base.apk",
                                assetName,
                                BuildConfig.BUNDLED_CLASH_SHA256,
                                apk.length()
                        ))
                );
                ApkCompatibilityInspector.requireCompatible(
                        activity,
                        compatibilityPlan,
                        Collections.singletonList(apk)
                );
                if (!verify(
                        apk,
                        app.packageName,
                        expectedCertificateSha256
                )) {
                    deleteQuietly(apk);
                    inFlightPackages.remove(app.packageName);
                    postError(listener, activity.getString(R.string.signature_failed));
                    return;
                }
                File finalApk = apk;
                InstallPlan plan = compatibilityPlan;
                activity.runOnUiThread(() -> {
                    if (isInstalled(app.packageName)) {
                        deleteQuietly(finalApk);
                        inFlightPackages.remove(app.packageName);
                        listener.onError(
                                activity.getString(
                                        R.string.app_already_installed,
                                        app.name
                                )
                        );
                        return;
                    }
                    commitInstall(
                            plan,
                            Collections.singletonList(finalApk),
                            app.packageName,
                            listener
                    );
                });
            } catch (Exception exception) {
                deleteQuietly(apk);
                inFlightPackages.remove(app.packageName);
                postError(
                        listener,
                        activity.getString(
                                R.string.offline_package_failed,
                                exception.getMessage() == null
                                        ? exception.getClass().getSimpleName()
                                        : exception.getMessage()
                        )
                );
            }
        });
    }

    public void onHostResume() {
        reconcileCompletedInstalls();
        cleanupStaleInstallSessions();
        if (pendingPermissionAction == null
                || (!activity.getPackageManager().canRequestPackageInstalls()
                && !pendingOemPermissionFlow)) {
            return;
        }
        Runnable action = pendingPermissionAction;
        boolean returnedFromOemSettings = pendingOemPermissionFlow;
        pendingPermissionAction = null;
        pendingOemPermissionFlow = false;
        oemPermissionBypassOnce = returnedFromOemSettings;
        action.run();
    }

    public void shutdown() {
        executor.shutdownNow();
    }

    private boolean ensureInstallPermission(Runnable retry, Listener listener) {
        if (Build.VERSION.SDK_INT < 26
                || activity.getPackageManager().canRequestPackageInstalls()
                || legacyGlobalUnknownSourcesAllowed()) {
            return true;
        }
        if (oemPermissionBypassOnce) {
            oemPermissionBypassOnce = false;
            return true;
        }
        Intent settings = new Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:" + activity.getPackageName())
        );
        boolean oemGlobalFlow = false;
        if (settings.resolveActivity(activity.getPackageManager()) == null) {
            settings = new Intent("com.xiaomi.mitv.settings.SECURITY_SETTINGS");
            oemGlobalFlow = true;
        }
        if (settings.resolveActivity(activity.getPackageManager()) == null) {
            // Several TV ROMs expose only a global unknown-source switch and still
            // report canRequestPackageInstalls() as false after it is enabled.
            // PackageInstaller remains the authoritative final permission check.
            Log.w(LOG_TAG, "No unknown-source settings activity; deferring to PackageInstaller");
            return true;
        }
        pendingPermissionAction = retry;
        pendingOemPermissionFlow = oemGlobalFlow;
        PendingInstallStore.Task task = pendingInstallStore.read();
        if (task != null) {
            pendingInstallStore.save(
                    task.packageName,
                    task.appName,
                    PendingInstallStore.State.WAITING_PERMISSION,
                    ""
            );
        }
        listener.onError(activity.getString(R.string.unknown_sources_needed));
        try {
            activity.startActivity(settings);
            return false;
        } catch (ActivityNotFoundException | SecurityException exception) {
            Log.w(LOG_TAG, "Unknown-source settings unavailable", exception);
            pendingPermissionAction = null;
            pendingOemPermissionFlow = false;
            return true;
        }
    }

    private boolean legacyGlobalUnknownSourcesAllowed() {
        return Settings.Secure.getInt(
                activity.getContentResolver(),
                "install_non_market_apps",
                0
        ) == 1;
    }

    private File download(
            String packageName,
            String sourceUrl,
            String userAgent,
            String cookie
    ) throws IOException {
        File folder = activity.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        if (folder == null && (folder = activity.getCacheDir()) == null) {
            throw new IOException("No writable download folder");
        }
        File target = new File(folder, packageName + ".apk");
        File temporary = new File(folder, packageName + ".part");

        HttpURLConnection connection = (HttpURLConnection) new URL(sourceUrl).openConnection();
        connection.setConnectTimeout(20_000);
        connection.setReadTimeout(120_000);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty(
                "User-Agent",
                userAgent == null || userAgent.isEmpty()
                        ? "Aptelly/" + BuildConfig.VERSION_NAME
                        : userAgent
        );
        if (cookie != null && !cookie.isEmpty()) {
            connection.setRequestProperty("Cookie", cookie);
        }
        try {
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) {
                throw new IOException("HTTP " + status);
            }
            try (InputStream input = connection.getInputStream();
                 FileOutputStream output = new FileOutputStream(temporary)) {
                byte[] buffer = new byte[64 * 1024];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    if (Thread.currentThread().isInterrupted()) {
                        throw new IOException("Download cancelled");
                    }
                    output.write(buffer, 0, count);
                }
                output.getFD().sync();
            }
            if (target.exists() && !target.delete()) {
                throw new IOException("Cannot replace old download");
            }
            if (!temporary.renameTo(target)) {
                throw new IOException("Cannot finalize download");
            }
            return target;
        } finally {
            connection.disconnect();
            deleteQuietly(temporary);
        }
    }

    private File copyBundledAsset(String assetName, String fileName) throws IOException {
        File folder = activity.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        if (folder == null && (folder = activity.getCacheDir()) == null) {
            throw new IOException("No writable package folder");
        }
        File target = new File(folder, fileName);
        File temporary = new File(folder, fileName + ".part");
        try (InputStream input = activity.getAssets().open(assetName);
             FileOutputStream output = new FileOutputStream(temporary)) {
            byte[] buffer = new byte[64 * 1024];
            int count;
            while ((count = input.read(buffer)) != -1) {
                if (Thread.currentThread().isInterrupted()) {
                    throw new IOException("Copy cancelled");
                }
                output.write(buffer, 0, count);
            }
            output.getFD().sync();
        } finally {
            if (target.exists() && !target.delete()) {
                deleteQuietly(temporary);
                throw new IOException("Cannot replace old bundled package");
            }
        }
        if (!temporary.renameTo(target)) {
            deleteQuietly(temporary);
            throw new IOException("Cannot finalize bundled package");
        }
        return target;
    }

    private boolean isInstalled(String packageName) {
        try {
            activity.getPackageManager().getPackageInfo(packageName, 0);
            return true;
        } catch (PackageManager.NameNotFoundException ignored) {
            return false;
        }
    }

    private boolean verify(File apk, String packageName, String expectedDigest) throws Exception {
        int signatureFlag = Build.VERSION.SDK_INT >= 28
                ? PackageManager.GET_SIGNING_CERTIFICATES
                        | PackageManager.GET_SIGNATURES
                : PackageManager.GET_SIGNATURES;
        PackageInfo info = activity.getPackageManager().getPackageArchiveInfo(
                apk.getAbsolutePath(),
                signatureFlag
        );
        if (info == null
                || (packageName != null
                && !packageName.isEmpty()
                && !packageName.equals(info.packageName))) {
            Log.e(LOG_TAG, "Package archive identity mismatch for " + packageName);
            return false;
        }
        String verifiedPackageName = info.packageName;

        Set<String> downloadedSigners = signerDigests(info);
        if (downloadedSigners.isEmpty()) {
            Log.e(LOG_TAG, "Package archive has no readable signer: " + packageName);
            return false;
        }

        if (expectedDigest != null && !expectedDigest.isEmpty()) {
            for (String signer : downloadedSigners) {
                if (signer.equalsIgnoreCase(expectedDigest)) {
                    Log.i(LOG_TAG, "Verified publisher certificate for " + packageName);
                    return true;
                }
            }
            Log.e(
                    LOG_TAG,
                    "Publisher certificate mismatch for " + packageName
                            + "; expected=" + expectedDigest
                            + "; actual=" + downloadedSigners
            );
            return false;
        }

        try {
            PackageInfo installed = activity.getPackageManager().getPackageInfo(
                    verifiedPackageName,
                    signatureFlag
            );
            Set<String> installedSigners = signerDigests(installed);
            downloadedSigners.retainAll(installedSigners);
            return !downloadedSigners.isEmpty();
        } catch (PackageManager.NameNotFoundException ignored) {
            // For a first install the exact package name is the available stable identity.
            return true;
        }
    }

    private Set<String> signerDigests(PackageInfo info) throws Exception {
        Set<String> result = new HashSet<>();
        Signature[] signatures = signaturesOf(info);
        if (signatures == null) {
            return result;
        }
        for (Signature signature : signatures) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            result.add(toHex(digest.digest(signature.toByteArray())));
        }
        return result;
    }

    @SuppressWarnings("deprecation")
    private Signature[] signaturesOf(PackageInfo info) {
        if (Build.VERSION.SDK_INT >= 28) {
            if (info.signingInfo == null) {
                return info.signatures;
            }
            Signature[] current = info.signingInfo.getApkContentsSigners();
            if (current != null && current.length > 0) {
                return current;
            }
            Signature[] history = info.signingInfo.getSigningCertificateHistory();
            return history != null && history.length > 0
                    ? history
                    : info.signatures;
        }
        return info.signatures;
    }

    private void commitInstall(
            InstallPlan plan,
            List<File> files,
            String requestPackageName,
            Listener listener
    ) {
        listener.onStatus(activity.getString(R.string.installing_package));
        pendingInstallStore.save(
                plan.packageName,
                plan.appName,
                PendingInstallStore.State.WAITING_CONFIRMATION,
                ""
        );
        inFlightPlans.put(requestPackageName, plan);
        inFlightFiles.put(requestPackageName, new ArrayList<>(files));
        try {
            SessionPackageInstaller.install(
                    activity,
                    plan,
                    files,
                    new InstallSessionRegistry.Callback() {
                        @Override
                        public void onSuccess() {
                            if (!finishTrackedAttempt(requestPackageName, files)) {
                                return;
                            }
                            pendingInstallStore.clear();
                            reportResult(plan, "success", "");
                            postStatus(
                                    listener,
                                    activity.getString(
                                            R.string.install_session_success,
                                            plan.appName
                                    )
                            );
                        }

                        @Override
                        public void onFailure(String message) {
                            if (!finishTrackedAttempt(requestPackageName, files)) {
                                return;
                            }
                            pendingInstallStore.save(
                                    plan.packageName,
                                    plan.appName,
                                    PendingInstallStore.State.FAILED,
                                    message
                            );
                            reportResult(plan, "failure", message);
                            postError(
                                    listener,
                                    activity.getString(
                                            R.string.download_failed,
                                            message == null ? "Install failed" : message
                                    )
                            );
                        }
                    }
            );
        } catch (Exception exception) {
            finishTrackedAttempt(requestPackageName, files);
            pendingInstallStore.save(
                    plan.packageName,
                    plan.appName,
                    PendingInstallStore.State.FAILED,
                    exception.getMessage()
            );
            listener.onError(
                    activity.getString(
                            R.string.download_failed,
                            exception.getMessage() == null
                                    ? exception.getClass().getSimpleName()
                                    : exception.getMessage()
                    )
            );
        }
    }

    /**
     * A few TV ROMs copy a sealed PackageInstaller session into their own confirmation flow and
     * install successfully without completing the original session callback. When the host
     * resumes, the package manager's installed version is authoritative. Only reconcile when it
     * has reached the exact requested version or a newer one; an older installed version remains
     * in flight and cannot be mistaken for a successful update.
     */
    private void reconcileCompletedInstalls() {
        for (Map.Entry<String, InstallPlan> entry : inFlightPlans.entrySet()) {
            String requestPackageName = entry.getKey();
            InstallPlan plan = entry.getValue();
            long installedVersion = installedVersionCode(plan.packageName);
            if (installedVersion < plan.versionCode) {
                continue;
            }
            if (!finishTrackedAttempt(requestPackageName, null)) {
                continue;
            }
            Log.i(
                    LOG_TAG,
                    "Reconciled OEM installer completion for " + plan.packageName
                            + " at versionCode=" + installedVersion
            );
            reportResult(plan, "success", "OEM_SESSION_RECONCILED");
        }
    }

    private boolean finishTrackedAttempt(String requestPackageName, List<File> fallbackFiles) {
        boolean wasTracked = inFlightPackages.remove(requestPackageName);
        inFlightPlans.remove(requestPackageName);
        List<File> trackedFiles = inFlightFiles.remove(requestPackageName);
        deleteAll(trackedFiles == null ? fallbackFiles : trackedFiles);
        return wasTracked;
    }

    @SuppressWarnings("deprecation")
    private long installedVersionCode(String packageName) {
        try {
            PackageInfo info = activity.getPackageManager().getPackageInfo(packageName, 0);
            return Build.VERSION.SDK_INT >= 28 ? info.getLongVersionCode() : info.versionCode;
        } catch (PackageManager.NameNotFoundException ignored) {
            return -1L;
        }
    }

    private void cleanupStaleInstallSessions() {
        if (Build.VERSION.SDK_INT < 30) {
            return;
        }
        PackageInstaller installer = activity.getPackageManager().getPackageInstaller();
        long cutoff = System.currentTimeMillis() - STALE_SESSION_AGE_MS;
        try {
            for (PackageInstaller.SessionInfo session : installer.getMySessions()) {
                if (session == null || session.getCreatedMillis() > cutoff) {
                    continue;
                }
                try {
                    installer.abandonSession(session.getSessionId());
                    Log.i(
                            LOG_TAG,
                            "Abandoned stale owned install session " + session.getSessionId()
                    );
                } catch (RuntimeException ignored) {
                    // It may have completed between enumeration and cleanup.
                }
            }
        } catch (RuntimeException exception) {
            Log.w(LOG_TAG, "Unable to enumerate stale install sessions", exception);
        }
    }

    private boolean verifyArtifacts(List<File> files, InstallPlan plan) throws Exception {
        if (files.size() != plan.artifacts.size()) {
            return false;
        }
        for (int index = 0; index < files.size(); index++) {
            File file = files.get(index);
            String expectedHash = plan.artifacts.get(index).sha256;
            if (expectedHash != null
                    && !expectedHash.isEmpty()
                    && !expectedHash.equalsIgnoreCase(fileSha256(file))) {
                return false;
            }
            ArtifactFile artifact = plan.artifacts.get(index);
            if (artifact.kind == ArtifactFile.Kind.BASE) {
                if (!verify(file, plan.packageName, plan.expectedCertificateSha256)) {
                    return false;
                }
            } else {
                // Config splits cannot be parsed as standalone installable APKs
                // by PackageManager on every Android TV build. Their exact
                // server-qualified bytes are pinned by SHA-256 above; the
                // PackageInstaller session remains the authority that rejects
                // a mixed package name or signing certificate.
            }
        }
        return true;
    }

    private boolean ensureNetwork(Listener listener) {
        NetworkStatus status = NetworkPreflight.inspect(activity);
        if (status.canDownload()) {
            return true;
        }
        int message;
        if (status.kind == NetworkStatus.Kind.CAPTIVE_PORTAL) {
            message = R.string.network_needs_login;
        } else if (status.kind == NetworkStatus.Kind.UNVALIDATED) {
            message = R.string.network_not_validated;
        } else {
            message = R.string.network_no_connection;
        }
        listener.onError(activity.getString(message));
        PendingInstallStore.Task task = pendingInstallStore.read();
        if (task != null) {
            pendingInstallStore.save(
                    task.packageName,
                    task.appName,
                    PendingInstallStore.State.FAILED,
                    activity.getString(message)
            );
        }
        return false;
    }

    private void postStatus(Listener listener, String message) {
        activity.runOnUiThread(() -> listener.onStatus(message));
    }

    private void reportResult(
            InstallPlan plan,
            String result,
            String failureCode
    ) {
        try {
            executor.execute(() ->
                    MatchingApiClient.report(plan, result, failureCode)
            );
        } catch (RuntimeException ignored) {
            // The Activity may already be closing; reporting is best effort.
        }
    }

    private void postError(Listener listener, String message) {
        activity.runOnUiThread(() -> listener.onError(message));
    }

    private static void deleteQuietly(File file) {
        if (file != null && file.exists()) {
            // A failed temporary package contains no user data. Ignore a failed cleanup.
            file.delete();
        }
    }

    private static void deleteAll(List<File> files) {
        if (files == null) {
            return;
        }
        for (File file : files) {
            deleteQuietly(file);
        }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            result.append(String.format(Locale.ROOT, "%02x", value & 0xff));
        }
        return result.toString();
    }

    private static String fileSha256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = new java.io.FileInputStream(file)) {
            byte[] buffer = new byte[64 * 1024];
            int count;
            while ((count = input.read(buffer)) != -1) {
                digest.update(buffer, 0, count);
            }
        }
        return toHex(digest.digest());
    }
}
