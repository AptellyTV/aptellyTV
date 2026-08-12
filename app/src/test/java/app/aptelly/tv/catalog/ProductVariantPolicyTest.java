package app.aptelly.tv.catalog;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ProductVariantPolicyTest {
    @Test
    public void netflixCompatibilityRuntimeHasAProductWideVersionFloor() {
        assertEquals(
                ProductVariantPolicy.Status.UPDATE_REQUIRED,
                ProductVariantPolicy.assess(
                        ProductVariantPolicy.NETFLIX_TV,
                        ProductVariantPolicy.NETFLIX_COMPAT_RUNTIME,
                        35243,
                        false,
                        false
                )
        );
        assertEquals(
                ProductVariantPolicy.Status.READY,
                ProductVariantPolicy.assess(
                        ProductVariantPolicy.NETFLIX_TV,
                        ProductVariantPolicy.NETFLIX_COMPAT_RUNTIME,
                        ProductVariantPolicy.NETFLIX_COMPAT_MIN_VERSION,
                        false,
                        false
                )
        );
        assertTrue(ProductVariantPolicy.isInstallableVariant(
                ProductVariantPolicy.NETFLIX_TV,
                ProductVariantPolicy.NETFLIX_COMPAT_RUNTIME
        ));
    }

    @Test
    public void retiredCrunchyrollRequiresUpgradeEverywhere() {
        assertEquals(
                ProductVariantPolicy.Status.UPDATE_REQUIRED,
                ProductVariantPolicy.assess(
                        ProductVariantPolicy.CRUNCHYROLL,
                        ProductVariantPolicy.CRUNCHYROLL,
                        20203,
                        false,
                        false
                )
        );
        assertEquals(
                ProductVariantPolicy.Status.READY,
                ProductVariantPolicy.assess(
                        ProductVariantPolicy.CRUNCHYROLL,
                        ProductVariantPolicy.CRUNCHYROLL,
                        ProductVariantPolicy.CRUNCHYROLL_MIN_VERSION,
                        false,
                        false
                )
        );
    }

    @Test
    public void discoveryLegacyAlwaysMigratesAndCannotBeDownloadedAgain() {
        assertEquals(
                ProductVariantPolicy.Status.MIGRATION_REQUIRED,
                ProductVariantPolicy.assess(
                        ProductVariantPolicy.DISCOVERY_GOOGLE_TV,
                        ProductVariantPolicy.DISCOVERY_LEGACY_TV,
                        Long.MAX_VALUE,
                        true,
                        true
                )
        );
        assertFalse(ProductVariantPolicy.isInstallableVariant(
                ProductVariantPolicy.DISCOVERY_GOOGLE_TV,
                ProductVariantPolicy.DISCOVERY_LEGACY_TV
        ));
    }

    @Test
    public void discoveryVariantsFollowRuntimeCapabilities() {
        assertEquals(
                ProductVariantPolicy.Status.RUNTIME_REQUIRED,
                ProductVariantPolicy.assess(
                        ProductVariantPolicy.DISCOVERY_GOOGLE_TV,
                        ProductVariantPolicy.DISCOVERY_GOOGLE_TV,
                        1,
                        false,
                        false
                )
        );
        assertEquals(
                ProductVariantPolicy.Status.READY,
                ProductVariantPolicy.assess(
                        ProductVariantPolicy.DISCOVERY_GOOGLE_TV,
                        ProductVariantPolicy.DISCOVERY_GOOGLE_TV,
                        1,
                        true,
                        false
                )
        );
        assertTrue(ProductVariantPolicy.isInstallableVariant(
                ProductVariantPolicy.DISCOVERY_GOOGLE_TV,
                ProductVariantPolicy.DISCOVERY_FIRE_TV
        ));
        assertEquals(
                ProductVariantPolicy.Status.READY,
                ProductVariantPolicy.assess(
                        ProductVariantPolicy.DISCOVERY_GOOGLE_TV,
                        ProductVariantPolicy.DISCOVERY_FIRE_TV,
                        1,
                        false,
                        false
                )
        );
    }
}
