package app.aptelly.tv.install;
import org.junit.Test;
import static org.junit.Assert.*;

public class InstallTargetTest {
    private final String signer = "a".repeat(64);
    @Test public void cancelledUpdateLeavingOldPackageIsNotSuccess() {
        assertFalse(new InstallTarget("test.app", 20, signer, 0).matches("test.app", 19, signer, 0));
    }
    @Test public void requiresExactPackageAndSigner() {
        InstallTarget target = new InstallTarget("test.app", 20, signer, 0);
        assertFalse(target.matches("other.app", 20, signer, 0));
        assertFalse(target.matches("test.app", 20, "b".repeat(64), 0));
        assertTrue(target.matches("test.app", 20, signer.toUpperCase(), 0));
    }
    @Test public void checksSplitsAndAllowsPublisherNewerRelease() {
        InstallTarget target = new InstallTarget("test.app", 20, signer, 2);
        assertFalse(target.matches("test.app", 20, signer, 1));
        assertTrue(target.matches("test.app", 20, signer, 2));
        assertTrue(target.matches("test.app", 21, signer, 0));
    }
    @Test public void legacyUnknownTargetsCannotSucceed() {
        assertFalse(new InstallTarget("test.app", 0, "", 0).matches("test.app", 20, signer, 0));
    }
    @Test public void bundleMustReadRealArchiveCodeAndKnownPlansMustMatchIt() {
        assertTrue(InstallTarget.acceptsArchive("test.app", 0, "test.app", 20));
        assertTrue(InstallTarget.acceptsArchive("test.app", 20, "test.app", 20));
        assertFalse(InstallTarget.acceptsArchive("test.app", 20, "test.app", 19));
        assertFalse(InstallTarget.acceptsArchive("test.app", 0, "other.app", 20));
        assertFalse(InstallTarget.acceptsArchive("test.app", 0, "test.app", 0));
    }
    @Test public void updateUsesCodeAndKeepsProductMigrationsDistinct() {
        assertTrue(InstallTarget.requiresInstall("test.app", 20, "test.app", 21));
        assertFalse(InstallTarget.requiresInstall("test.app", 21, "test.app", 20));
        assertFalse(InstallTarget.requiresInstall("test.app", 20, "test.app", 20));
        assertTrue(InstallTarget.requiresInstall("test.app", 2000, "compat.app", 1));
    }
}
