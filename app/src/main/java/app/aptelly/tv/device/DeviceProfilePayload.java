package app.aptelly.tv.device;

import android.content.Context;

import app.aptelly.tv.BuildConfig;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;

/** Builds the schema-v2 profile exclusively from Android APIs inside Aptelly. */
public final class DeviceProfilePayload {
    public static final int SCHEMA_VERSION = 2;

    private DeviceProfilePayload() {
    }

    public static JSONObject collect(Context context) throws JSONException {
        StaticDeviceProfile stable = StaticDeviceProfile.collect(context);
        DynamicDeviceProfile runtime = DynamicDeviceProfile.collect(context);
        String environmentRevision = environmentRevision(stable, runtime);
        JSONObject device = new JSONObject();
        device.put("schema_version", SCHEMA_VERSION);
        device.put("profile_id", stable.hardwareProfileId);
        device.put("hardware_profile_id", stable.hardwareProfileId);
        device.put("environment_revision", environmentRevision);
        device.put("api", stable.androidApi);
        device.put("android_release", stable.androidRelease);
        device.put("build_id", stable.buildId);
        device.put("manufacturer", stable.manufacturer);
        device.put("brand", stable.brand);
        device.put("model", stable.model);
        device.put("device", stable.device);
        device.put("product", stable.product);
        device.put("fingerprint", stable.buildFingerprint);
        device.put("security_patch", stable.securityPatch);
        device.put("widevine", stable.widevineLevel);
        device.put("television", stable.television);
        device.put("width", stable.widthPixels);
        device.put("height", stable.heightPixels);
        device.put("density_dpi", stable.densityDpi);
        device.put("total_memory_bytes", stable.totalMemoryBytes);
        device.put("available_storage_bytes", runtime.availableStorageBytes);
        device.put("country", Locale.getDefault().getCountry());
        device.put("abis", new JSONArray(stable.supportedAbis));
        device.put("features", new JSONArray(stable.systemFeatures));
        device.put("google_services", runtime.googleServices);
        device.put("google_play", runtime.googlePlay);
        device.put("amazon_store", runtime.amazonStore);
        device.put("aurora_store", runtime.auroraStore);
        device.put("xiaomi_store", runtime.xiaomiStore);
        device.put("package_installer", runtime.packageInstaller);
        device.put("unknown_sources_allowed", runtime.unknownSourcesAllowed);
        device.put("system_webview", runtime.systemWebView);
        device.put("network_status", runtime.networkStatus.kind.name());
        device.put("network_metered", runtime.networkStatus.metered);
        boolean telemetryConsent = TelemetryPreferences.isEnabled(context);
        device.put("telemetry_consent", telemetryConsent);
        if (telemetryConsent) {
            device.put(
                    "installation_id",
                    TelemetryPreferences.installationId(context)
            );
        }
        // Android exposes no trustworthy local Play certification API. Unknown is
        // deliberately not promoted to certified by either client or server.
        device.put(
                "play_certification_status",
                runtime.googlePlay && runtime.googleServices ? "unknown" : "not_available"
        );
        device.put("play_certified", false);
        return device;
    }

    private static String environmentRevision(
            StaticDeviceProfile stable,
            DynamicDeviceProfile runtime
    ) {
        long storageBucket = runtime.availableStorageBytes / (64L * 1024L * 1024L);
        String value = String.join(
                "|",
                stable.environmentRevision,
                Boolean.toString(runtime.googlePlay),
                Boolean.toString(runtime.googleServices),
                Boolean.toString(runtime.amazonStore),
                Boolean.toString(runtime.auroraStore),
                Boolean.toString(runtime.xiaomiStore),
                Boolean.toString(runtime.packageInstaller),
                Boolean.toString(runtime.unknownSourcesAllowed),
                Boolean.toString(runtime.systemWebView),
                runtime.networkStatus.kind.name(),
                Boolean.toString(runtime.networkStatus.metered),
                Long.toString(storageBucket)
        );
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(24);
            for (int index = 0; index < 12; index++) {
                result.append(String.format(Locale.ROOT, "%02x", digest[index] & 0xff));
            }
            return result.toString();
        } catch (Exception ignored) {
            return Integer.toHexString(value.hashCode());
        }
    }

    public static JSONObject envelope(Context context) throws JSONException {
        JSONObject root = new JSONObject();
        root.put("schema_version", SCHEMA_VERSION);
        root.put("client_version_code", BuildConfig.VERSION_CODE);
        root.put("client_version_name", BuildConfig.VERSION_NAME);
        boolean telemetryConsent = TelemetryPreferences.isEnabled(context);
        root.put("telemetry_consent", telemetryConsent);
        root.put("consent_version", TelemetryPreferences.CONSENT_VERSION);
        if (telemetryConsent) {
            root.put("installation_id", TelemetryPreferences.installationId(context));
            root.put("deletion_token", TelemetryPreferences.deletionToken(context));
        }
        root.put("device", collect(context));
        return root;
    }
}
