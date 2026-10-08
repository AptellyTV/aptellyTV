package app.aptelly.tv.install;

import android.content.SharedPreferences;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.*;

public class PendingInstallStoreTest {
    private SharedPreferences preferences() {
        Map<String, Object> persisted = new HashMap<>();
        SharedPreferences.Editor editor = (SharedPreferences.Editor) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{SharedPreferences.Editor.class},
                (proxy, method, args) -> {
                    if (method.getName().startsWith("put")) persisted.put((String) args[0], args[1]);
                    if (method.getName().equals("clear")) persisted.clear();
                    if (method.getName().equals("commit")) return true;
                    if (method.getName().equals("apply")) return null;
                    return proxy;
                });
        return (SharedPreferences) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{SharedPreferences.class}, (proxy, method, args) -> {
                    if (method.getName().equals("edit")) return editor;
                    if (method.getName().startsWith("get")) return persisted.getOrDefault(args[0], args[1]);
                    throw new UnsupportedOperationException(method.getName());
                });
    }
    private InstallPlan plan() {
        return new InstallPlan("Test", "test.app", 20, "1.0", "a".repeat(64), "",
                InstallPlan.Evidence.DEVICE_FAMILY_TESTED,
                List.of(ArtifactFile.remote(ArtifactFile.Kind.BASE, "base.apk", "https://test.invalid/base.apk", "b".repeat(64), 100)));
    }
    @Test public void targetAndResultSurviveStoreRecreation() {
        SharedPreferences prefs = preferences();
        PendingInstallStore first = new PendingInstallStore(prefs);
        first.save("test.app", "Test", PendingInstallStore.State.WAITING_CONFIRMATION, "");
        first.attachSession(plan(), 42, "request");
        PendingInstallStore recreated = new PendingInstallStore(prefs);
        assertEquals(20, recreated.read().target.versionCode);
        assertEquals("a".repeat(64), recreated.read().target.certificateSha256);
        assertEquals(42, recreated.read().sessionId);
        assertTrue(recreated.read().expectedFiles.contains("base.apk"));
        recreated.recordResult("request", 42, PendingInstallStore.State.FAILED, "INSTALL_ABORTED");
        assertEquals(PendingInstallStore.State.FAILED, new PendingInstallStore(prefs).read().state);
        assertEquals("INSTALL_ABORTED", recreated.read().message);
    }
    @Test public void staleCallbackCannotOverwriteNewTask() {
        PendingInstallStore store = new PendingInstallStore(preferences());
        store.save("test.app", "Test", PendingInstallStore.State.WAITING_CONFIRMATION, "");
        store.attachSession(plan(), 42, "request");
        store.recordResult("old-request", 42, PendingInstallStore.State.FAILED, "bad");
        store.recordResult("request", 41, PendingInstallStore.State.FAILED, "bad");
        assertEquals(PendingInstallStore.State.WAITING_CONFIRMATION, store.read().state);
    }
    @Test public void nextAttemptClearsOldTargetAndOldSuccess() {
        PendingInstallStore store = new PendingInstallStore(preferences());
        store.save("test.app", "Test", PendingInstallStore.State.WAITING_CONFIRMATION, "");
        store.attachSession(plan(), 42, "request");
        store.recordResult("request", 42, PendingInstallStore.State.SUCCEEDED, "");
        store.save("test.app", "Test", PendingInstallStore.State.REQUESTED, "");
        assertEquals(0, store.read().target.versionCode);
        assertEquals(-1, store.read().sessionId);
        assertEquals(PendingInstallStore.State.REQUESTED, store.read().state);
    }
}
