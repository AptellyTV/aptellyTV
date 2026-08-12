package app.aptelly.tv.device;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;

public final class NetworkPreflight {
    private NetworkPreflight() {
    }

    public static NetworkStatus inspect(Context context) {
        ConnectivityManager manager =
                (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (manager == null) {
            return new NetworkStatus(NetworkStatus.Kind.NO_NETWORK, false);
        }
        Network network = manager.getActiveNetwork();
        NetworkCapabilities capabilities =
                network == null ? null : manager.getNetworkCapabilities(network);
        if (capabilities == null) {
            return new NetworkStatus(
                    NetworkStatus.Kind.NO_NETWORK,
                    manager.isActiveNetworkMetered()
            );
        }
        if (capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL)) {
            return new NetworkStatus(
                    NetworkStatus.Kind.CAPTIVE_PORTAL,
                    manager.isActiveNetworkMetered()
            );
        }
        boolean internet =
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
        boolean validated =
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
        if (!internet || !validated) {
            return new NetworkStatus(
                    NetworkStatus.Kind.UNVALIDATED,
                    manager.isActiveNetworkMetered()
            );
        }
        return new NetworkStatus(
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
                        ? NetworkStatus.Kind.VPN_READY
                        : NetworkStatus.Kind.INTERNET_READY,
                manager.isActiveNetworkMetered()
        );
    }
}
