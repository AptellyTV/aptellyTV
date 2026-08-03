package app.aptelly.tv.catalog;

import android.app.Activity;

import app.aptelly.tv.BrowserActivity;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Official service-owned web entry points used when a native TV package cannot
 * be installed or cannot run without a vendor runtime.
 *
 * <p>This is intentionally separate from APK sources. A web entry does not
 * claim to install an app and does not bypass a provider's account, region,
 * DRM, subscription, or device policies.</p>
 */
public final class OfficialWebRuntime {
    private static final Map<String, String> URLS;

    static {
        Map<String, String> urls = new HashMap<>();

        // Global services.
        urls.put("com.google.android.youtube.tv", "https://www.youtube.com/tv");
        urls.put("com.netflix.ninja", "https://www.netflix.com/browse");
        urls.put("com.disney.disneyplus", "https://www.disneyplus.com/");
        urls.put("com.google.android.videos", "https://www.youtube.com/feed/storefront");
        urls.put("com.amazon.amazonvideo.livingroom", "https://www.primevideo.com/");
        urls.put("com.plexapp.android", "https://app.plex.tv/desktop/");
        urls.put("com.spotify.tv.android", "https://open.spotify.com/");
        urls.put("com.apple.atve.androidtv.appletv", "https://tv.apple.com/");
        urls.put("com.wbd.stream", "https://play.max.com/");
        urls.put("com.cbs.ott", "https://www.paramountplus.com/");
        urls.put("com.crunchyroll.crunchyroid", "https://www.crunchyroll.com/");
        urls.put("tv.twitch.android.app", "https://www.twitch.tv/");
        urls.put("tv.pluto.android", "https://pluto.tv/");
        urls.put("com.tubitv", "https://tubitv.com/");
        urls.put("com.dazn", "https://www.dazn.com/");
        urls.put("com.google.android.youtube.tvkids", "https://www.youtubekids.com/");

        // Regional services.
        urls.put("com.hulu.livingroomplus", "https://www.hulu.com/hub/home");
        urls.put("com.peacocktv.peacockandroid", "https://www.peacocktv.com/");
        urls.put("com.univision.prendetv", "https://vix.com/");
        urls.put("com.globo.globotv", "https://globoplay.globo.com/");
        urls.put("com.iqiyi.i18n.tv", "https://www.iq.com/");
        urls.put("com.viki.android", "https://www.viki.com/");
        urls.put("net.mbc.shahidTV", "https://shahid.mbc.net/");
        urls.put("in.startv.hotstar", "https://www.hotstar.com/in");
        urls.put("tv.ifvod.classic", "https://www.yifan.tv/");

        // Mainland China services. These URLs remain useful even when a TV
        // manufacturer store does not expose the matching licensed TV package.
        urls.put("com.gitvdemo.video", "https://www.iqiyi.com/");
        urls.put("com.ktcp.video", "https://v.qq.com/");
        urls.put("com.cibn.tv", "https://www.youku.com/");
        urls.put("com.starcor.mango", "https://www.mgtv.com/");
        urls.put("com.xiaodianshi.tv.yst", "https://www.bilibili.com/");
        urls.put("com.newtv.cboxtv", "https://tv.cctv.com/");
        urls.put("com.tencent.qqmusictv", "https://y.qq.com/");

        URLS = Collections.unmodifiableMap(urls);
    }

    private OfficialWebRuntime() {
    }

    public static boolean isAvailable(CatalogApp app) {
        return URLS.containsKey(app.packageName);
    }

    public static String urlFor(CatalogApp app) {
        return URLS.get(app.packageName);
    }

    public static void open(Activity activity, CatalogApp app) {
        String url = urlFor(app);
        if (url == null) {
            return;
        }
        BrowserActivity.openTvRuntime(activity, url, app.name);
    }
}
