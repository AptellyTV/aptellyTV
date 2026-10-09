package app.aptelly.tv.install;

/** Preparation is distinct from successful installation or functional qualification. */
public final class AppSetupRequirements {
    public enum Kind { NONE, SERVER, COMPUTER, VPN_CONFIG, NETWORK_ACCOUNT, SERVICE_ACCOUNT }
    private AppSetupRequirements() {}
    public static Kind forPackage(String name) {
        if ("org.jellyfin.androidtv".equals(name)) return Kind.SERVER;
        if ("com.limelight".equals(name)) return Kind.COMPUTER;
        if ("com.tailscale.ipn".equals(name)) return Kind.NETWORK_ACCOUNT;
        if ("com.wireguard.android".equals(name) || "de.blinkt.openvpn".equals(name)
                || "com.github.metacubex.clash.meta".equals(name)) return Kind.VPN_CONFIG;
        if ("com.netflix.ninja".equals(name) || "com.disney.disneyplus".equals(name)
                || "com.amazon.amazonvideo.livingroom".equals(name) || "app.aptelly.prime.compat".equals(name)
                || "com.apple.atve.androidtv.appletv".equals(name) || "com.wbd.stream".equals(name)
                || "com.cbs.ott".equals(name) || "com.spotify.tv.android".equals(name)) return Kind.SERVICE_ACCOUNT;
        return Kind.NONE;
    }
}
