package app.aptelly.tv.device;

import java.security.SecureRandom;
import java.util.Locale;
import java.util.UUID;

/** Generates non-account telemetry credentials stored only in app-private preferences. */
public final class TelemetryIdentity {
    private static final SecureRandom RANDOM = new SecureRandom();

    private TelemetryIdentity() {
    }

    public static String newInstallationId() {
        return UUID.randomUUID().toString().toLowerCase(Locale.ROOT);
    }

    public static String newDeletionToken() {
        byte[] value = new byte[32];
        RANDOM.nextBytes(value);
        StringBuilder result = new StringBuilder(64);
        for (byte item : value) {
            result.append(String.format(Locale.ROOT, "%02x", item & 0xff));
        }
        return result.toString();
    }

    public static boolean validInstallationId(String value) {
        return value != null && value.matches(
                "(?i)^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$"
        );
    }

    public static boolean validDeletionToken(String value) {
        return value != null && value.matches("(?i)^[0-9a-f]{64}$");
    }
}
