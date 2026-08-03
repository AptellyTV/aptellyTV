package app.aptelly.tv.device;

import android.content.Context;
import android.content.SharedPreferences;

/** Explicit opt-in state and private deletion credentials for anonymous diagnostics. */
public final class TelemetryPreferences {
    public static final int CONSENT_VERSION = 1;
    private static final String PREFERENCES = "telemetry_privacy";
    private static final String ENABLED = "enabled";
    private static final String INSTALLATION_ID = "installation_id";
    private static final String DELETION_TOKEN = "deletion_token";
    private static final String PENDING_DELETION = "pending_deletion";

    private TelemetryPreferences() {
    }

    public static boolean isEnabled(Context context) {
        return preferences(context).getBoolean(ENABLED, false);
    }

    public static synchronized void enable(Context context) {
        SharedPreferences preferences = preferences(context);
        String installationId = preferences.getString(INSTALLATION_ID, "");
        String deletionToken = preferences.getString(DELETION_TOKEN, "");
        if (!TelemetryIdentity.validInstallationId(installationId)) {
            installationId = TelemetryIdentity.newInstallationId();
        }
        if (!TelemetryIdentity.validDeletionToken(deletionToken)) {
            deletionToken = TelemetryIdentity.newDeletionToken();
        }
        preferences.edit()
                .putBoolean(ENABLED, true)
                .putString(INSTALLATION_ID, installationId)
                .putString(DELETION_TOKEN, deletionToken)
                .putBoolean(PENDING_DELETION, false)
                .apply();
    }

    public static void disableAndQueueDeletion(Context context) {
        preferences(context).edit()
                .putBoolean(ENABLED, false)
                .putBoolean(PENDING_DELETION, true)
                .apply();
    }

    public static boolean hasPendingDeletion(Context context) {
        return preferences(context).getBoolean(PENDING_DELETION, false)
                && TelemetryIdentity.validInstallationId(installationId(context))
                && TelemetryIdentity.validDeletionToken(deletionToken(context));
    }

    public static String installationId(Context context) {
        return preferences(context).getString(INSTALLATION_ID, "");
    }

    public static String deletionToken(Context context) {
        return preferences(context).getString(DELETION_TOKEN, "");
    }

    public static void completeDeletion(Context context) {
        preferences(context).edit()
                .remove(INSTALLATION_ID)
                .remove(DELETION_TOKEN)
                .putBoolean(PENDING_DELETION, false)
                .putBoolean(ENABLED, false)
                .apply();
    }

    private static SharedPreferences preferences(Context context) {
        return context.getApplicationContext().getSharedPreferences(
                PREFERENCES,
                Context.MODE_PRIVATE
        );
    }
}
