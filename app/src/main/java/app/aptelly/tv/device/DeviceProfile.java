package app.aptelly.tv.device;

import android.content.Context;
import app.aptelly.tv.BuildConfig;

import java.util.Locale;

public final class DeviceProfile {
    public final StaticDeviceProfile staticProfile;
    public final DynamicDeviceProfile dynamicProfile;
    public final boolean hyperOs;
    public final boolean fireTv;
    public final boolean googlePlay;
    public final boolean googleServices;
    public final boolean amazonStore;
    public final boolean auroraStore;
    public final boolean xiaomiStore;
    public final boolean packageInstaller;
    public final boolean unknownSourcesAllowed;
    public final boolean systemWebView;
    public final String widevineLevel;
    public final String primaryAbi;
    public final int androidApi;
    public final String manufacturer;
    public final String model;

    private DeviceProfile(
            StaticDeviceProfile staticProfile,
            DynamicDeviceProfile dynamicProfile,
            boolean hyperOs,
            boolean fireTv,
            boolean googlePlay,
            boolean googleServices,
            boolean amazonStore,
            boolean auroraStore,
            boolean xiaomiStore,
            boolean packageInstaller,
            boolean unknownSourcesAllowed,
            boolean systemWebView,
            String widevineLevel,
            String primaryAbi,
            int androidApi,
            String manufacturer,
            String model
    ) {
        this.staticProfile = staticProfile;
        this.dynamicProfile = dynamicProfile;
        this.hyperOs = hyperOs;
        this.fireTv = fireTv;
        this.googlePlay = googlePlay;
        this.googleServices = googleServices;
        this.amazonStore = amazonStore;
        this.auroraStore = auroraStore;
        this.xiaomiStore = xiaomiStore;
        this.packageInstaller = packageInstaller;
        this.unknownSourcesAllowed = unknownSourcesAllowed;
        this.systemWebView = systemWebView;
        this.widevineLevel = widevineLevel;
        this.primaryAbi = primaryAbi;
        this.androidApi = androidApi;
        this.manufacturer = manufacturer;
        this.model = model;
    }

    public static DeviceProfile detect(Context context) {
        StaticDeviceProfile stable = StaticDeviceProfile.collect(context);
        DynamicDeviceProfile current = DynamicDeviceProfile.collect(context);
        return assemble(context, stable, current);
    }

    /** Refresh fields which can change while the app is backgrounded without probing DRM again. */
    public DeviceProfile refreshDynamic(Context context) {
        return assemble(context, staticProfile, DynamicDeviceProfile.collect(context));
    }

    private static DeviceProfile assemble(
            Context context,
            StaticDeviceProfile stable,
            DynamicDeviceProfile current
    ) {
        String maker = stable.manufacturer;
        String brand = stable.brand;
        String joined = (maker + " " + brand).toLowerCase(Locale.ROOT);
        boolean hyper = BuildConfig.FORCE_HYPER_OS
                || joined.contains("xiaomi")
                || joined.contains("redmi")
                || joined.contains("poco");
        boolean fire = joined.contains("amazon")
                || context.getPackageManager().hasSystemFeature(
                        "amazon.hardware.fire_tv"
                );
        return new DeviceProfile(
                stable,
                current,
                hyper,
                fire,
                current.googlePlay,
                current.googleServices,
                current.amazonStore,
                current.auroraStore,
                current.xiaomiStore,
                current.packageInstaller,
                current.unknownSourcesAllowed,
                current.systemWebView,
                stable.widevineLevel,
                stable.primaryAbi,
                stable.androidApi,
                maker,
                stable.model
        );
    }

    public boolean isGoogleReady() {
        return googlePlay && googleServices;
    }

    public boolean hasHardwareWidevine() {
        return "L1".equalsIgnoreCase(widevineLevel);
    }

    public String platformName() {
        if (fireTv) {
            return "Fire TV";
        }
        if (hyperOs) {
            return "Xiaomi HyperOS / Android";
        }
        if (isGoogleReady()) {
            return "Google TV / Android TV";
        }
        return "AOSP Android TV";
    }

}
