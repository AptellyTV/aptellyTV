package app.aptelly.tv.catalog;

public final class CatalogApp {
    public enum Delivery {
        BUNDLED_VERIFIED,
        SERVER_MATCHED,
        WEB_REFERENCE
    }

    public enum Source {
        PLAY_STORE,
        PLATFORM_STORE,
        CHINA_TV_STORE,
        OFFICIAL_SMARTTUBE,
        OFFICIAL_CLASH_META,
        OFFICIAL_WIREGUARD,
        OFFICIAL_TAILSCALE,
        OFFICIAL_AURORA_STORE,
        FDROID_OPENVPN,
        TEST_FIXTURE,
        WEB
    }

    public final String name;
    public final int descriptionRes;
    public final String packageName;
    public final int iconRes;
    public final int startColor;
    public final int endColor;
    public final Source source;
    public final boolean requiresGoogle;
    public final boolean recommended;
    public final boolean tvOptimized;
    public final String officialPage;

    public CatalogApp(
            String name,
            int descriptionRes,
            String packageName,
            int iconRes,
            int startColor,
            int endColor,
            Source source,
            boolean requiresGoogle,
            boolean recommended,
            boolean tvOptimized,
            String officialPage
    ) {
        this.name = name;
        this.descriptionRes = descriptionRes;
        this.packageName = packageName;
        this.iconRes = iconRes;
        this.startColor = startColor;
        this.endColor = endColor;
        this.source = source;
        this.requiresGoogle = requiresGoogle;
        this.recommended = recommended;
        this.tvOptimized = tvOptimized;
        this.officialPage = officialPage;
    }

    public Delivery delivery() {
        if (source == Source.OFFICIAL_CLASH_META) {
            return Delivery.BUNDLED_VERIFIED;
        }
        return source == Source.WEB
                ? Delivery.WEB_REFERENCE
                : Delivery.SERVER_MATCHED;
    }

    public boolean supportsOneClickInstall() {
        return source != Source.WEB && source != Source.PLATFORM_STORE;
    }

    public boolean isPlatformStore() {
        return source == Source.PLATFORM_STORE;
    }

    public boolean isBundledInstall() {
        return delivery() == Delivery.BUNDLED_VERIFIED;
    }
}
