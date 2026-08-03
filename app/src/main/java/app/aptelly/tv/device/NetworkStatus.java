package app.aptelly.tv.device;

public final class NetworkStatus {
    public enum Kind {
        NO_NETWORK,
        CAPTIVE_PORTAL,
        UNVALIDATED,
        INTERNET_READY,
        VPN_READY
    }

    public final Kind kind;
    public final boolean metered;

    public NetworkStatus(Kind kind, boolean metered) {
        this.kind = kind;
        this.metered = metered;
    }

    public boolean canDownload() {
        return kind == Kind.INTERNET_READY || kind == Kind.VPN_READY;
    }
}
