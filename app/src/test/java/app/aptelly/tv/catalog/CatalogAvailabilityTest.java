package app.aptelly.tv.catalog;

import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

public class CatalogAvailabilityTest {
    @Before public void reset() { CatalogAvailability.configure("test", false); CatalogAvailability.invalidate(); }
    private JSONObject snapshot(boolean visible) throws Exception {
        return new JSONObject("{\"default_visible\":true,\"overrides\":[{\"package_name\":\"test.app\",\"visible\":"
                + visible + ",\"reason_code\":\"REGION_MISMATCH\",\"usage_gate\":\"account\",\"next_action\":\"sign in\"}]}");
    }
    @Test public void replacesSnapshotAndRestoresLocalFloor() throws Exception {
        CatalogAvailability.configure("xiaomi", true);
        long ticket = CatalogAvailability.beginRequest("a");
        CatalogAvailability.apply(snapshot(false), ticket, 100);
        assertFalse(CatalogAvailability.isVisible("test.app", 101));
        ticket = CatalogAvailability.beginRequest("a");
        CatalogAvailability.apply(new JSONObject("{\"overrides\":[]}"), ticket, 102);
        assertTrue(CatalogAvailability.isVisible("test.app", 103));
        assertFalse(CatalogAvailability.isVisible("com.aurora.store", 103));
    }
    @Test public void revokedPositiveOverrideDoesNotOverrideLocalGate() throws Exception {
        CatalogAvailability.configure("xiaomi", true);
        long ticket = CatalogAvailability.beginRequest("a");
        CatalogAvailability.apply(new JSONObject("{\"overrides\":[{\"package_name\":\"com.aurora.store\",\"visible\":true}]}"), ticket, 100);
        assertTrue(CatalogAvailability.isVisible("com.aurora.store", 101));
        ticket = CatalogAvailability.beginRequest("a");
        CatalogAvailability.apply(new JSONObject("{\"overrides\":[]}"), ticket, 102);
        assertFalse(CatalogAvailability.isVisible("com.aurora.store", 103));
    }
    @Test public void keepsFunctionAndNextActionEvenWhenVisibilityDoesNotChange() throws Exception {
        long ticket = CatalogAvailability.beginRequest("a");
        CatalogAvailability.apply(snapshot(true), ticket);
        assertEquals("REGION_MISMATCH", CatalogAvailability.status("test.app").reasonCode);
        assertEquals("account", CatalogAvailability.status("test.app").usageGate);
        assertEquals("sign in", CatalogAvailability.status("test.app").nextAction);
        ticket = CatalogAvailability.beginRequest("a");
        assertTrue(CatalogAvailability.apply(new JSONObject("{\"overrides\":[{\"package_name\":\"test.app\",\"visible\":true,\"function_result\":\"fail\"}]}"), ticket));
        assertEquals("fail", CatalogAvailability.status("test.app").functionResult);
    }
    @Test public void ttlExpiresCloudOverrides() throws Exception {
        long ticket = CatalogAvailability.beginRequest("a");
        CatalogAvailability.apply(snapshot(false), ticket, 100);
        assertFalse(CatalogAvailability.isVisible("test.app", 299999));
        assertTrue(CatalogAvailability.isVisible("test.app", 300100));
    }
    @Test public void rejectsResponsesFromOldEnvironmentAndOutOfOrderRequests() throws Exception {
        long old = CatalogAvailability.beginRequest("a");
        long current = CatalogAvailability.beginRequest("b");
        assertFalse(CatalogAvailability.apply(snapshot(false), old, 100));
        assertTrue(CatalogAvailability.isVisible("test.app", 101));
        CatalogAvailability.apply(snapshot(false), current, 102);
        long later = CatalogAvailability.beginRequest("b");
        assertFalse(CatalogAvailability.apply(snapshot(true), current, 103));
        CatalogAvailability.apply(snapshot(true), later, 104);
        assertTrue(CatalogAvailability.isVisible("test.app", 105));
    }
    @Test public void rejectsMalformedPartialSnapshots() throws Exception {
        long ticket = CatalogAvailability.beginRequest("a");
        CatalogAvailability.apply(snapshot(false), ticket, 100);
        ticket = CatalogAvailability.beginRequest("a");
        assertFalse(CatalogAvailability.apply(new JSONObject("{\"overrides\":[null]}"), ticket, 102));
        assertFalse(CatalogAvailability.isVisible("test.app", 103));
    }
}
