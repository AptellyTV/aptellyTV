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
import java.util.concurrent.ConcurrentHashMap;

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
                    "com.iqiyi.i18n.tv", "com.cibn.tv", "com.gitvdemo.video",
                    "com.ktcp.video", "com.starcor.mango", "com.tencent.qqmusictv"
            ))
    );

    private static final ConcurrentHashMap<String, Boolean> OVERRIDES =
            new ConcurrentHashMap<>();

    private CatalogAvailability() {
    }

    public static void configure(DeviceProfile profile) {
        OVERRIDES.clear();
        if (profile == null) return;
        String manufacturer = profile.manufacturer.toLowerCase(Locale.ROOT);
        String model = profile.model.toLowerCase(Locale.ROOT);
        if (manufacturer.contains("xiaomi")
                && "mitv-mftr0".equals(model)
                && profile.androidApi == 30
                && "armeabi-v7a".equalsIgnoreCase(profile.primaryAbi)) {
            for (String packageName : XIAOMI_MFTR0_PENDING) {
                OVERRIDES.put(packageName, false);
            }
        }
    }

    public static boolean apply(JSONObject response) {
        JSONArray items = response == null ? null : response.optJSONArray("overrides");
        if (items == null) return false;
        boolean changed = false;
        for (int index = 0; index < items.length(); index++) {
            JSONObject item = items.optJSONObject(index);
            if (item == null) continue;
            String packageName = item.optString("package_name", "");
            if (!packageName.isEmpty()) {
                boolean visible = item.optBoolean("visible", false);
                boolean previous = isVisible(packageName);
                OVERRIDES.put(packageName, visible);
                changed |= previous != visible;
            }
        }
        return changed;
    }

    public static boolean isVisible(String packageName) {
        return OVERRIDES.getOrDefault(packageName, true);
    }

    public static List<CatalogApp> filter(List<CatalogApp> apps) {
        List<CatalogApp> visible = new ArrayList<>();
        for (CatalogApp app : apps) {
            if (isVisible(app.packageName)) visible.add(app);
        }
        return visible;
    }
}
