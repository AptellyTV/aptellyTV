package app.aptelly.tv.install;
import app.aptelly.tv.R;
import org.junit.Test;
import static org.junit.Assert.*;
public class InstallGuidanceTest {
    @Test public void mapsSafetyAndSignerFailuresToDifferentActions() {
        assertEquals(R.string.match_security_blocked, InstallGuidance.resource("ARTIFACT_SECURITY_REJECTED"));
        assertEquals(R.string.match_signer_blocked, InstallGuidance.resource("INSTALLED_SIGNATURE_UNKNOWN"));
    }
    @Test public void distinguishesStaleEvidenceFromMissingFunctions() {
        assertEquals(R.string.match_qualification_expired, InstallGuidance.resource("CAPABILITY_QUALIFICATION_EXPIRED"));
        assertEquals(R.string.match_function_not_qualified, InstallGuidance.resource("ARTIFACT_QUALIFICATION_MISMATCH"));
        assertEquals(R.string.match_function_not_qualified, InstallGuidance.resource("HD_PLAYBACK_NOT_QUALIFIED"));
    }
    @Test public void mapsEnvironmentAndSourceFailuresToExecutableActions() {
        assertEquals(R.string.match_google_required, InstallGuidance.resource("GMS_MISSING"));
        assertEquals(R.string.match_amazon_required, InstallGuidance.resource("AMAZON_RUNTIME_MISSING"));
        assertEquals(R.string.match_region_required, InstallGuidance.resource("REGION_MISMATCH"));
        assertEquals(R.string.match_device_unsupported, InstallGuidance.resource("API_TOO_LOW"));
        assertEquals(R.string.match_package_unavailable, InstallGuidance.resource("INCOMPLETE_INSTALL_SET"));
        assertEquals(R.string.match_no_source, InstallGuidance.resource("NO_VERIFIED_ARTIFACT"));
    }
}
