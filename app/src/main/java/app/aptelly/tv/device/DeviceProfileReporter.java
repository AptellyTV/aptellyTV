package app.aptelly.tv.device;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import app.aptelly.tv.BuildConfig;

import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Non-blocking, explicit opt-in device diagnostics with authenticated deletion. */
public final class DeviceProfileReporter {
    private static final String PREFERENCES = "device_profile_reporter";
    private static final long MAX_AGE_MS = 12L * 60L * 60L * 1000L;
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();

    private DeviceProfileReporter() {
    }

    public interface DeletionCallback {
        void onComplete(boolean deleted);
    }

    public static void reportIfChanged(Context context) {
        Context appContext = context.getApplicationContext();
        EXECUTOR.execute(() -> {
            if (TelemetryPreferences.hasPendingDeletion(appContext)) {
                deletePending(appContext);
                return;
            }
            if (TelemetryPreferences.isEnabled(appContext)) {
                report(appContext);
            }
        });
    }

    public static void enableAndReport(Context context) {
        Context appContext = context.getApplicationContext();
        TelemetryPreferences.enable(appContext);
        appContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                .edit()
                .clear()
                .apply();
        reportIfChanged(appContext);
    }

    public static void disableAndDelete(Context context, DeletionCallback callback) {
        Context appContext = context.getApplicationContext();
        TelemetryPreferences.disableAndQueueDeletion(appContext);
        EXECUTOR.execute(() -> {
            boolean deleted = deletePending(appContext);
            if (callback != null) {
                new Handler(Looper.getMainLooper()).post(
                        () -> callback.onComplete(deleted)
                );
            }
        });
    }

    private static void report(Context context) {
        if (!TelemetryPreferences.isEnabled(context)) {
            return;
        }
        if (BuildConfig.MATCH_API_BASE_URL == null
                || BuildConfig.MATCH_API_BASE_URL.isEmpty()) {
            return;
        }
        try {
            JSONObject payload = DeviceProfilePayload.envelope(context);
            JSONObject device = payload.getJSONObject("device");
            String revision = device.getString("environment_revision");
            SharedPreferences preferences = context.getSharedPreferences(
                    PREFERENCES,
                    Context.MODE_PRIVATE
            );
            long now = System.currentTimeMillis();
            if (revision.equals(preferences.getString("last_revision", ""))
                    && BuildConfig.VERSION_CODE
                    == preferences.getInt("last_client_version_code", -1)
                    && now - preferences.getLong("last_success_at", 0) < MAX_AGE_MS) {
                return;
            }
            post(
                    BuildConfig.MATCH_API_BASE_URL + "/v1/device-profiles",
                    payload.toString()
            );
            preferences.edit()
                    .putString("last_revision", revision)
                    .putInt("last_client_version_code", BuildConfig.VERSION_CODE)
                    .putLong("last_success_at", now)
                    .putString("last_error", "")
                    .apply();
        } catch (Exception exception) {
            context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                    .edit()
                    .putString(
                            "last_error",
                            exception.getClass().getSimpleName()
                                    + ": "
                                    + String.valueOf(exception.getMessage())
                    )
                    .apply();
        }
    }

    private static boolean deletePending(Context context) {
        if (!TelemetryPreferences.hasPendingDeletion(context)) {
            return true;
        }
        if (BuildConfig.MATCH_API_BASE_URL == null
                || BuildConfig.MATCH_API_BASE_URL.isEmpty()) {
            return false;
        }
        try {
            JSONObject body = new JSONObject();
            body.put(
                    "installation_id",
                    TelemetryPreferences.installationId(context)
            );
            body.put(
                    "deletion_token",
                    TelemetryPreferences.deletionToken(context)
            );
            post(
                    BuildConfig.MATCH_API_BASE_URL + "/v1/privacy/delete",
                    body.toString()
            );
            TelemetryPreferences.completeDeletion(context);
            context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                    .edit()
                    .clear()
                    .apply();
            return true;
        } catch (Exception exception) {
            context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                    .edit()
                    .putString(
                            "last_error",
                            "deletion: " + exception.getClass().getSimpleName()
                    )
                    .apply();
            return false;
        }
    }

    private static void post(String endpoint, String body) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(endpoint).openConnection();
        connection.setConnectTimeout(8_000);
        connection.setReadTimeout(12_000);
        connection.setInstanceFollowRedirects(false);
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setRequestProperty("Accept", "application/json");
        try {
            try (OutputStream output = connection.getOutputStream()) {
                output.write(body.getBytes(StandardCharsets.UTF_8));
            }
            int status = connection.getResponseCode();
            InputStream input = status >= 200 && status < 300
                    ? connection.getInputStream()
                    : connection.getErrorStream();
            if (input != null) {
                try (InputStream ignored = input) {
                    byte[] buffer = new byte[2048];
                    while (ignored.read(buffer) >= 0) {
                        // Drain the response so the HTTP connection can be reused.
                    }
                }
            }
            if (status < 200 || status >= 300) {
                throw new IOException("Device profile HTTP " + status);
            }
        } finally {
            connection.disconnect();
        }
    }
}
