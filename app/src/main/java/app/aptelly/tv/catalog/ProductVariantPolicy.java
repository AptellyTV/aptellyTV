package app.aptelly.tv.catalog;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Product-level acceptance policy for packages and versions.
 *
 * A package being installed or launchable is never sufficient evidence that a
 * catalog product is usable.  Every decision must remain portable across TVs:
 * it is based on publisher product identity, supported version and runtime
 * capabilities, never on a device model or a one-off test-set exception.
 */
public final class ProductVariantPolicy {
    public static final String CRUNCHYROLL = "com.crunchyroll.crunchyroid";
    public static final long CRUNCHYROLL_MIN_VERSION = 22350L;

    public static final String NETFLIX_TV = "com.netflix.ninja";
    public static final String NETFLIX_COMPAT_RUNTIME = "com.netflix.mediaclient";
    public static final long NETFLIX_COMPAT_MIN_VERSION = 64348L;

    public static final String DISCOVERY_GOOGLE_TV =
            "com.discovery.discoveryplus.mobile";
    public static final String DISCOVERY_FIRE_TV =
            "com.discovery.discoveryplus.firetv";
    public static final String DISCOVERY_LEGACY_TV =
            "com.discoveryplus.tv.android";

    public enum Status {
        READY,
        UPDATE_REQUIRED,
        MIGRATION_REQUIRED,
        RUNTIME_REQUIRED,
        NOT_PRODUCT_VARIANT
    }

    private ProductVariantPolicy() {
    }

    public static List<String> variants(String catalogPackage) {
        if (NETFLIX_TV.equals(catalogPackage)) {
            return Arrays.asList(NETFLIX_TV, NETFLIX_COMPAT_RUNTIME);
        }
        if (DISCOVERY_GOOGLE_TV.equals(catalogPackage)) {
            return Arrays.asList(
                    DISCOVERY_GOOGLE_TV,
                    DISCOVERY_FIRE_TV,
                    DISCOVERY_LEGACY_TV
            );
        }
        return Collections.singletonList(catalogPackage);
    }

    public static boolean isKnownVariant(String catalogPackage, String actualPackage) {
        return actualPackage != null && variants(catalogPackage).contains(actualPackage);
    }

    /** True only for variants a matching service may install. */
    public static boolean isInstallableVariant(
            String catalogPackage,
            String actualPackage
    ) {
        if (!isKnownVariant(catalogPackage, actualPackage)) return false;
        return !DISCOVERY_LEGACY_TV.equals(actualPackage);
    }

    public static Status assess(
            String catalogPackage,
            String actualPackage,
            long versionCode,
            boolean googleReady,
            boolean amazonRuntimeReady
    ) {
        if (!isKnownVariant(catalogPackage, actualPackage)) {
            return Status.NOT_PRODUCT_VARIANT;
        }
        if (CRUNCHYROLL.equals(catalogPackage)
                && versionCode < CRUNCHYROLL_MIN_VERSION) {
            return Status.UPDATE_REQUIRED;
        }
        if (NETFLIX_TV.equals(catalogPackage)
                && NETFLIX_COMPAT_RUNTIME.equals(actualPackage)
                && versionCode < NETFLIX_COMPAT_MIN_VERSION) {
            return Status.UPDATE_REQUIRED;
        }
        if (DISCOVERY_GOOGLE_TV.equals(catalogPackage)) {
            if (DISCOVERY_LEGACY_TV.equals(actualPackage)) {
                return Status.MIGRATION_REQUIRED;
            }
            if (DISCOVERY_GOOGLE_TV.equals(actualPackage) && !googleReady) {
                return Status.RUNTIME_REQUIRED;
            }
            // A Fire TV artifact is a source/package variant, not proof of an
            // Amazon runtime dependency. The matching artifact records that
            // dependency explicitly and rejects it when the required runtime
            // is absent. This product-level policy only validates identity.
        }
        return Status.READY;
    }
}
