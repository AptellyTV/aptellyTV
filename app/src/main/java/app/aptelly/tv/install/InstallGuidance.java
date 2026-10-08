package app.aptelly.tv.install;

import android.content.Context;
import app.aptelly.tv.R;
import app.aptelly.tv.catalog.CatalogAvailability;
import java.util.Locale;

/** Stable, localized user actions; server diagnostics remain available in the status object. */
public final class InstallGuidance {
    private InstallGuidance() { }

    public static String message(Context context, String reason) {
        return context.getString(resource(reason));
    }

    static int resource(String reason) {
        String code = reason == null ? "" : reason.toUpperCase(Locale.ROOT);
        if (code.contains("SECURITY")) return R.string.match_security_blocked;
        if (code.contains("SIGNATURE")) return R.string.match_signer_blocked;
        if (code.contains("EXPIRED")) return R.string.match_qualification_expired;
        if (code.contains("GMS") || code.contains("PLAY_CERTIFICATION")) return R.string.match_google_required;
        if (code.contains("AMAZON_RUNTIME")) return R.string.match_amazon_required;
        if (code.contains("REGION")) return R.string.match_region_required;
        if (code.contains("PLAYBACK") || code.contains("CERTIFIED")
                || code.contains("QUALIFICATION") || code.equals("CAPABILITY_NOT_QUALIFIED")) {
            return R.string.match_function_not_qualified;
        }
        if (code.contains("RETIRED") || code.contains("VARIANT_INACTIVE")) return R.string.match_retired;
        if (code.contains("INSTALL_SET") || code.contains("INVALID_DIRECT")
                || code.contains("ARTIFACT_UNAVAILABLE")) return R.string.match_package_unavailable;
        if (code.contains("ABI") || code.contains("API_") || code.contains("MODEL")
                || code.contains("FIRMWARE") || code.contains("FEATURE")
                || code.contains("RUNTIME") || code.contains("CAPABILITY")
                || code.contains("WIDEVINE") || code.contains("PLATFORM")) return R.string.match_device_unsupported;
        if (code.equals("AMAZON_VARIANT_NOT_DIRECT")) return R.string.amazon_variant_not_direct;
        return R.string.match_no_source;
    }

    public static String catalogMessage(Context context, CatalogAvailability.Status status) {
        if (status == null) return "";
        if (status.functionResult.contains("installer_permission")
                || status.currentDeviceFit.equals("installer_core_function_blocked")
                || status.reasonCode.contains("INSTALLER_PERMISSION_LIMITED")) {
            return context.getString(R.string.match_store_permission_limited);
        }
        if (!status.installBlocker.isEmpty()) return message(context, status.installBlocker);
        if (!status.visible) return message(context, status.reasonCode);
        if (status.functionResult.startsWith("fail")
                || status.reasonCode.contains("QUALIFICATION_PENDING")
                || status.usageGate.contains("playback_required")
                || status.usageGate.contains("qualification_required")) {
            return context.getString(R.string.match_function_not_qualified);
        }
        if (status.usageGate.contains("subscription") || status.usageGate.contains("account")) {
            return context.getString(R.string.match_account_required);
        }
        return "";
    }
}
