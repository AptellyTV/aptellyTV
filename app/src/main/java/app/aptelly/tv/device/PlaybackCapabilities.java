package app.aptelly.tv.device;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaDrm;
import android.hardware.display.DisplayManager;
import android.os.Build;
import android.view.Display;
import android.webkit.WebView;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.Arrays;
import java.util.TreeMap;
import java.util.UUID;

/** Android's reported capabilities are observations, never proof of successful playback. */
public final class PlaybackCapabilities {
    private PlaybackCapabilities() {}
    public static JSONObject collect(Context context) throws JSONException {
        JSONObject result = new JSONObject();
        result.put("reported_only", true);
        JSONArray decoders = new JSONArray();
        TreeMap<String, JSONObject> sorted = new TreeMap<>();
        try {
            for (MediaCodecInfo info : new MediaCodecList(MediaCodecList.ALL_CODECS).getCodecInfos()) {
                if (info.isEncoder()) continue;
                for (String mime : info.getSupportedTypes()) {
                    if (!Arrays.asList("video/avc", "video/hevc", "video/x-vnd.on2.vp9", "video/av01",
                            "video/dolby-vision").contains(mime)) continue;
                    try {
                        MediaCodecInfo.CodecCapabilities caps = info.getCapabilitiesForType(mime);
                        JSONObject decoder = new JSONObject();
                        decoder.put("name", info.getName());
                        decoder.put("mime", mime);
                        decoder.put("secure_supported", caps.isFeatureSupported(MediaCodecInfo.CodecCapabilities.FEATURE_SecurePlayback));
                        decoder.put("hardware_accelerated", Build.VERSION.SDK_INT >= 29
                                ? info.isHardwareAccelerated() : JSONObject.NULL);
                        sorted.put(info.getName() + "|" + mime, decoder);
                    } catch (RuntimeException brokenDecoder) { }
                }
            }
        } catch (RuntimeException unavailableCodecs) { }
        for (JSONObject decoder : sorted.values()) {
            if (decoders.length() == 64) break;
            decoders.put(decoder);
        }
        result.put("decoders", decoders);
        JSONArray hdr = new JSONArray();
        try {
            DisplayManager manager = (DisplayManager) context.getSystemService(Context.DISPLAY_SERVICE);
            Display display = manager == null ? null : manager.getDisplay(Display.DEFAULT_DISPLAY);
            if (display != null) {
                int[] types = display.getHdrCapabilities().getSupportedHdrTypes();
                Arrays.sort(types);
                for (int type : types) hdr.put(type);
            }
        } catch (RuntimeException unavailableDisplay) { }
        result.put("hdr_types", hdr);
        result.put("connected_hdcp_level", JSONObject.NULL);
        result.put("max_hdcp_level", JSONObject.NULL);
        MediaDrm drm = null;
        if (Build.VERSION.SDK_INT >= 28) try {
            drm = new MediaDrm(new UUID(0xedef8ba979d64aceL, 0xa3c827dcd51d21edL));
            result.put("connected_hdcp_level", drm.getConnectedHdcpLevel());
            result.put("max_hdcp_level", drm.getMaxHdcpLevel());
        } catch (Exception unavailableDrm) {
            // An unavailable provider or unsupported property remains unknown.
        } finally {
            if (drm != null) try { drm.close(); } catch (RuntimeException ignored) { }
        }
        return result;
    }

    public static JSONObject runtimeVersions(Context context) throws JSONException {
        JSONObject result = new JSONObject();
        for (String name : new String[]{"com.google.android.gms", "com.android.vending", "com.amazon.venezia"}) {
            try { result.put(name, version(context.getPackageManager().getPackageInfo(name, 0))); }
            catch (android.content.pm.PackageManager.NameNotFoundException | RuntimeException unknown) { }
        }
        try {
            PackageInfo provider = WebView.getCurrentWebViewPackage();
            if (provider != null) result.put("webview", version(provider));
        } catch (RuntimeException unavailableWebView) { }
        return result;
    }

    private static JSONObject version(PackageInfo info) throws JSONException {
        JSONObject value = new JSONObject();
        value.put("package_name", info.packageName);
        value.put("version_name", info.versionName == null ? "" : info.versionName);
        value.put("version_code", Build.VERSION.SDK_INT >= 28 ? info.getLongVersionCode() : info.versionCode);
        return value;
    }
}
