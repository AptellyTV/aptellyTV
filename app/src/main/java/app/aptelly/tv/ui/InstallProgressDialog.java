package app.aptelly.tv.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.view.KeyEvent;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import app.aptelly.tv.R;
import app.aptelly.tv.catalog.CatalogApp;
import app.aptelly.tv.install.SecurePackageInstaller;
import java.util.Locale;

/** One remote-operable download panel shared by home and app management. */
public final class InstallProgressDialog {
    private final Activity activity;
    private final SecurePackageInstaller installer;
    private AlertDialog dialog;
    private TextView status;
    private TextView amount;
    private ProgressBar progress;

    public InstallProgressDialog(Activity activity, SecurePackageInstaller installer) {
        this.activity = activity;
        this.installer = installer;
    }

    public SecurePackageInstaller.Listener listener(CatalogApp app) {
        return new SecurePackageInstaller.Listener() {
            @Override public void onDownloadStarted() { show(app); }
            @Override public void onStatus(String message) {
                if (!alive()) return;
                if (dialog != null) status.setText(message);
                else Toast.makeText(activity, message, Toast.LENGTH_SHORT).show();
            }
            @Override public void onDownloadProgress(long received, long total) {
                if (!alive() || dialog == null) return;
                progress.setIndeterminate(total <= 0);
                if (total > 0) {
                    int percent = (int) Math.min(100, received * 100 / total);
                    progress.setProgress(percent);
                    amount.setText(activity.getString(R.string.install_progress_known,
                            megabytes(received), megabytes(total), percent));
                } else amount.setText(activity.getString(R.string.install_progress_unknown, megabytes(received)));
            }
            @Override public void onDownloadFinished() { close(); }
            @Override public void onCancelled() {
                close();
                if (alive()) Toast.makeText(activity, R.string.install_download_cancelled, Toast.LENGTH_SHORT).show();
            }
            @Override public void onError(String message) {
                close();
                TvMessageDialog.showInstallError(activity, message);
            }
            @Override public void onInstalled(String actualPackage) {
                close();
                AppSetupGuide.installed(activity, app, actualPackage);
            }
        };
    }

    private void show(CatalogApp app) {
        if (!alive()) return;
        close();
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        int padding = (int) (24 * activity.getResources().getDisplayMetrics().density);
        content.setPadding(padding, padding, padding, padding);
        status = new TextView(activity);
        status.setTextSize(20);
        status.setText(activity.getString(R.string.downloading, app.name));
        content.addView(status);
        progress = new ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        progress.setIndeterminate(true);
        content.addView(progress, new LinearLayout.LayoutParams(-1, padding * 2));
        amount = new TextView(activity);
        amount.setTextSize(18);
        content.addView(amount);
        dialog = new AlertDialog.Builder(activity)
                .setTitle(app.name).setView(content)
                .setNegativeButton(android.R.string.cancel, null).create();
        dialog.setCancelable(false);
        dialog.setOnKeyListener((ignored, key, event) -> {
            if (key != KeyEvent.KEYCODE_BACK) return false;
            if (event.getAction() == KeyEvent.ACTION_UP) requestCancel();
            return true;
        });
        dialog.setOnShowListener(ignored -> {
            dialog.getButton(DialogInterface.BUTTON_NEGATIVE).setOnClickListener(view -> requestCancel());
            dialog.getButton(DialogInterface.BUTTON_NEGATIVE).requestFocus();
        });
        dialog.show();
    }

    private void requestCancel() {
        if (dialog == null || !installer.cancelDownload()) return;
        status.setText(R.string.install_download_cancelling);
        dialog.getButton(DialogInterface.BUTTON_NEGATIVE).setEnabled(false);
    }

    public void close() {
        if (dialog != null) {
            dialog.dismiss();
            dialog = null;
        }
    }

    private boolean alive() { return !activity.isFinishing() && !activity.isDestroyed(); }
    private static String megabytes(long bytes) {
        return String.format(Locale.getDefault(), "%.1f MB", bytes / (1024.0 * 1024.0));
    }
}
