package app.aptelly.tv.install;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.os.Build;
import android.util.Log;

import app.aptelly.tv.PrimeVideoShortcutController;
import app.aptelly.tv.TvAppShortcutController;

public final class InstallStatusReceiver extends BroadcastReceiver {
    private static final String LOG_TAG = "AptellyInstaller";
    static final String EXTRA_REQUEST_ID = "request_id";

    @Override
    public void onReceive(Context context, Intent intent) {
        String requestId = intent.getStringExtra(EXTRA_REQUEST_ID);
        if (requestId == null) {
            return;
        }
        int status = intent.getIntExtra(
                PackageInstaller.EXTRA_STATUS,
                PackageInstaller.STATUS_FAILURE
        );
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            Intent confirmation;
            if (Build.VERSION.SDK_INT >= 33) {
                confirmation = intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent.class);
            } else {
                //noinspection deprecation
                confirmation = intent.getParcelableExtra(Intent.EXTRA_INTENT);
            }
            if (confirmation == null) {
                InstallSessionRegistry.failure(requestId, "Installer confirmation unavailable");
                return;
            }
            confirmation.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (!startConfirmation(context, confirmation)) {
                InstallSessionRegistry.failure(
                        requestId,
                        "Installer confirmation activity unavailable"
                );
            }
            return;
        }
        if (status == PackageInstaller.STATUS_SUCCESS) {
            InstallSessionRegistry.success(requestId);
            PrimeVideoShortcutController.sync(context);
            TvAppShortcutController.sync(context);
            return;
        }
        String message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
        InstallSessionRegistry.failure(
                requestId,
                statusName(status)
                        + (message == null || message.isEmpty() ? "" : ": " + message)
        );
    }

    /**
     * Some TV ROMs ship a PackageInstaller whose InstallStart activity is present but whose
     * manifest omits the hidden CONFIRM_INSTALL intent filter. The framework still returns an
     * implicit package-scoped confirmation intent, so startActivity() throws even though the
     * installer UI exists. Retry that same, framework-issued intent against InstallStart
     * explicitly. Never let a broken OEM installer intent crash the launcher process.
     */
    private static boolean startConfirmation(Context context, Intent confirmation) {
        try {
            context.startActivity(confirmation);
            return true;
        } catch (RuntimeException firstFailure) {
            Log.w(LOG_TAG, "Implicit installer confirmation unavailable", firstFailure);
        }

        String installerPackage = confirmation.getPackage();
        if (installerPackage == null || installerPackage.isEmpty()) {
            return false;
        }
        Intent explicit = new Intent(confirmation);
        // Android 11's PackageInstaller uses CONFIRM_PERMISSIONS. Some Xiaomi TV builds run a
        // newer framework which emits CONFIRM_INSTALL while retaining the older installer app.
        // Translate only in this fallback path; the session id and all framework extras stay
        // unchanged.
        if ("android.content.pm.action.CONFIRM_INSTALL".equals(explicit.getAction())) {
            explicit.setAction("android.content.pm.action.CONFIRM_PERMISSIONS");
        }
        explicit.setComponent(new ComponentName(
                installerPackage,
                installerPackage + ".InstallStart"
        ));
        // Xiaomi TV keeps the completed InstallInstalling activity as the root of the
        // package-installer task. Reusing that task for the next confirmation brings the stale
        // progress screen to the front and leaves the new session waiting forever. The fallback
        // is entered only for this broken OEM confirmation route, so clear that installer-owned
        // task before starting the framework-issued session confirmation.
        explicit.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        try {
            context.startActivity(explicit);
            Log.i(LOG_TAG, "Opened OEM installer confirmation via explicit InstallStart");
            return true;
        } catch (RuntimeException secondFailure) {
            Log.e(LOG_TAG, "Explicit installer confirmation unavailable", secondFailure);
            return false;
        }
    }

    private static String statusName(int status) {
        switch (status) {
            case PackageInstaller.STATUS_FAILURE_ABORTED:
                return "INSTALL_ABORTED";
            case PackageInstaller.STATUS_FAILURE_BLOCKED:
                return "INSTALL_BLOCKED";
            case PackageInstaller.STATUS_FAILURE_CONFLICT:
                return "INSTALL_CONFLICT_OR_SIGNATURE_MISMATCH";
            case PackageInstaller.STATUS_FAILURE_INCOMPATIBLE:
                return "INSTALL_INCOMPATIBLE_API_ABI_OR_FEATURE";
            case PackageInstaller.STATUS_FAILURE_INVALID:
                return "INSTALL_INVALID_OR_MISSING_SPLIT";
            case PackageInstaller.STATUS_FAILURE_STORAGE:
                return "INSTALL_STORAGE";
            default:
                return "INSTALL_FAILURE_" + status;
        }
    }
}
