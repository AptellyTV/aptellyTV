package app.aptelly.tv.device;

import org.json.JSONArray;
import org.json.JSONObject;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

/** Stable software/capability identity. Space, network and install permissions are request conditions. */
public final class EnvironmentRevision {
    private EnvironmentRevision() { }
    public static String calculate(JSONObject device) {
        String[] keys = {"hardware_profile_id", "api", "android_release", "build_id", "fingerprint",
                "security_patch", "widevine", "features", "google_services", "google_play",
                "amazon_store", "aurora_store", "xiaomi_store", "package_installer", "system_webview",
                "reported_media_capabilities", "runtime_versions"};
        StringBuilder input = new StringBuilder();
        for (String key : keys) input.append(key).append('=').append(canonical(device.opt(key))).append('\n');
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(input.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder();
            for (int i = 0; i < 12; i++) result.append(String.format(Locale.ROOT, "%02x", hash[i] & 0xff));
            return result.toString();
        } catch (Exception impossible) { throw new IllegalStateException("SHA-256 unavailable", impossible); }
    }
    private static String canonical(Object value) {
        if (value instanceof JSONObject) {
            JSONObject object = (JSONObject) value;
            List<String> keys = new ArrayList<>();
            Iterator<String> iterator = object.keys();
            while (iterator.hasNext()) keys.add(iterator.next());
            Collections.sort(keys);
            StringBuilder result = new StringBuilder("{");
            for (String key : keys) result.append(JSONObject.quote(key)).append(':').append(canonical(object.opt(key))).append(',');
            return result.append('}').toString();
        }
        if (value instanceof JSONArray) {
            JSONArray array = (JSONArray) value;
            List<String> items = new ArrayList<>();
            for (int i = 0; i < array.length(); i++) items.add(canonical(array.opt(i)));
            Collections.sort(items);
            return items.toString();
        }
        if (value == null || value == JSONObject.NULL) return "null";
        if (value instanceof String) return JSONObject.quote((String) value);
        return value.toString();
    }
}
