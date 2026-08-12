package app.aptelly.tv.update;

import org.json.JSONObject;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Locale;

public final class AptellyUpdateVerifier {
    public static final String RELEASE_CERTIFICATE_SHA256 =
            "e9ec43199ab7c9872328037ada9f5ce08d367df25aef8732e015b06ea28be375";
    private static final String RELEASE_PUBLIC_KEY_BASE64 =
            "MIICIjANBgkqhkiG9w0BAQEFAAOCAg8AMIICCgKCAgEAhaIEcL7Vpe+L3yK4CsNkPpZ8OZMjLU6JjSLgeZNvR+eA/D1NnV89EJrkaZ/xsnPYXvdRabbGilE5aR+loUpW3SGG+c9PScSQfkrMiQlgSXg2oLrQLTR2plOoI5Mvpw8g5XxhuLDBmTZpN972rJSQ/+kRIvvm8nJP+5djh3RN4OJfmvtVfhS5y/QiJyTaLniM459QJ1wP1ud5wGwClBmev94srv+VDwC/2/LCC79bH6hJK+cXmq2BkA55A4TAT6Pw64NmSEdZ8iZMUTu9k7tZ8H6SvlqD2oUsNGGTSsxHqiFlJcsNYVCf41yExbv9E8kZ6Oq4/9XstMozeG+3E1CfYBOgbMOCO+937LKW/B40En0cpGqnk0i6w+K0Akk024h6N+bjRnhP7lnEs76umDcE612mn14uANCaW6XtbU5tTIifVOhTjPiiMCo7EKuSOqhxevaMDY+ADLqx3JrXD2PS++UylwiSVhCm7vExRNy/uvRJ0LjH/6acMO5rOh1O18lf27A++yZLUx+7lTsQaJ6IEy5y3usB6ofI5uPBDI7malmICaIw4WW0bn0T2UR1FsOaiCYDsZDYKrLzu95hlLeJl7oFiT1sL3IjENx9436KktyHJ/ENxk7oLn3vLWLXsfSRARqyJk3KTQL0JgY+D/ZMYso2GDiqWn/tdYPkKnbpDhMCAwEAAQ==";
    private static final int MAX_PAYLOAD_BYTES = 32 * 1024;

    private AptellyUpdateVerifier() {}

    public static AptellyUpdateManifest verify(String envelopeJson) throws Exception {
        JSONObject envelope = new JSONObject(envelopeJson);
        byte[] payload = Base64.getDecoder().decode(envelope.getString("payload"));
        byte[] signatureBytes = Base64.getDecoder().decode(envelope.getString("signature"));
        if (payload.length == 0 || payload.length > MAX_PAYLOAD_BYTES) {
            throw new SecurityException("Invalid update payload size");
        }
        if (!verifySignature(payload, signatureBytes)) {
            throw new SecurityException("Update manifest signature mismatch");
        }

        JSONObject value = new JSONObject(new String(payload, StandardCharsets.UTF_8));
        if (!"app.aptelly.tv".equals(value.getString("packageName"))) {
            throw new SecurityException("Update package mismatch");
        }
        if (!RELEASE_CERTIFICATE_SHA256.equalsIgnoreCase(
                value.getString("signingCertificateSha256")
        )) {
            throw new SecurityException("Update certificate pin mismatch");
        }
        int versionCode = value.getInt("versionCode");
        String versionName = value.getString("versionName").trim();
        int minSdk = value.getInt("minSdk");
        long sizeBytes = value.getLong("sizeBytes");
        String apkUrl = value.getString("apkUrl").trim();
        String sha256 = value.getString("sha256").toLowerCase(Locale.ROOT);
        URI uri = URI.create(apkUrl);
        if (versionCode <= 0 || versionName.isEmpty() || minSdk < 26) {
            throw new SecurityException("Invalid update version metadata");
        }
        if (sizeBytes <= 0 || sizeBytes > AptellyUpdateClient.MAX_APK_BYTES) {
            throw new SecurityException("Invalid update file size");
        }
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) {
            throw new SecurityException("Update URL must use HTTPS");
        }
        if (!sha256.matches("[0-9a-f]{64}")) {
            throw new SecurityException("Invalid update SHA-256");
        }
        return new AptellyUpdateManifest(
                versionCode,
                versionName,
                minSdk,
                apkUrl,
                sizeBytes,
                sha256,
                value.optBoolean("mandatory", false)
        );
    }

    private static PublicKey releasePublicKey() throws Exception {
        byte[] encoded = Base64.getDecoder().decode(RELEASE_PUBLIC_KEY_BASE64);
        return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(encoded));
    }

    static boolean verifySignature(byte[] payload, byte[] signatureBytes) throws Exception {
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initVerify(releasePublicKey());
        signature.update(payload);
        return signature.verify(signatureBytes);
    }
}
