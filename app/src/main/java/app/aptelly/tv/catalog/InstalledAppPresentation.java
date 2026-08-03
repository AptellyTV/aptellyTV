package app.aptelly.tv.catalog;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;

import app.aptelly.tv.R;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Human-friendly metadata for cards in the installed-app shelf. */
public final class InstalledAppPresentation {
    private static final int MAX_REGION_LABELS = 3;
    private static final Map<String, MarketProfile> PROFILES = profiles();

    public final String description;
    public final String languages;
    public final String regions;

    private InstalledAppPresentation(
            String description,
            String languages,
            String regions
    ) {
        this.description = description;
        this.languages = languages;
        this.regions = regions;
    }

    public static InstalledAppPresentation resolve(
            Context context,
            String installedPackage,
            String label
    ) {
        CatalogApp catalogApp = catalogApp(installedPackage);
        String canonicalPackage = catalogApp == null
                ? installedPackage
                : catalogApp.packageName;
        MarketProfile profile = PROFILES.get(canonicalPackage);
        String description = description(context, installedPackage, label, catalogApp);
        String languages = profile == null
                ? detectedLanguages(context, installedPackage)
                : languageLabel(context, profile.languageTags);
        String regions = profile == null
                ? context.getString(R.string.app_region_service_dependent)
                : regionLabel(context, profile.regionCodes);
        return new InstalledAppPresentation(description, languages, regions);
    }

    private static CatalogApp catalogApp(String installedPackage) {
        for (CatalogApp app : AppCatalog.all()) {
            if (InstalledAppResolver.isAcceptedVariant(
                    app.packageName,
                    installedPackage
            )) {
                return app;
            }
        }
        return null;
    }

    private static String description(
            Context context,
            String installedPackage,
            String label,
            CatalogApp catalogApp
    ) {
        if ("com.netflix.mediaclient".equals(installedPackage)) {
            return context.getString(R.string.desc_netflix_mobile_variant);
        }
        if (catalogApp != null) {
            return context.getString(catalogApp.descriptionRes);
        }
        if ("com.overseas.store.appstore".equals(installedPackage)) {
            return context.getString(R.string.desc_emotn_store);
        }
        try {
            PackageManager manager = context.getPackageManager();
            ApplicationInfo info = manager.getApplicationInfo(installedPackage, 0);
            CharSequence supplied = info.loadDescription(manager);
            if (supplied != null && !supplied.toString().trim().isEmpty()) {
                return supplied.toString().trim();
            }
        } catch (PackageManager.NameNotFoundException ignored) {
            // The launcher entry disappeared; retain a useful generic description.
        }
        return context.getString(R.string.installed_generic_description, label);
    }

    private static String detectedLanguages(Context context, String packageName) {
        try {
            String[] locales = context.getPackageManager()
                    .getResourcesForApplication(packageName)
                    .getAssets()
                    .getLocales();
            Set<String> languages = new LinkedHashSet<>();
            for (String value : locales) {
                String language = Locale.forLanguageTag(value.replace('_', '-'))
                        .getLanguage();
                if (!language.isEmpty() && !"und".equals(language)) {
                    languages.add(language);
                }
            }
            if (languages.size() > 3) {
                return context.getString(R.string.app_language_multiple);
            }
            if (!languages.isEmpty()) {
                return languageLabel(context, new ArrayList<>(languages));
            }
        } catch (PackageManager.NameNotFoundException ignored) {
            // Fall through to the honest system-language label.
        }
        return context.getString(R.string.app_language_system);
    }

    private static String languageLabel(Context context, List<String> tags) {
        if (tags.isEmpty() || tags.contains("multi")) {
            return context.getString(R.string.app_language_multiple);
        }
        Locale displayLocale = displayLocale(context);
        List<String> labels = new ArrayList<>();
        for (String tag : tags) {
            String label = Locale.forLanguageTag(tag).getDisplayLanguage(displayLocale);
            if (!label.isEmpty() && !labels.contains(label)) {
                labels.add(label);
            }
            if (labels.size() == 2) break;
        }
        return labels.isEmpty()
                ? context.getString(R.string.app_language_system)
                : String.join("/", labels);
    }

    private static String regionLabel(Context context, List<String> codes) {
        if (codes.isEmpty() || codes.contains("GLOBAL")) {
            return context.getString(R.string.app_region_global);
        }
        Locale displayLocale = displayLocale(context);
        List<String> labels = new ArrayList<>();
        for (String code : codes) {
            String label = new Locale("", code).getDisplayCountry(displayLocale);
            if (!label.isEmpty() && !labels.contains(label)) {
                labels.add(label);
            }
            if (labels.size() == MAX_REGION_LABELS) break;
        }
        return labels.isEmpty()
                ? context.getString(R.string.app_region_service_dependent)
                : String.join(" · ", labels);
    }

