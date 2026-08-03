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
}
