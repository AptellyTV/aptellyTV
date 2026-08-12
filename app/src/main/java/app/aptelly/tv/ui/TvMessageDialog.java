package app.aptelly.tv.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;

import app.aptelly.tv.R;

public final class TvMessageDialog {
    private TvMessageDialog() {
    }

    public static void showInstallError(Activity activity, String message) {
        if (activity.isFinishing() || activity.isDestroyed()) {
            return;
        }
        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle(R.string.install_unavailable_title)
                .setMessage(message)
                .setPositiveButton(android.R.string.ok, null)
                .create();
        dialog.setOnShowListener(ignored -> {
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).requestFocus();
        });
        dialog.show();
    }

    public static void confirmInstall(
            Activity activity,
            String appName,
            Runnable onConfirm
    ) {
        if (activity.isFinishing() || activity.isDestroyed()) {
            return;
        }
        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle(activity.getString(R.string.install_confirm_title, appName))
                .setMessage(activity.getString(R.string.install_confirm_message, appName))
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.install, (ignored, which) -> onConfirm.run())
                .create();
        dialog.setOnShowListener(ignored ->
                dialog.getButton(DialogInterface.BUTTON_POSITIVE).requestFocus());
        dialog.show();
    }
}
