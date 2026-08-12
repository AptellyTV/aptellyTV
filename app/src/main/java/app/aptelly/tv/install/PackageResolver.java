package app.aptelly.tv.install;

import android.os.Build;

import app.aptelly.tv.catalog.CatalogApp;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PackageResolver {
    private static final String CLASH_META_CERT =
            "5f5571af767fe0f611292aa088b445a05f725fb8262fb80a13b69e4d809ae1f1";
    private static final String WIREGUARD_CERT =
            "84a13fa2c4e0064b0c11654b8a86574b7a9b9352a3834cee32455b061c3d4127";
    private static final String TAILSCALE_CERT =
            "5cdb295551bfe1a087fed6acda07141c6c929fa7c29bd273a7092813acc434bf";
    private static final String FDROID_CERT =
            "4cd330fe6593e2e64b1e1fa383f0c6d73892184fc1cd1a909e71d558d862e212";
    private static final String AURORA_STORE_CERT =
            "4c626157ad02bda3401a7263555f68a79663fc3e13a4d4369a12570941aa280f";
    private static final String AIYIFAN_CERT =
            "b119d71050cc11a9303cfd172da1774a8a0bd3f0577f707e11fabaa486568449";

    private static final Pattern WIREGUARD_APK = Pattern.compile(
            "com\\.wireguard\\.android-([0-9.]+)\\.apk"
    );
    private static final Pattern AURORA_STORE_APK = Pattern.compile(
            "AuroraStore-([0-9.]+)\\.apk"
    );
    private static final Pattern TAILSCALE_APK = Pattern.compile(
            "tailscale-android-universal-([0-9.]+)\\.apk"
    );

    private PackageResolver() {
    }

    public static String clashCertificateSha256() {
        return CLASH_META_CERT;
    }

    public static String auroraStoreCertificateSha256() {
        return AURORA_STORE_CERT;
    }

    public static String expectedCertificateSha256(String packageName) {
        if ("tv.ifvod.classic".equals(packageName)) {
            return AIYIFAN_CERT;
        }
        return "";
    }

    public static ResolvedPackage resolve(CatalogApp app) throws IOException {
        if (app.source == CatalogApp.Source.OFFICIAL_CLASH_META) {
            return resolveClashMeta();
        }

        if (app.source == CatalogApp.Source.OFFICIAL_WIREGUARD) {
            String page = get("https://download.wireguard.com/android-client/");
            Matcher matcher = WIREGUARD_APK.matcher(page);
            if (!matcher.find()) {
                throw new IOException("Official WireGuard APK not found");
            }
            String version = matcher.group(1);
            return new ResolvedPackage(
                    "https://download.wireguard.com/android-client/"
                            + "com.wireguard.android-" + version + ".apk",
                    numericVersion(version),
                    version,
                    WIREGUARD_CERT
            );
        }

        if (app.source == CatalogApp.Source.OFFICIAL_TAILSCALE) {
            String page = get("https://pkgs.tailscale.com/stable/");
            Matcher matcher = TAILSCALE_APK.matcher(page);
            if (!matcher.find()) {
                throw new IOException("Official Tailscale APK not found");
            }
            String version = matcher.group(1);
            return new ResolvedPackage(
                    "https://pkgs.tailscale.com/stable/"
                            + "tailscale-android-universal-" + version + ".apk",
                    semanticVersionCode(version),
                    version,
                    TAILSCALE_CERT
            );
        }

        if (app.source == CatalogApp.Source.FDROID_OPENVPN) {
            try {
                JSONObject json = new JSONObject(
                        get("https://f-droid.org/api/v1/packages/" + app.packageName)
                );
                long code = json.getLong("suggestedVersionCode");
                String version = "";
                if (json.getJSONArray("packages").length() > 0) {
                    version = json.getJSONArray("packages")
                            .getJSONObject(0)
                            .optString("versionName", "");
                }
                return new ResolvedPackage(
                        "https://f-droid.org/repo/" + app.packageName + "_" + code + ".apk",
                        code,
                        version,
                        FDROID_CERT
                );
            } catch (JSONException exception) {
                throw new IOException("Invalid F-Droid package metadata", exception);
            }
        }

        if (app.source == CatalogApp.Source.OFFICIAL_AURORA_STORE) {
            String index = get("https://auroraoss.com/api/files");
            Matcher matcher = AURORA_STORE_APK.matcher(index);
            String latest = "";
            while (matcher.find()) {
                String candidate = matcher.group(1);
                if (latest.isEmpty()
                        || semanticVersionCode(candidate) > semanticVersionCode(latest)) {
                    latest = candidate;
                }
            }
            if (latest.isEmpty()) {
                throw new IOException("Official Aurora Store APK not found");
            }
            return new ResolvedPackage(
                    "https://auroraoss.com/downloads/AuroraStore/Release/"
                            + "AuroraStore-" + latest + ".apk",
                    semanticVersionCode(latest),
                    latest,
                    AURORA_STORE_CERT
            );
        }

        throw new IOException("This source does not expose a direct APK");
    }

    public static InstallPlan resolvePlan(
            CatalogApp app,
            String deviceProfileId
    ) throws IOException {
        ResolvedPackage resolved = resolve(app);
        ArtifactFile artifact = ArtifactFile.remote(
                ArtifactFile.Kind.BASE,
                app.packageName + "-base.apk",
                resolved.downloadUrl,
                "",
                -1
        );
        return new InstallPlan(
                app.name,
                app.packageName,
                resolved.versionCode,
                resolved.versionName,
                resolved.expectedCertificateSha256,
                deviceProfileId,
                InstallPlan.Evidence.EMULATOR_TESTED,
                Collections.singletonList(artifact)
        );
    }

    private static ResolvedPackage resolveClashMeta() throws IOException {
        try {
            JSONObject release = new JSONObject(get(
                    "https://api.github.com/repos/MetaCubeX/ClashMetaForAndroid/releases/latest"
            ));
            String version = release.optString("tag_name", "").replaceFirst("^v", "");
            JSONArray assets = release.getJSONArray("assets");
            String preferredAbi = preferredAbi();
            String preferredSuffix = "-meta-" + preferredAbi + "-release.apk";
            String universalSuffix = "-meta-universal-release.apk";
            String downloadUrl = "";

            for (int index = 0; index < assets.length(); index++) {
                JSONObject asset = assets.getJSONObject(index);
                String name = asset.optString("name", "");
                if (name.endsWith(preferredSuffix)) {
                    downloadUrl = asset.optString("browser_download_url", "");
                    break;
                }
                if (downloadUrl.isEmpty() && name.endsWith(universalSuffix)) {
                    downloadUrl = asset.optString("browser_download_url", "");
                }
            }
            if (version.isEmpty() || downloadUrl.isEmpty()) {
                throw new IOException("Official Clash Meta APK not found");
            }
            return new ResolvedPackage(
                    downloadUrl,
                    semanticVersionCode(version),
                    version + ".Meta",
                    CLASH_META_CERT
            );
        } catch (JSONException exception) {
            throw new IOException("Invalid Clash Meta release metadata", exception);
        }
    }

    private static String preferredAbi() {
        for (String abi : Build.SUPPORTED_ABIS) {
            if ("arm64-v8a".equals(abi)
                    || "armeabi-v7a".equals(abi)
                    || "x86_64".equals(abi)
                    || "x86".equals(abi)) {
                return abi;
            }
        }
        return "universal";
    }

    private static String get(String url) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(15_000);
        connection.setReadTimeout(30_000);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("User-Agent", "Aptelly/0.1");
        connection.setRequestProperty("Accept", "application/vnd.github+json");
        try {
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) {
                throw new IOException("HTTP " + status);
            }
            try (InputStream input = connection.getInputStream();
                 BufferedReader reader = new BufferedReader(
                         new InputStreamReader(input, StandardCharsets.UTF_8)
                 )) {
                StringBuilder result = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    result.append(line).append('\n');
                }
                return result.toString();
            }
        } finally {
            connection.disconnect();
        }
    }

    private static long numericVersion(String value) {
        String digits = value.replaceAll("[^0-9]", "");
        if (digits.length() > 15) {
            digits = digits.substring(0, 15);
        }
        try {
            return Long.parseLong(digits);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static long semanticVersionCode(String value) {
        String[] parts = value.split("[^0-9]+");
        long major = numberAt(parts, 0);
        long minor = numberAt(parts, 1);
        long patch = numberAt(parts, 2);
        return major * 100_000L + minor * 1_000L + patch;
    }

    private static long numberAt(String[] values, int index) {
        if (index >= values.length || values[index].isEmpty()) {
            return 0;
        }
        try {
            return Long.parseLong(values[index]);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }
}
