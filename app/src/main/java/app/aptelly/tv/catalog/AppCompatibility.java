package app.aptelly.tv.catalog;

import app.aptelly.tv.R;
import app.aptelly.tv.device.DeviceProfile;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public final class AppCompatibility {
    private static final Set<String> GOOGLE_NATIVE_PACKAGES = new HashSet<>(
            Arrays.asList(
                    "com.google.android.videos",
                    // The qualified Fubo Android TV build performs a hard
                    // Play Store / Play services licence check before its UI.
                    "tv.fubo.mobile"
            )
    );

    private static final Set<String> GOOGLE_TV_PACKAGES = new HashSet<>(
            Arrays.asList(
                    "com.google.android.youtube.tv",
                    "com.google.android.apps.youtube.unplugged"
            )
    );

    private static final Set<String> OPTIONAL_GOOGLE_PACKAGES = new HashSet<>(
            Arrays.asList(
                    "com.android.chrome"
            )
    );

    private static final Set<String> PLAY_CERTIFIED_VIDEO_PACKAGES = new HashSet<>(
            Arrays.asList(
                    "com.netflix.ninja"
            )
    );

    private static final Set<String> ACCOUNT_REQUIRED_PACKAGES = new HashSet<>(
            Arrays.asList(
                    "com.disney.disneyplus",
                    "com.amazon.amazonvideo.livingroom",
                    "com.spotify.tv.android",
                    "com.cbs.ott",
                    "com.dazn",
                    "com.discovery.discoveryplus.mobile",
                    "com.sling",
                    "tv.fubo.mobile",
                    "com.espn.score_center",
                    "com.formulaone.production",
                    "com.mubi",
                    "com.britbox.tv",
                    "com.kanopy.tvapp",
                    "com.stremio.one"
            )
    );

    private static final Set<String> EXTERNAL_SERVICE_PACKAGES = new HashSet<>(
            Arrays.asList(
                    "org.jellyfin.androidtv",
                    "com.limelight",
                    "com.tailscale.ipn",
                    "com.wireguard.android",
                    "de.blinkt.openvpn"
            )
    );

    private static final Set<String> PROTECTED_VIDEO_PACKAGES = new HashSet<>(
            Arrays.asList(
                    "com.netflix.ninja",
                    "com.disney.disneyplus",
                    "com.amazon.amazonvideo.livingroom",
                    "com.apple.atve.androidtv.appletv",
                    "com.wbd.stream",
                    "com.cbs.ott",
                    "com.crunchyroll.crunchyroid",
                    "tv.pluto.android",
                    "com.tubitv",
                    "com.dazn",
                    "com.hulu.livingroomplus",
                    "com.peacocktv.peacockandroid",
                    "com.univision.prendetv",
                    "com.globo.globotv",
                    "com.iqiyi.i18n.tv",
                    "com.viki.android",
                    "net.mbc.shahidTV",
                    "in.startv.hotstar"
            )
    );

    private AppCompatibility() {
    }

    public static int statusResource(
            CatalogApp app,
            DeviceProfile profile,
            boolean installed
    ) {
        if (app.isPlatformStore()) {
            return installed
                    ? R.string.compat_store_available
                    : R.string.compat_not_available_for_device;
        }
        if (!profile.packageInstaller && !hasUsableStore(profile)) {
            return R.string.compat_no_installer;
        }
        if (GOOGLE_NATIVE_PACKAGES.contains(app.packageName)) {
            if (profile.isGoogleReady()) {
                return R.string.compat_google_ready;
            }
            return R.string.compat_google_native_blocked;
        }
        if (ACCOUNT_REQUIRED_PACKAGES.contains(app.packageName)) {
            return R.string.compat_account_required;
        }
        if (EXTERNAL_SERVICE_PACKAGES.contains(app.packageName)) {
            return R.string.compat_external_service_required;
        }
        if (GOOGLE_TV_PACKAGES.contains(app.packageName)) {
            if (profile.isGoogleReady() || profile.fireTv) {
                return R.string.compat_platform_ready;
            }
            return R.string.compat_platform_variant;
        }
        if (OPTIONAL_GOOGLE_PACKAGES.contains(app.packageName)) {
            return R.string.compat_google_optional;
        }
        if (PROTECTED_VIDEO_PACKAGES.contains(app.packageName)) {
            if ("Unavailable".equalsIgnoreCase(profile.widevineLevel)) {
                return R.string.compat_no_widevine;
            }
            if ("L1".equalsIgnoreCase(profile.widevineLevel)) {
                return PLAY_CERTIFIED_VIDEO_PACKAGES.contains(app.packageName)
                        ? R.string.compat_play_certified_drm
                        : R.string.compat_widevine_l1;
            }
            return R.string.compat_widevine_l3;
        }
        return R.string.compat_native_ready;
    }

    public static boolean requiresGoogleRuntime(CatalogApp app) {
        return GOOGLE_NATIVE_PACKAGES.contains(app.packageName);
    }

    public static boolean isGoogleNativeBlocked(
            CatalogApp app,
            DeviceProfile profile
    ) {
        if (ProductVariantPolicy.DISCOVERY_GOOGLE_TV.equals(app.packageName)) {
            // discovery+ has separate Google TV and Fire TV artifacts. The
            // matcher decides from each artifact's declared capabilities;
            // provenance alone must not become a local platform blocker.
            return false;
        }
        return GOOGLE_NATIVE_PACKAGES.contains(app.packageName)
                && !profile.isGoogleReady();
    }

    public static boolean requiresProtectedVideo(CatalogApp app) {
        return PROTECTED_VIDEO_PACKAGES.contains(app.packageName);
    }

    private static boolean hasUsableStore(DeviceProfile profile) {
        return profile.amazonStore
                || profile.auroraStore
                || profile.googlePlay
                || profile.xiaomiStore;
    }
}
