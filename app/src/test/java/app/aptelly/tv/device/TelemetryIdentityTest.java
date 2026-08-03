package app.aptelly.tv.device;

import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class TelemetryIdentityTest {
    @Test
    public void generatedInstallationIdsAreValidAndDistinct() {
        String first = TelemetryIdentity.newInstallationId();
        String second = TelemetryIdentity.newInstallationId();
        assertTrue(TelemetryIdentity.validInstallationId(first));
        assertTrue(TelemetryIdentity.validInstallationId(second));
        assertNotEquals(first, second);
    }

    @Test
    public void generatedDeletionTokensAreValidAndDistinct() {
        String first = TelemetryIdentity.newDeletionToken();
        String second = TelemetryIdentity.newDeletionToken();
        assertTrue(TelemetryIdentity.validDeletionToken(first));
        assertTrue(TelemetryIdentity.validDeletionToken(second));
        assertNotEquals(first, second);
    }
}