    private static Locale displayLocale(Context context) {
        if (Build.VERSION.SDK_INT >= 24) {
            return context.getResources().getConfiguration().getLocales().get(0);
        }
        return context.getResources().getConfiguration().locale;
    }

    private static Map<String, MarketProfile> profiles() {
        Map<String, MarketProfile> values = new LinkedHashMap<>();

        global(values, "multi",
                "com.android.vending", "com.amazon.venezia", "com.aurora.store",
                "cm.aptoidetv.pt", "com.google.android.youtube.tv",
                "org.smarttube.stable",
                "com.disney.disneyplus", "com.google.android.videos",
                "com.amazon.amazonvideo.livingroom", "com.plexapp.android",
                "com.spotify.tv.android", "org.jellyfin.androidtv",
                "org.xbmc.kodi", "org.videolan.vlc", "com.phlox.tvwebbrowser",
                "tv.pluto.android", "com.apple.atve.androidtv.appletv",
                "com.wbd.stream", "com.cbs.ott", "com.crunchyroll.crunchyroid",
                "tv.twitch.android.app", "com.dazn",
                "com.google.android.youtube.tvkids", "tv.emby.embyatv",
                "com.stremio.one", "com.limelight",
                "com.valvesoftware.steamlink", "com.viki.android",
                "com.github.metacubex.clash.meta", "com.tailscale.ipn",
                "com.wireguard.android", "de.blinkt.openvpn");
        global(values, "multi", "com.overseas.store.appstore");
        put(values, "com.xiaomi.mitv.appstore", "zh", "CN");

        // These are suggested sign-in exits, not a claim that Netflix is
        // unavailable elsewhere. Keep the observed mobile package separate
        // from the TV catalog identity so it never satisfies TV installation.
        put(values, "com.netflix.ninja", "multi", "US", "HK", "SG");
        put(values, "com.netflix.mediaclient", "multi", "US", "HK", "SG");

        put(values, "com.tubitv", "en", "US", "CA", "AU");
        put(values, "com.recipe.filmrise", "en", "US", "CA", "GB");
        put(values, "com.future.moviesByFawesomeAndroidTV", "en", "US", "CA", "GB");
        put(values, "com.xumo.xumo.tv", "en", "US", "CA", "GB");
        put(values, "com.kanopy.tvapp", "en", "US", "CA", "AU");
        put(values, "jp.co.rakuten.channel.tv.google", "ja", "JP");
        put(values, "com.hulu.livingroomplus", "en", "US");
        put(values, "com.peacocktv.peacockandroid", "en", "US");
        put(values, "com.univision.prendetv", "es,en", "US", "MX", "AR");
        put(values, "com.globo.globotv", "pt", "BR", "PT", "US");
        put(values, "com.iqiyi.i18n.tv", "zh,en", "SG", "MY", "TH");
        put(values, "net.mbc.shahidTV", "ar,en", "SA", "AE", "EG");
        put(values, "in.startv.hotstar", "hi,en", "IN");
        put(values, "com.sonyliv", "hi,en", "IN");
        put(values, "com.vuclip.viu", "zh,en", "HK", "SG", "TH");
        put(values, "com.graymatrix.did", "hi,en", "IN", "US", "GB");
        put(values, "tv.ifvod.classic", "zh", "GLOBAL");

        for (String packageName : Arrays.asList(
                "com.gitvdemo.video", "com.ktcp.video", "com.cibn.tv",
                "com.starcor.mango", "com.xiaodianshi.tv.yst",
                "com.newtv.cboxtv", "com.tencent.qqmusictv"
        )) {
            put(values, packageName, "zh", "CN");
        }
        return Collections.unmodifiableMap(values);
    }

    private static void global(
            Map<String, MarketProfile> values,
            String languages,
            String... packages
    ) {
        for (String packageName : packages) {
            put(values, packageName, languages, "GLOBAL");
        }
    }

    private static void put(
            Map<String, MarketProfile> values,
            String packageName,
            String languages,
            String... regions
    ) {
        values.put(packageName, new MarketProfile(
                Arrays.asList(languages.split(",")),
                Arrays.asList(regions)
        ));
    }

    private static final class MarketProfile {
        final List<String> languageTags;
        final List<String> regionCodes;

        MarketProfile(List<String> languageTags, List<String> regionCodes) {
            this.languageTags = languageTags;
            this.regionCodes = regionCodes;
        }
    }
}
