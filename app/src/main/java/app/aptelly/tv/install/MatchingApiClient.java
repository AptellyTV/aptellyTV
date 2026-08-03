package app.aptelly.tv.install;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Build;

import app.aptelly.tv.BuildConfig;
import app.aptelly.tv.catalog.CatalogApp;
import app.aptelly.tv.catalog.InstalledAppResolver;
import app.aptelly.tv.device.DeviceProfilePayload;
import app.aptelly.tv.device.StaticDeviceProfile;
import app.aptelly.tv.device.TelemetryIdentity;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class MatchingApiClient {
    private MatchingApiClient() {
    }

    public static InstallPlan resolve(Context context, CatalogApp app) throws IOException {
        if (BuildConfig.MATCH_API_BASE_URL == null
                || BuildConfig.MATCH_API_BASE_URL.isEmpty()) {
            throw new NoMatchingArtifactException(
                    "MATCHER_NOT_CONFIGURED",
                    "Aptelly matching service is not configured in this build"
            );
        }
        StaticDeviceProfile profile = StaticDeviceProfile.collect(context);
        String correlationId = UUID.randomUUID().toString();
        JSONObject request = new JSONObject();
        String environmentRevision;
        String installationId;
        try {
            request.put("schema_version", DeviceProfilePayload.SCHEMA_VERSION);
            request.put("correlation_id", correlationId);
            request.put("app_id", app.packageName);
            request.put("package_name", app.packageName);
            request.put("client_version", BuildConfig.VERSION_CODE);
            request.put("client_version_name", BuildConfig.VERSION_NAME);
            JSONObject device = DeviceProfilePayload.collect(context);
            environmentRevision = device.getString("environment_revision");
            installationId = device.optString("installation_id", "");
            request.put("device", device);
            String installedPackage = InstalledAppResolver.installedPackage(
                    context,
                    app.packageName
            );
            if (installedPackage != null) {
                PackageInfo info = packageInfo(context, installedPackage);
                JSONObject installed = new JSONObject();
                installed.put("package_name", installedPackage);
                installed.put(
                        "version_code",
                        Build.VERSION.SDK_INT >= 28
                                ? info.getLongVersionCode()
                                : info.versionCode
                );
                installed.put("certificate_sha256", signingCertificate(info));
                request.put("installed", installed);
            }
        } catch (JSONException impossible) {
            throw new IOException("Unable to encode device profile", impossible);
        }

        JSONObject response = postJson(
                BuildConfig.MATCH_API_BASE_URL + "/v1/apps/resolve",
                request
        );
        if (!response.optBoolean("available", false)) {
            throw new NoMatchingArtifactException(
                    response.optString("reason_code", "NO_VERIFIED_ARTIFACT"),
                    response.optString("reason", "No verified build for this television")
            );
        }
        String packageName = response.optString("package_name", "");
        if (!InstalledAppResolver.isAcceptedVariant(app.packageName, packageName)) {
            throw new IOException("Matching service returned a different package");
        }
        String certificate = response.optString("certificate_sha256", "");
        if (!certificate.matches("(?i)^[0-9a-f]{64}$")) {
            throw new IOException("Install plan has no trusted publisher certificate");
        }
        try {
            JSONArray artifactJson = response.getJSONArray("artifacts");
            List<ArtifactFile> artifacts = new ArrayList<>();
            int baseCount = 0;
            for (int index = 0; index < artifactJson.length(); index++) {
                JSONObject item = artifactJson.getJSONObject(index);
                String url = item.getString("url");
                if (!url.startsWith("https://")
                        && !(BuildConfig.ALLOW_CLEARTEXT_TEST
                        && url.startsWith("http://"))) {
                    throw new IOException("Matching service returned a non-HTTPS artifact");
                }
                String kindValue = item.optString("kind");
                ArtifactFile.Kind kind;
                if ("base".equals(kindValue)) {
                    kind = ArtifactFile.Kind.BASE;
                } else if ("split".equals(kindValue)) {
                    kind = ArtifactFile.Kind.SPLIT;
                } else {
                    throw new IOException("Install plan has an invalid artifact kind");
                }
                if (kind == ArtifactFile.Kind.BASE) {
                    baseCount++;
                }
                String name = item.optString("name", "");
                String hash = item.optString("sha256", "");
                if (!name.matches("^[A-Za-z0-9._-]+\\.apk$")
                        || (!hash.isEmpty() && !hash.matches("(?i)^[0-9a-f]{64}$"))) {
                    throw new IOException("Install plan has invalid artifact metadata");
                }
                artifacts.add(ArtifactFile.remote(
                        kind,
                        name,
                        url,
                        hash,
                        item.optLong("size", -1)
                ));
            }
            if (baseCount != 1) {
                throw new IOException("Install plan must contain exactly one base APK");
            }
            return new InstallPlan(
                    app.name,
                    app.packageName,
                    packageName,
                    response.optLong("version_code", 0),
                    response.optString("version_name", ""),
                    certificate,
                    profile.hardwareProfileId,
                    environmentRevision,
                    response.optString("correlation_id", correlationId),
                    response.optString("source", ""),
                    response.optString("release_id", response.optString("version_name", "")),
                    installationId,
                    evidence(response.optString("evidence", "")),
                    artifacts
            );
        } catch (JSONException exception) {
            throw new IOException("Invalid install plan", exception);
        }
    }

    @SuppressWarnings("deprecation")
    private static PackageInfo packageInfo(Context context, String packageName)
            throws IOException {
        int flags = Build.VERSION.SDK_INT >= 28
                ? PackageManager.GET_SIGNING_CERTIFICATES
                : PackageManager.GET_SIGNATURES;
        try {
            return context.getPackageManager().getPackageInfo(packageName, flags);
        } catch (PackageManager.NameNotFoundException exception) {
            throw new IOException("Installed package disappeared during matching", exception);
        }
    }

    @SuppressWarnings("deprecation")
    private static String signingCertificate(PackageInfo info) throws IOException {
        Signature[] signatures = Build.VERSION.SDK_INT >= 28 && info.signingInfo != null
                ? info.signingInfo.getApkContentsSigners()
                : info.signatures;
        if (signatures == null || signatures.length < 1) {
            throw new IOException("Installed package has no signing certificate");
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(signatures[0].toByteArray());
            StringBuilder result = new StringBuilder(64);
            for (byte value : digest) {
                result.append(String.format(Locale.US, "%02x", value & 0xff));
            }
            return result.toString();
        } catch (Exception exception) {
            throw new IOException("Unable to inspect installed signing certificate", exception);
        }
    }

    private static JSONObject postJson(String endpoint, JSONObject request) throws IOException {
        HttpURLConnection connection =
                (HttpURLConnection) new URL(endpoint).openConnection();
        connection.setConnectTimeout(8_000);
        connection.setReadTimeout(12_000);
        connection.setInstanceFollowRedirects(false);
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty(
                "User-Agent",
                "Aptelly/" + BuildConfig.VERSION_NAME + " Android/" + Build.VERSION.SDK_INT
        );
        try {
            try (OutputStream output = connection.getOutputStream()) {
                output.write(request.toString().getBytes(StandardCharsets.UTF_8));
            }
            int status = connection.getResponseCode();
            InputStream stream = status >= 200 && status < 300
                    ? connection.getInputStream()
                    : connection.getErrorStream();
            String body = read(stream);
            if (status < 200 || status >= 300) {
                throw new IOException(
                        "Matching service HTTP " + status
                                + (body.isEmpty() ? "" : ": " + body)
                );
            }
            try {
                return new JSONObject(body);
            } catch (JSONException exception) {
                throw new IOException("Invalid matching service response", exception);
            }
        } finally {
            connection.disconnect();
        }
    }

    public static void report(
            InstallPlan plan,
            String result,
            String failureCode
    ) {
        if (!TelemetryIdentity.validInstallationId(plan.installationId)) {
            return;
        }
        if (BuildConfig.MATCH_API_BASE_URL == null
                || BuildConfig.MATCH_API_BASE_URL.isEmpty()) {
            return;
        }
        try {
            JSONObject event = new JSONObject();
            event.put("schema_version", DeviceProfilePayload.SCHEMA_VERSION);
            event.put("correlation_id", plan.correlationId);
            event.put("catalog_package_name", plan.catalogPackageName);
            event.put("package_name", plan.packageName);
            event.put("actual_package_name", plan.packageName);
            event.put("source_kind", plan.sourceKind);
            event.put("release_id", plan.releaseId);
            event.put("version_code", plan.versionCode);
            event.put("version_name", plan.versionName);
            event.put("profile_id", plan.deviceProfileId);
            event.put("hardware_profile_id", plan.deviceProfileId);
            event.put("environment_revision", plan.environmentRevision);
            event.put("installation_id", plan.installationId);
            event.put("result", result);
            event.put("failure_code", failureCode == null ? "" : failureCode);
            postJson(
                    BuildConfig.MATCH_API_BASE_URL + "/v1/install-events",
                    event
            );
        } catch (Exception ignored) {
            // Telemetry must never change the installation result.
        }
    }

    private static String read(InputStream input) throws IOException {
        if (input == null) {
            return "";
        }
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(input, StandardCharsets.UTF_8)
        )) {
            StringBuilder result = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                result.append(line);
            }
            return result.toString();
        }
    }

    private static InstallPlan.Evidence evidence(String value) {
        if ("physical_device".equals(value)) {
            return InstallPlan.Evidence.PHYSICAL_DEVICE_TESTED;
        }
        if ("device_family".equals(value)) {
            return InstallPlan.Evidence.DEVICE_FAMILY_TESTED;
        }
        return InstallPlan.Evidence.EMULATOR_TESTED;
    }
}
