package app.aptelly.tv.catalog;

import android.graphics.Color;

import app.aptelly.tv.BuildConfig;
import app.aptelly.tv.R;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class AppCatalog {
    private AppCatalog() {
    }

    public static List<CatalogApp> stores() {
        return Arrays.asList(
                platformStore(
                        "Google Play",
                        R.string.desc_google_play_store,
                        "com.android.vending",
                        R.drawable.brand_google_play_store,
                        66, 133, 244, 15, 157, 88
                ),
                platformStore(
                        "Amazon Appstore",
                        R.string.desc_amazon_appstore,
                        "com.amazon.venezia",
                        R.drawable.brand_amazon_appstore,
                        35, 47, 62, 0, 168, 225
                ),
                new CatalogApp(
                        "Aurora Store",
                        R.string.desc_aurora_store,
                        "com.aurora.store",
                        R.drawable.brand_aurora_store,
                        Color.rgb(45, 196, 166),
                        Color.rgb(30, 92, 127),
                        CatalogApp.Source.OFFICIAL_AURORA_STORE,
                        false,
                        true,
                        true,
                        "https://www.auroraoss.com/"
                ),
                platformStore(
                        "Aptoide TV",
                        R.string.desc_aptoide_tv_store,
                        "cm.aptoidetv.pt",
                        R.drawable.brand_aptoide_tv,
                        255, 112, 67, 193, 48, 91
                ),
                platformStore(
                        "小米电视应用商店",
                        R.string.desc_xiaomi_tv_store,
                        "com.xiaomi.mitv.appstore",
                        R.drawable.brand_xiaomi_tv_store,
                        255, 105, 0, 201, 64, 0
                )
        );
    }

    public static List<CatalogApp> popular() {
        return Arrays.asList(
                app("YouTube", R.string.desc_youtube, "com.google.android.youtube.tv",
                        R.drawable.brand_youtube, 244, 32, 47, 145, 11, 30, true, true),
                direct("SmartTube", R.string.desc_smarttube, "org.smarttube.stable",
                        R.drawable.brand_smarttube, 239, 39, 35, 7, 126, 140,
                        CatalogApp.Source.OFFICIAL_SMARTTUBE),
                app("Netflix", R.string.desc_netflix, "com.netflix.ninja",
                        R.drawable.brand_netflix, 24, 24, 28, 5, 5, 8, false, true),
                app("Disney+", R.string.desc_disney, "com.disney.disneyplus",
                        R.drawable.brand_disney, 20, 111, 174, 7, 30, 75, false, true),
                app("Google TV", R.string.desc_google_tv, "com.google.android.videos",
                        R.drawable.brand_google_tv, 49, 82, 175, 20, 35, 88, true, true),
                app("Prime Video", R.string.desc_prime, "com.amazon.amazonvideo.livingroom",
                        R.drawable.brand_prime, 17, 75, 119, 2, 31, 55, false, true),
                app("Plex", R.string.desc_plex, "com.plexapp.android",
                        R.drawable.brand_plex, 48, 48, 48, 12, 12, 13, false, true),
                app("Spotify", R.string.desc_spotify, "com.spotify.tv.android",
                        R.drawable.brand_spotify, 30, 215, 96, 11, 90, 51, false, true)
        );
    }

    public static List<CatalogApp> optionalMedia() {
        List<CatalogApp> result = new ArrayList<>(Arrays.asList(
                app("Jellyfin", R.string.desc_jellyfin, "org.jellyfin.androidtv",
                        R.drawable.brand_jellyfin, 108, 65, 230, 36, 126, 218, false, false),
                app("Kodi", R.string.desc_kodi, "org.xbmc.kodi",
                        R.drawable.brand_kodi, 32, 177, 224, 18, 92, 164, false, false),
                app("VLC", R.string.desc_vlc, "org.videolan.vlc",
                        R.drawable.brand_vlc, 255, 163, 35, 216, 78, 25, false, false),
                app("TV Bro", R.string.desc_tv_bro, "com.phlox.tvwebbrowser",
                        R.drawable.brand_tv_bro, 255, 231, 0, 31, 151, 255,
                        false, true)
        ));
        if (BuildConfig.ENABLE_TEST_FIXTURES) {
            result.add(direct(
                    "Aptelly Upgrade Fixture",
                    R.string.desc_upgrade_fixture,
                    "app.aptelly.fixture.upgrade",
                    R.drawable.app_icon,
                    53, 92, 220, 88, 42, 176,
                    CatalogApp.Source.TEST_FIXTURE
            ));
        }
        return result;
    }

    public static List<CatalogApp> globalServices() {
        return Arrays.asList(
                app("Pluto TV", R.string.desc_free_streaming,
                        "tv.pluto.android", R.drawable.brand_pluto,
                        58, 58, 68, 12, 12, 17, false, true),
                app("Tubi", R.string.desc_free_streaming,
                        "com.tubitv", R.drawable.brand_tubi,
                        253, 93, 255, 70, 227, 160, false, true),
                app("FilmRise", R.string.desc_free_movies_no_account,
                        "com.recipe.filmrise", R.drawable.brand_filmrise,
                        240, 83, 44, 121, 22, 53, false, true),
                app("Fawesome", R.string.desc_free_movies_no_account,
                        "com.future.moviesByFawesomeAndroidTV", R.drawable.brand_fawesome,
                        244, 71, 82, 131, 22, 123, false, true),
                app("Xumo Play", R.string.desc_xumo_free,
                        "com.xumo.xumo.tv", R.drawable.brand_xumo,
                        51, 74, 230, 28, 19, 100, false, true),
                app("Apple TV", R.string.desc_subscription_global,
                        "com.apple.atve.androidtv.appletv", R.drawable.brand_apple_tv,
                        28, 28, 31, 4, 4, 6, false, false),
                app("HBO Max", R.string.desc_subscription_global,
                        "com.wbd.stream", R.drawable.brand_hbo_max,
                        119, 57, 233, 33, 15, 85, false, false),
                app("Paramount+", R.string.desc_subscription_global,
                        "com.cbs.ott", R.drawable.brand_paramount,
                        18, 96, 214, 5, 32, 96, false, false),
                app("Crunchyroll", R.string.desc_anime_global,
                        "com.crunchyroll.crunchyroid", R.drawable.brand_crunchyroll,
                        245, 117, 32, 119, 39, 7, false, false),
                app("Twitch", R.string.desc_live_community,
                        "tv.twitch.android.app", R.drawable.brand_twitch,
                        145, 70, 255, 55, 25, 115, false, false),
                app("DAZN", R.string.desc_sports_global,
                        "com.dazn", R.drawable.brand_dazn,
                        38, 38, 41, 6, 6, 8, false, false),
                app("YouTube Kids", R.string.desc_youtube_kids,
                        "com.google.android.youtube.tvkids", R.drawable.brand_youtube_kids,
                        255, 93, 83, 167, 24, 35, true, false),
                app("Emby", R.string.desc_personal_media,
                        "tv.emby.embyatv", R.drawable.brand_emby,
                        82, 181, 75, 20, 74, 43, false, false),
                app("Stremio", R.string.desc_video_hub,
                        "com.stremio.one", R.drawable.brand_stremio,
                        123, 92, 250, 42, 27, 112, false, false),
                app("Moonlight", R.string.desc_game_streaming,
                        "com.limelight", R.drawable.brand_moonlight,
                        118, 185, 0, 21, 73, 18, false, false),
                app("Steam Link", R.string.desc_game_streaming,
                        "com.valvesoftware.steamlink", R.drawable.brand_steam_link,
                        27, 79, 114, 7, 24, 39, false, false)
        );
    }

    public static List<CatalogApp> regionalServices() {
        return Arrays.asList(
                new CatalogApp(
                        "爱壹帆",
                        R.string.desc_aiyifan,
                        "tv.ifvod.classic",
                        R.drawable.brand_aiyifan,
                        Color.rgb(234, 52, 137),
                        Color.rgb(52, 69, 205),
                        CatalogApp.Source.CHINA_TV_STORE,
                        false,
                        true,
                        true,
                        "https://apkpure.net/cn/aiyifan-tv/tv.ifvod.classic/download"
                ),
                app("Kanopy", R.string.desc_library_free,
                        "com.kanopy.tvapp", R.drawable.brand_kanopy,
                        31, 31, 35, 6, 6, 8, false, false),
                app("R Channel", R.string.desc_r_channel_free,
                        "jp.co.rakuten.channel.tv.google", R.drawable.brand_r_channel,
                        191, 0, 40, 80, 0, 22, false, false),
                app("Hulu", R.string.desc_us_streaming,
                        "com.hulu.livingroomplus", R.drawable.brand_hulu,
                        34, 202, 108, 8, 81, 49, false, false),
                app("Peacock", R.string.desc_us_streaming,
                        "com.peacocktv.peacockandroid", R.drawable.brand_peacock,
                        45, 45, 50, 5, 5, 8, false, false),
                app("ViX", R.string.desc_latam_streaming,
                        "com.univision.prendetv", R.drawable.brand_vix,
                        255, 105, 33, 123, 28, 92, false, false),
                app("Globoplay", R.string.desc_brazil_streaming,
                        "com.globo.globotv", R.drawable.brand_globoplay,
                        238, 62, 59, 112, 23, 139, false, false),
                app("iQIYI", R.string.desc_asian_streaming,
                        "com.iqiyi.i18n.tv", R.drawable.brand_iqiyi,
                        47, 205, 95, 8, 100, 45, false, false),
                app("Rakuten Viki", R.string.desc_asian_streaming,
                        "com.viki.android", R.drawable.brand_viki,
                        33, 201, 197, 9, 92, 120, false, false),
                app("Shahid", R.string.desc_mena_streaming,
                        "net.mbc.shahidTV", R.drawable.brand_shahid,
                        40, 178, 112, 7, 72, 55, false, false),
                app("JioHotstar", R.string.desc_india_streaming,
                        "in.startv.hotstar", R.drawable.brand_jiohotstar,
                        28, 91, 184, 4, 27, 80, false, false),
                app("Sony LIV", R.string.desc_india_streaming,
                        "com.sonyliv", R.drawable.brand_sonyliv,
                        84, 33, 123, 27, 9, 54, false, false),
                app("Viu", R.string.desc_asian_streaming,
                        "com.vuclip.viu", R.drawable.brand_viu,
                        246, 168, 0, 132, 71, 0, false, false),
                app("ZEE5", R.string.desc_india_streaming,
                        "com.graymatrix.did", R.drawable.brand_zee5,
                        37, 37, 39, 5, 5, 6, false, false)
        );
    }

    public static List<CatalogApp> chinaMainlandServices() {
        return Arrays.asList(
                chinaApp("爱奇艺（银河奇异果）", R.string.desc_galaxy_qiyi,
                        "com.gitvdemo.video", R.drawable.brand_galaxy_qiyi,
                        77, 214, 75, 13, 105, 43, true,
                        "https://app.iqiyi.com/tv/player/"),
                chinaApp("腾讯视频（云视听极光）", R.string.desc_tencent_video_tv,
                        "com.ktcp.video", R.drawable.brand_tencent_video,
                        36, 166, 247, 8, 70, 164, true,
                        "https://v.qq.com/"),
                chinaApp("优酷（酷喵）", R.string.desc_youku_tv,
                        "com.cibn.tv", R.drawable.brand_youku,
                        255, 55, 112, 0, 132, 214, true,
                        "https://pd.youku.com/cibn"),
                chinaApp("芒果TV", R.string.desc_mango_tv,
                        "com.starcor.mango", R.drawable.brand_mango_tv,
                        255, 139, 29, 193, 66, 11, false,
                        "https://www.mgtv.com/app/index.html"),
                chinaApp("哔哩哔哩（云视听小电视）", R.string.desc_bilibili_tv,
                        "com.xiaodianshi.tv.yst", R.drawable.brand_bilibili_tv,
                        0, 174, 236, 21, 83, 148, false,
                        "https://www.bilibili.com/"),
                chinaApp("央视频TV", R.string.desc_cctv_tv,
                        "com.newtv.cboxtv", R.drawable.brand_cctv_tv,
                        222, 44, 50, 42, 64, 122, false,
                        "https://www.cctv.com/")
        );
    }

    public static List<CatalogApp> privacy() {
        return Arrays.asList(
                direct("Clash Meta", R.string.desc_clash,
                        "com.github.metacubex.clash.meta", R.drawable.brand_clash,
                        42, 114, 210, 22, 42, 92,
                        CatalogApp.Source.OFFICIAL_CLASH_META),
                direct("Tailscale", R.string.desc_tailscale, "com.tailscale.ipn",
                        R.drawable.brand_tailscale, 48, 48, 52, 8, 8, 10,
                        CatalogApp.Source.OFFICIAL_TAILSCALE),
                direct("WireGuard", R.string.desc_wireguard, "com.wireguard.android",
                        R.drawable.brand_wireguard, 125, 45, 57, 66, 15, 31,
                        CatalogApp.Source.OFFICIAL_WIREGUARD),
                direct("OpenVPN", R.string.desc_openvpn, "de.blinkt.openvpn",
                        R.drawable.brand_openvpn, 250, 114, 39, 84, 43, 35,
                        CatalogApp.Source.FDROID_OPENVPN)
        );
    }

    public static List<CatalogApp> all() {
        List<CatalogApp> result = new ArrayList<>();
        result.addAll(stores());
        result.addAll(popular());
        result.addAll(chinaMainlandServices());
        result.addAll(globalServices());
        result.addAll(regionalServices());
        result.addAll(optionalMedia());
        result.addAll(privacy());
        return result;
    }

    /** Content services grouped by what users do, independent of market. */
    public static List<CatalogApp> tvEntertainment() {
        Set<String> tools = new HashSet<>(Arrays.asList(
                "com.plexapp.android", "tv.emby.embyatv", "com.stremio.one",
                "com.limelight", "com.valvesoftware.steamlink"
        ));
        List<CatalogApp> result = new ArrayList<>();
        for (List<CatalogApp> group : Arrays.asList(
                popular(), globalServices(), regionalServices()
        )) {
            for (CatalogApp app : group) {
                if (!tools.contains(app.packageName)) result.add(app);
            }
        }
        return result;
    }

    public static List<CatalogApp> mediaPlayers() {
        return matchingPackages(
                Arrays.asList(popular(), globalServices(), optionalMedia()),
                "com.plexapp.android", "tv.emby.embyatv", "com.stremio.one",
                "org.jellyfin.androidtv", "org.xbmc.kodi", "org.videolan.vlc",
                "app.aptelly.fixture.upgrade"
        );
    }

    public static List<CatalogApp> gameStreaming() {
        return matchingPackages(
                Arrays.asList(globalServices()),
                "com.limelight", "com.valvesoftware.steamlink"
        );
    }

    public static List<CatalogApp> webTools() {
        return matchingPackages(
                Arrays.asList(optionalMedia()),
                "com.phlox.tvwebbrowser"
        );
    }

    private static List<CatalogApp> matchingPackages(
            List<List<CatalogApp>> groups,
            String... packages
    ) {
        Set<String> accepted = new HashSet<>(Arrays.asList(packages));
        List<CatalogApp> result = new ArrayList<>();
        for (List<CatalogApp> group : groups) {
            for (CatalogApp app : group) {
                if (accepted.contains(app.packageName)) result.add(app);
            }
        }
        return result;
    }

    private static CatalogApp app(
            String name,
            int description,
            String packageName,
            int icon,
            int startRed,
            int startGreen,
            int startBlue,
            int endRed,
            int endGreen,
            int endBlue,
            boolean requiresGoogle,
            boolean recommended
    ) {
        return new CatalogApp(
                name,
                description,
                packageName,
                icon,
                Color.rgb(startRed, startGreen, startBlue),
                Color.rgb(endRed, endGreen, endBlue),
                CatalogApp.Source.PLAY_STORE,
                requiresGoogle,
                recommended,
                true,
                null
        );
    }

    private static CatalogApp chinaApp(
            String name,
            int description,
            String packageName,
            int icon,
            int startRed,
            int startGreen,
            int startBlue,
            int endRed,
            int endGreen,
            int endBlue,
            boolean recommended,
            String officialPage
    ) {
        return new CatalogApp(
                name,
                description,
                packageName,
                icon,
                Color.rgb(startRed, startGreen, startBlue),
                Color.rgb(endRed, endGreen, endBlue),
                CatalogApp.Source.CHINA_TV_STORE,
                false,
                recommended,
                true,
                officialPage
        );
    }

    private static CatalogApp direct(
            String name,
            int description,
            String packageName,
            int icon,
            int startRed,
            int startGreen,
            int startBlue,
            int endRed,
            int endGreen,
            int endBlue,
            CatalogApp.Source source
    ) {
        return new CatalogApp(
                name,
                description,
                packageName,
                icon,
                Color.rgb(startRed, startGreen, startBlue),
                Color.rgb(endRed, endGreen, endBlue),
                source,
                false,
                false,
                true,
                null
        );
    }

    private static CatalogApp platformStore(
            String name,
            int description,
            String packageName,
            int icon,
            int startRed,
            int startGreen,
            int startBlue,
            int endRed,
            int endGreen,
            int endBlue
    ) {
        return new CatalogApp(
                name,
                description,
                packageName,
                icon,
                Color.rgb(startRed, startGreen, startBlue),
                Color.rgb(endRed, endGreen, endBlue),
                CatalogApp.Source.PLATFORM_STORE,
                false,
                false,
                true,
                null
        );
    }

    private static CatalogApp mobileApp(
            String name,
            int description,
            String packageName,
            int icon,
            int startRed,
            int startGreen,
            int startBlue,
            int endRed,
            int endGreen,
            int endBlue
    ) {
        return new CatalogApp(
                name,
                description,
                packageName,
                icon,
                Color.rgb(startRed, startGreen, startBlue),
                Color.rgb(endRed, endGreen, endBlue),
                CatalogApp.Source.PLAY_STORE,
                false,
                false,
                false,
                null
        );
    }
}
