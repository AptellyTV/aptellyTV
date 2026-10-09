package app.aptelly.tv.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.DialogInterface;
import app.aptelly.tv.R;
import app.aptelly.tv.catalog.CatalogApp;
import app.aptelly.tv.install.AppSetupRequirements;

public final class AppSetupGuide {
    private AppSetupGuide() {}
    public static String preparation(Activity activity, String packageName) {
        switch (AppSetupRequirements.forPackage(packageName)) {
            case SERVER: return activity.getString(R.string.setup_jellyfin);
            case COMPUTER: return activity.getString(R.string.setup_moonlight);
            case VPN_CONFIG: return activity.getString(R.string.setup_vpn);
            case NETWORK_ACCOUNT: return activity.getString(R.string.setup_tailscale);
            case SERVICE_ACCOUNT: return activity.getString(R.string.setup_service_account);
            default: return "";
        }
    }

    public static void installed(Activity activity, CatalogApp app, String actualPackage) {
        if (activity.isFinishing() || activity.isDestroyed()) return;
        Intent intent = activity.getPackageManager().getLeanbackLaunchIntentForPackage(actualPackage);
        if (intent == null) intent = activity.getPackageManager().getLaunchIntentForPackage(actualPackage);
        if (intent == null) {
            TvMessageDialog.showInstallError(activity, activity.getString(R.string.install_verified_no_entry));
            return;
        }
        final Intent launch = intent;
        String steps = preparation(activity, app.packageName);
        String message = activity.getString(R.string.install_session_success, app.name);
        if (!steps.isEmpty()) message += "\n\n" + steps;
        AlertDialog dialog = new AlertDialog.Builder(activity).setTitle(app.name).setMessage(message)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.open, (ignored, which) -> {
                    try { activity.startActivity(launch); }
                    catch (android.content.ActivityNotFoundException missing) {
                        TvMessageDialog.showInstallError(activity, activity.getString(R.string.install_verified_no_entry));
                    }
                }).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(DialogInterface.BUTTON_POSITIVE).requestFocus());
        dialog.show();
    }
}
