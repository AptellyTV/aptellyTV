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
import app.aptelly.tv.device.StaticDeviceProfile;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
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
        default void onDownloadStarted() {}
        default void onDownloadProgress(long received, long total) {}
        default void onDownloadFinished() {}
        default void onCancelled() {}
        default void onInstalled(String actualPackage) {}

    }

    private final Activity activity;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final PendingInstallStore pendingInstallStore;
    private static final Set<String> inFlightPackages = ConcurrentHashMap.newKeySet();
    private final Map<String, InstallPlan> inFlightPlans = new ConcurrentHashMap<>();
    private final Map<String, List<File>> inFlightFiles = new ConcurrentHashMap<>();
    private final Map<String, Listener> inFlightListeners = new ConcurrentHashMap<>();
    private volatile DownloadOperation activeDownload;
    private volatile boolean hostClosed;
    private long lastProgressNanos;
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
        PendingInstallStore.Task existing = pendingInstallStore.read();
        if (!inFlightPackages.isEmpty()) {
            listener.onStatus(activity.getString(R.string.install_in_progress, app.name));
            return;
        }
        if (existing != null && existing.state == PendingInstallStore.State.WAITING_CONFIRMATION
                && existing.sessionId >= 0) {
            listener.onError(activity.getString(R.string.install_pending_confirmation));
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

        DownloadOperation operation = beginDownload(listener);
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
                operation.bindWorker();
                InstallPlan plan = PackageResolver.resolvePlan(app, StaticDeviceProfile.collect(activity).hardwareProfileId);
                operation.check();
                File folder = downloadFolder();
                long[] sizes = new long[plan.artifacts.size()];
                for (int index = 0; index < sizes.length; index++) {
                    sizes[index] = plan.artifacts.get(index).sizeBytes;
                }
                DownloadBudget budget = new DownloadBudget(sizes);
                long cached = 0;
                for (int index = 0; index < sizes.length; index++) {
                    ArtifactFile artifact = plan.artifacts.get(index);
                    cached += DownloadCheckpoint.cachedBytes(folder, app.packageName + "-" + index,
                            artifact.sha256, artifact.sizeBytes);
                }
                budget.cached(cached);
                budget.progress((received, total) -> postProgress(listener, received, total));
                budget.preflight(folder.getUsableSpace());
                for (ArtifactFile artifact : plan.artifacts) {
                    files.add(PackageDownload.fetch(folder,
                            app.packageName + "-" + files.size(), artifact.downloadUrl,
                            "Aptelly/" + BuildConfig.VERSION_NAME, artifact.sizeBytes, artifact.sha256, budget, operation));
                }
                postStatus(listener, activity.getString(R.string.verifying));
                if (!verifyArtifacts(files, plan)) {
                    throw new SecurityException(activity.getString(R.string.signature_failed));
                }
                InstallPlan inspectedPlan = InspectedInstallPlan.read(activity, plan, files);
                ApkCompatibilityInspector.requireCompatible(activity, inspectedPlan, files);
                operation.check();
                activity.runOnUiThread(() -> {
                    if (hostClosed || activity.isFinishing() || activity.isDestroyed() || !operation.submit()) {
                        cancelPrepared(app, files, operation, listener);
                        return;
                    }
                    finishDownload(operation, listener);
                    commitInstall(inspectedPlan, files, app.packageName, listener);
                });
            } catch (Exception exception) {
                Log.e(LOG_TAG, "Install failed for " + app.packageName, exception);
                deleteAll(files);
                activity.runOnUiThread(() -> {
                    boolean cancelled = operation.cancelled();
                    finishDownload(operation, listener);
                    inFlightPackages.remove(app.packageName);
                    if (cancelled) {
                        pendingInstallStore.clear();
                        listener.onCancelled();
                        return;
                    }
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
                            listener.onError(InstallGuidance.message(activity, noMatch.reasonCode));
                        });
                        return;
                    }
                    String failureMessage = exception instanceof DownloadBudget.Failure
                            ? downloadFailure((DownloadBudget.Failure) exception)
                            : shouldSuggestStartingClash(exception)
                            ? activity.getString(R.string.matcher_unreachable_clash_stopped)
                            : activity.getString(
                                    R.string.download_failed,
                                    exception.getMessage() == null
                                            ? exception.getClass().getSimpleName()
                                            : exception.getMessage()
                            );
                    if (exception instanceof IOException && !(exception instanceof DownloadBudget.Failure)) {
                        activity.runOnUiThread(() -> {
                            if (StoreInstallRouter.open(activity, app)) {
                                pendingInstallStore.clear();
                                listener.onStatus(activity.getString(
                                        R.string.opened_managed_store,
                                        app.name
                                ));
                                return;
                            }
                            pendingInstallStore.save(
                                    app.packageName,
                                    app.name,
                                    PendingInstallStore.State.FAILED,
                                    failureMessage
                            );
                            listener.onError(failureMessage);
                        });
                        return;
                    }
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
                });
            } finally {
                operation.unbindWorker();
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

        DownloadOperation operation = beginDownload(listener);
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
                operation.bindWorker();
                apk = copyBundledAsset(assetName, app.packageName + "-bundled.apk", operation, listener);
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
                if (!verifyArtifacts(Collections.singletonList(apk), compatibilityPlan)) {
                    throw new SecurityException(activity.getString(R.string.signature_failed));
                }
                ApkCompatibilityInspector.requireCompatible(
                        activity,
                        compatibilityPlan,
                        Collections.singletonList(apk)
                );
                operation.check();
                File finalApk = apk;
                InstallPlan plan = InspectedInstallPlan.read(activity, compatibilityPlan,
                        Collections.singletonList(finalApk));
                activity.runOnUiThread(() -> {
                    if (hostClosed || activity.isFinishing() || activity.isDestroyed() || !operation.submit()) {
                        cancelPrepared(app, Collections.singletonList(finalApk), operation, listener);
                        return;
                    }
                    finishDownload(operation, listener);
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
                activity.runOnUiThread(() -> {
                    boolean cancelled = operation.cancelled();
                    finishDownload(operation, listener);
                    inFlightPackages.remove(app.packageName);
                    if (cancelled) {
                        pendingInstallStore.clear();
                        listener.onCancelled();
                        return;
                    }
                    String message = exception instanceof DownloadBudget.Failure
                            ? downloadFailure((DownloadBudget.Failure) exception)
                            : activity.getString(R.string.offline_package_failed,
                                    exception.getMessage() == null
                                            ? exception.getClass().getSimpleName() : exception.getMessage());
                    pendingInstallStore.save(app.packageName, app.name,
                            PendingInstallStore.State.FAILED, message);
                    listener.onError(message);
                });
            } finally {
                operation.unbindWorker();
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
        hostClosed = true;
        cancelDownload();
        executor.shutdownNow();
    }

    public boolean cancelDownload() {
        DownloadOperation operation = activeDownload;
        return operation != null && operation.cancel();
    }

    private DownloadOperation beginDownload(Listener listener) {
        DownloadOperation operation = new DownloadOperation();
        activeDownload = operation;
        lastProgressNanos = 0;
        listener.onDownloadStarted();
        return operation;
    }

    private void finishDownload(DownloadOperation operation, Listener listener) {
        if (activeDownload == operation) activeDownload = null;
        operation.finish();
        listener.onDownloadFinished();
    }

    private void cancelPrepared(CatalogApp app, List<File> files, DownloadOperation operation, Listener listener) {
        deleteAll(files);
        finishDownload(operation, listener);
        inFlightPackages.remove(app.packageName);
        pendingInstallStore.clear();
        listener.onCancelled();
    }

    private void postProgress(Listener listener, long received, long total) {
        long now = System.nanoTime();
        if (now - lastProgressNanos < 250_000_000 && received != total) return;
        lastProgressNanos = now;
        activity.runOnUiThread(() -> {
            if (!hostClosed && !activity.isFinishing() && !activity.isDestroyed()) {
                listener.onDownloadProgress(received, total);
            }
        });
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

    private File downloadFolder() throws IOException {
        File parent = activity.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        if (parent == null) parent = activity.getCacheDir();
        if (parent == null) throw new IOException("No writable download folder");
        File folder = new File(parent, "aptelly-installs");
        if (!folder.isDirectory() && !folder.mkdirs()) throw new IOException("Cannot create download folder");
        // Only our abandoned staging files; never touch installed apps or user data.
        File[] stale = folder.listFiles();
        long cutoff = System.currentTimeMillis() - 24L * 60 * 60 * 1000;
        if (stale != null) for (File file : stale) {
            if (file.isFile() && file.lastModified() < cutoff
                    && (file.getName().endsWith(".part") || file.getName().endsWith(".apk") || file.getName().endsWith(".resume"))) {
                deleteQuietly(file);
            }
        }
        return folder;
    }

    private String downloadFailure(DownloadBudget.Failure failure) {
        switch (failure.reason) {
            case SPACE: return activity.getString(R.string.install_download_space);
            case SIZE: return activity.getString(R.string.install_download_size);
            case TIMEOUT: return activity.getString(R.string.install_download_timeout);
            case INTEGRITY: return activity.getString(R.string.signature_failed);
            default: return activity.getString(R.string.install_download_cancelled);
        }
    }

    private File copyBundledAsset(String assetName, String fileName,
                                  DownloadOperation operation, Listener listener) throws IOException {
        File folder = downloadFolder();
        File target = new File(folder, fileName);
        File temporary = new File(folder, fileName + ".part");
        long size = -1;
        try (android.content.res.AssetFileDescriptor descriptor = activity.getAssets().openFd(assetName)) {
            size = descriptor.getLength();
        } catch (IOException compressedAsset) {
            // Compressed assets have no file descriptor. The streaming budget still applies.
        }
        DownloadBudget budget = new DownloadBudget(size);
        budget.progress((received, total) -> postProgress(listener, received, total));
        budget.preflight(folder.getUsableSpace());
        try {
            try (InputStream input = activity.getAssets().open(assetName);
                 FileOutputStream output = new FileOutputStream(temporary)) {
                budget.copy(input, output, size, folder::getUsableSpace, operation::check);
                output.getFD().sync();
            }
            budget.timeoutMillis(1);
            PackageDownload.replace(temporary, target);
            return target;
        } finally {
            deleteQuietly(temporary);
        }
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
        inFlightListeners.put(requestPackageName, listener);
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
                            if (!InstalledTargetVerifier.matches(activity, InstalledTargetVerifier.target(plan))) {
                                pendingInstallStore.save(plan.packageName, plan.appName,
                                        PendingInstallStore.State.FAILED,
                                        activity.getString(R.string.install_target_not_verified));
                                postError(listener, activity.getString(R.string.install_target_not_verified));
                                return;
                            }
                            pendingInstallStore.clear();
                            reportResult(plan, "success", "");
                            activity.runOnUiThread(() -> listener.onInstalled(plan.packageName));
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
            if (!InstalledTargetVerifier.matches(activity, InstalledTargetVerifier.target(plan))) {
                continue;
            }
            Listener completion = inFlightListeners.get(requestPackageName);
            if (!finishTrackedAttempt(requestPackageName, null)) {
                continue;
            }
            pendingInstallStore.clear();
            if (completion != null) completion.onInstalled(plan.packageName);
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
        inFlightListeners.remove(requestPackageName);
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
        // Local source does not upload installation results.
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
