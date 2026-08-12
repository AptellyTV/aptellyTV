package app.aptelly.tv.catalog;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class AppCompatibilityTest {
    @Test
    public void fuboRequiresGoogleRuntime() {
        CatalogApp fubo = new CatalogApp(
                "Fubo",
                0,
                "tv.fubo.mobile",
                0,
                0,
                0,
                CatalogApp.Source.PLAY_STORE,
                true,
                true,
                true,
                null
        );

        assertTrue(AppCompatibility.requiresGoogleRuntime(fubo));
    }
}
