package app.aptelly.tv.catalog;

import app.aptelly.tv.device.DeviceProfile;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Device-scoped visibility gate populated by the cloud compatibility registry. */
public final class CatalogAvailability {
    private static final Set<String> XIAOMI_MFTR0_PENDING = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList(
                    "com.google.android.videos", "com.android.chrome", "com.tubitv",
                    "com.aurora.store", "com.netflix.ninja",
                    "com.hulu.livingroomplus", "com.peacocktv.peacockandroid",
                    "com.viki.android", "net.mbc.shahidTV",
                    "com.apple.atve.androidtv.appletv", "com.univision.prendetv",
                    "com.globo.globotv", "com.sonyliv", "com.vuclip.viu",
                    "com.graymatrix.did", "com.plexapp.android",
                    "com.recipe.filmrise", "com.future.moviesByFawesomeAndroidTV",
                    "com.crunchyroll.crunchyroid", "jp.co.rakuten.channel.tv.google",
                    "in.startv.hotstar", "tv.emby.embyatv",
                    "com.valvesoftware.steamlink", "com.wbd.stream",
                    "com.google.android.apps.youtube.unplugged",
                    "com.discovery.discoveryplus.mobile", "com.sling",
                    "tv.fubo.mobile", "com.espn.score_center",
                    "com.formulaone.production", "com.mubi", "com.britbox.tv",
                    "com.iqiyi.i18n.tv", "com.cibn.tv", "com.gitvdemo.video",
                    "com.ktcp.video", "com.hunantv.license", "com.tencent.qqmusictv"
            ))
    );

    private static final long TTL_MS = 5L * 60L * 1000L;
    private static final Set<String> LOCAL_HIDDEN = new HashSet<>();
    private static java.util.Map<String, Status> snapshot = Collections.emptyMap();
    private static String profileKey = "";
    private static String requestEnvironment = "";
    private static long generation;
    private static long receivedAt;
    private static boolean defaultVisible = true;

    /** Preserve the whole response so UI can explain install and usage limitations separately. */
    public static final class Status {
        public final boolean visible;
        public final String state, reasonCode, detail, functionResult, usageGate, nextAction;
        public final String installBlocker, currentDeviceFit, publisherTvStatus;
        public final String installResult, launchResult, remoteResult, evidenceUri, testedAt, nextReviewAt;
        private final String serialized;

        Status(JSONObject item) {
            visible = item.optBoolean("visible", false);
            state = item.optString("state");
            reasonCode = item.optString("reason_code");
            detail = item.optString("detail");
            functionResult = item.optString("function_result");
            usageGate = item.optString("usage_gate");
            nextAction = item.optString("next_action");
            installBlocker = item.optString("install_blocker");
            currentDeviceFit = item.optString("current_device_fit");
            publisherTvStatus = item.optString("publisher_tv_status");
            installResult = item.optString("install_result");
            launchResult = item.optString("launch_result");
            remoteResult = item.optString("remote_result");
            evidenceUri = item.optString("evidence_uri");
            testedAt = item.optString("tested_at");
            nextReviewAt = item.optString("next_review_at");
            serialized = item.toString();
        }
        @Override public boolean equals(Object other) {
            return other instanceof Status && serialized.equals(((Status) other).serialized);
        }
        @Override public int hashCode() { return serialized.hashCode(); }
    }

    private CatalogAvailability() { }

    public static synchronized void configure(DeviceProfile profile) {
        if (profile == null) { configure("", false); return; }
        String maker = profile.manufacturer.toLowerCase(Locale.ROOT);
        String model = profile.model.toLowerCase(Locale.ROOT);
        String key = maker + "|" + model + "|" + profile.androidApi + "|" + profile.primaryAbi
                + "|" + profile.staticProfile.environmentRevision + "|" + profile.googlePlay
                + "|" + profile.googleServices + "|" + profile.amazonStore
                + "|" + profile.packageInstaller + "|" + profile.unknownSourcesAllowed
                + "|" + profile.systemWebView + "|" + profile.dynamicProfile.networkStatus.kind;
        configure(key, maker.contains("xiaomi") && "mitv-mftr0".equals(model)
                && profile.androidApi == 30 && "armeabi-v7a".equalsIgnoreCase(profile.primaryAbi));
    }

    static synchronized void configure(String key, boolean localPending) {
        if (key.equals(profileKey)) return;
        profileKey = key;
        LOCAL_HIDDEN.clear();
        if (localPending) LOCAL_HIDDEN.addAll(XIAOMI_MFTR0_PENDING);
        invalidate();
    }

    public static synchronized void invalidate() {
        snapshot = Collections.emptyMap();
        defaultVisible = true;
        receivedAt = 0;
        requestEnvironment = "";
        generation++;
    }

    public static synchronized long beginRequest(String environment) {
        if (!environment.equals(requestEnvironment)) invalidate();
        requestEnvironment = environment;
        return ++generation;
    }

    public static synchronized boolean apply(JSONObject response, long ticket) {
        return apply(response, ticket, System.nanoTime() / 1000000L);
    }

    static synchronized boolean apply(JSONObject response, long ticket, long now) {
        JSONArray items = response == null ? null : response.optJSONArray("overrides");
        if (items == null || ticket != generation) return false;
        java.util.Map<String, Status> replacement = new java.util.HashMap<>();
        for (int index = 0; index < items.length(); index++) {
            JSONObject item = items.optJSONObject(index);
            if (item == null) return false; // Reject partial or malformed snapshots atomically.
            String pkg = item.optString("package_name", "");
            if (pkg.isEmpty() || replacement.containsKey(pkg)) return false;
            replacement.put(pkg, new Status(item));
        }
        boolean nextDefault = response.optBoolean("default_visible", true);
        boolean changed = !snapshot.equals(replacement) || defaultVisible != nextDefault;
        snapshot = Collections.unmodifiableMap(replacement);
        defaultVisible = nextDefault;
        receivedAt = now;
        return changed;
    }

    private static synchronized void expire(long now) {
        if (receivedAt != 0 && now - receivedAt >= TTL_MS) invalidate();
    }

    public static synchronized Status status(String packageName) {
        expire(System.nanoTime() / 1000000L);
        return snapshot.get(packageName);
    }

    static synchronized boolean isVisible(String packageName, long now) {
        expire(now);
        Status status = snapshot.get(packageName);
        return status != null ? status.visible : defaultVisible && !LOCAL_HIDDEN.contains(packageName);
    }

    public static boolean isVisible(String packageName) {
        return isVisible(packageName, System.nanoTime() / 1000000L);
    }

    public static List<CatalogApp> filter(List<CatalogApp> apps) {
        List<CatalogApp> visible = new ArrayList<>();
        for (CatalogApp app : apps) {
            if (isVisible(app.packageName)) visible.add(app);
        }
        return visible;
    }
}
