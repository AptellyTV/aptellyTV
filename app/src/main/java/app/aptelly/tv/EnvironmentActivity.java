package app.aptelly.tv;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import app.aptelly.tv.catalog.AppCatalog;
import app.aptelly.tv.catalog.CatalogApp;
import app.aptelly.tv.device.DeviceProfile;
import app.aptelly.tv.device.DeviceProfilePayload;
import app.aptelly.tv.device.DeviceProfileReporter;
import app.aptelly.tv.device.TelemetryPreferences;
import app.aptelly.tv.install.SecurePackageInstaller;
import app.aptelly.tv.ui.TvMessageDialog;
import app.aptelly.tv.ui.AmbientBackgroundView;
import app.aptelly.tv.update.AptellyUpdateClient;

public final class EnvironmentActivity extends Activity {
    private LinearLayout content;
    private SecurePackageInstaller installer;
    private AptellyUpdateClient updateClient;
    private DeviceProfile profile;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        installer = new SecurePackageInstaller(this);
        updateClient = new AptellyUpdateClient(this);
        setContentView(buildScreen());
        hideSystemBars();
    }

    @Override
    protected void onResume() {
        super.onResume();
        hideSystemBars();
        installer.onHostResume();
        profile = DeviceProfile.detect(this);
        DeviceProfileReporter.reportIfChanged(this);
        rebuild();
    }

    @Override
    protected void onDestroy() {
        updateClient.shutdown();
        installer.shutdown();
        super.onDestroy();
    }

    private View buildScreen() {
        FrameLayout root = new FrameLayout(this);
        root.addView(
                new AmbientBackgroundView(this),
                new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                )
        );

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroll.setPadding(dp(72), dp(42), dp(72), dp(72));

        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(content, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT
        ));
        root.addView(scroll, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));
        return root;
    }

    private void rebuild() {
        content.removeAllViews();

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(14), dp(10), dp(14), dp(10));
        GradientDrawable headerBackground = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{
                        Color.argb(46, 255, 255, 255),
                        Color.argb(22, 255, 255, 255)
                }
        );
        headerBackground.setCornerRadius(dp(24));
        headerBackground.setStroke(dp(1), Color.argb(42, 226, 240, 255));
        header.setBackground(headerBackground);
        Button back = actionButton(getString(R.string.back), false);
        back.setOnClickListener(view -> finish());
        header.addView(back, new LinearLayout.LayoutParams(dp(120), dp(52)));

        LinearLayout titleGroup = new LinearLayout(this);
        titleGroup.setOrientation(LinearLayout.VERTICAL);
        TextView title = text(getString(R.string.environment_title), 30, Color.WHITE, true);
        TextView subtitle = text(
                getString(R.string.environment_subtitle),
                15,
                Color.argb(205, 255, 255, 255),
                false
        );
        titleGroup.addView(title);
        titleGroup.addView(subtitle);
        LinearLayout.LayoutParams titleParams =
                new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        titleParams.leftMargin = dp(24);
        header.addView(titleGroup, titleParams);
        LinearLayout.LayoutParams headerParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        headerParams.bottomMargin = dp(10);
        content.addView(header, headerParams);

        addSectionTitle(getString(R.string.environment_device));
        addStatus(
                getString(R.string.environment_platform),
                profile.platformName(),
                profile.manufacturer + " " + profile.model
                        + " · API " + profile.androidApi
                        + " · " + profile.primaryAbi,
                true
        );
        addStatus(
                getString(R.string.environment_profile),
                        profile.staticProfile.hardwareProfileId
                        + " / "
                        + currentEnvironmentRevision(),
                getString(R.string.environment_profile_desc),
                true
        );
        addStatus(
                getString(R.string.environment_resources),
                formatMegabytes(profile.staticProfile.totalMemoryBytes)
                        + " RAM · "
                        + formatMegabytes(profile.dynamicProfile.availableStorageBytes)
                        + " free",
                getString(R.string.environment_resources_desc),
                profile.dynamicProfile.availableStorageBytes > 128L * 1024L * 1024L
        );
        addStatus(
                getString(R.string.environment_network),
                profile.dynamicProfile.networkStatus.kind.name()
                        + (profile.dynamicProfile.networkStatus.metered ? " · metered" : ""),
                getString(R.string.environment_network_desc),
                profile.dynamicProfile.networkStatus.canDownload()
        );
        addStatus(
                getString(R.string.environment_stores),
                detectedStores(),
                getString(R.string.environment_stores_desc),
                true
        );
        addStatus(
                getString(R.string.environment_installer),
                getString(profile.packageInstaller
                        ? R.string.environment_ready
                        : R.string.environment_missing),
                getString(R.string.environment_installer_desc),
                profile.packageInstaller
        );
        addStatus(
                getString(R.string.environment_permission),
                getString(profile.unknownSourcesAllowed
                        ? R.string.environment_ready
                        : R.string.environment_action_needed),
                getString(R.string.environment_permission_desc),
                profile.unknownSourcesAllowed
        );
        if (!profile.unknownSourcesAllowed) {
            Button permission = actionButton(
                    getString(R.string.environment_grant_permission),
                    true
            );
            permission.setOnClickListener(view -> openInstallPermission());
            addAction(permission);
        }

        addSectionTitle(getString(R.string.telemetry_section));
        boolean telemetryEnabled = TelemetryPreferences.isEnabled(this);
        boolean deletionPending = TelemetryPreferences.hasPendingDeletion(this);
        addStatus(
                getString(R.string.telemetry_title),
                getString(telemetryEnabled
                        ? R.string.telemetry_enabled
                        : deletionPending
                                ? R.string.telemetry_deletion_pending
                                : R.string.telemetry_disabled),
                getString(R.string.telemetry_description),
                !deletionPending
        );
        Button telemetry = actionButton(
                getString(telemetryEnabled
                        ? R.string.telemetry_disable_delete
                        : deletionPending
                                ? R.string.telemetry_retry_delete
                                : R.string.telemetry_enable),
                !telemetryEnabled
        );
        telemetry.setOnClickListener(view -> {
            if (telemetryEnabled || deletionPending) {
                disableTelemetryAndDelete();
            } else {
                showTelemetryConsent();
            }
        });
        addAction(telemetry);

        addSectionTitle(getString(R.string.aptelly_update_section));
        addStatus(
                getString(R.string.aptelly_update_title),
                BuildConfig.VERSION_NAME + " (" + BuildConfig.VERSION_CODE + ")",
                getString(R.string.aptelly_update_description),
                true
        );
        Button checkUpdate = actionButton(getString(R.string.aptelly_update_check), false);
        checkUpdate.setOnClickListener(view -> {
            checkUpdate.setEnabled(false);
            updateClient.checkAndInstall((status, detail) -> {
                int message;
                switch (status) {
                    case CHECKING:
                        message = R.string.aptelly_update_checking;
                        break;
                    case NOT_CONFIGURED:
                        message = R.string.aptelly_update_not_configured;
                        checkUpdate.setEnabled(true);
                        break;
                    case UP_TO_DATE:
                        message = R.string.aptelly_update_up_to_date;
                        checkUpdate.setEnabled(true);
                        break;
                    case DOWNLOADING:
                        message = R.string.aptelly_update_downloading;
                        break;
                    case READY_TO_INSTALL:
                        message = R.string.aptelly_update_ready;
                        checkUpdate.setEnabled(true);
                        break;
                    default:
                        message = R.string.aptelly_update_failed;
                        checkUpdate.setEnabled(true);
                }
                Toast.makeText(this, message, Toast.LENGTH_LONG).show();
            });
        });
        addAction(checkUpdate);

        addSectionTitle(getString(R.string.environment_bootstrap));
        addBootstrapAction(AppCatalog.stores().get(0));
        addBootstrapAction(AppCatalog.privacy().get(0));

        addSectionTitle(getString(R.string.environment_runtime));
        addStatus(
                getString(R.string.environment_google),
                getString(profile.isGoogleReady()
                        ? R.string.environment_detected
                        : R.string.environment_not_present),
                getString(R.string.environment_google_desc),
                profile.isGoogleReady()
        );
        addStatus(
                getString(R.string.environment_drm),
                "Widevine " + profile.widevineLevel,
                getString(profile.hasHardwareWidevine()
                        ? R.string.environment_drm_l1_desc
                        : R.string.environment_drm_limited_desc),
                profile.hasHardwareWidevine()
        );
        addStatus(
                getString(R.string.environment_webview),
                getString(profile.systemWebView
                        ? R.string.environment_ready
                        : R.string.environment_missing),
                getString(R.string.environment_webview_desc),
                profile.systemWebView
        );
        addStatus(
                getString(R.string.environment_web_runtime),
                getString(profile.systemWebView
                        ? R.string.environment_ready
                        : R.string.environment_missing),
                getString(R.string.environment_web_runtime_desc),
                profile.systemWebView
        );

        Button settings = actionButton(getString(R.string.environment_system_settings), false);
        settings.setOnClickListener(view -> openSystemSettings());
        addAction(settings);

        back.postDelayed(back::requestFocus, 160);
    }

    private String detectedStores() {
        StringBuilder value = new StringBuilder();
        appendDetected(value, "Google Play", profile.googlePlay);
        appendDetected(value, "Amazon", profile.amazonStore);
        appendDetected(value, "Aurora", profile.auroraStore);
        appendDetected(value, "Xiaomi", profile.xiaomiStore);
        return value.length() == 0 ? getString(R.string.environment_not_present) : value.toString();
    }

    private String currentEnvironmentRevision() {
        try {
            return DeviceProfilePayload.collect(this)
                    .optString("environment_revision", "unknown");
        } catch (Exception ignored) {
            return "unknown";
        }
    }

    private void appendDetected(StringBuilder value, String label, boolean detected) {
        if (!detected) return;
        if (value.length() > 0) value.append(" · ");
        value.append(label);
    }

    private String formatMegabytes(long bytes) {
        return (bytes / (1024L * 1024L)) + " MB";
    }

    private void addBootstrapAction(CatalogApp app) {
        boolean installed = isInstalled(app.packageName);
        addStatus(
                app.name,
                getString(installed
                        ? R.string.installed
                        : app.isBundledInstall()
                                ? R.string.bundled_offline
                                : R.string.official_download),
                getString(app.descriptionRes),
                installed
        );
        Button action = actionButton(
                getString(installed ? R.string.open : R.string.install),
                true
        );
        action.setOnClickListener(view -> {
            if (isInstalled(app.packageName)) {
                launch(app.packageName);
            } else {
                installer.installIfMissing(app, new SecurePackageInstaller.Listener() {
                    @Override
                    public void onStatus(String status) {
                        Toast.makeText(
                                EnvironmentActivity.this,
                                status,
                                Toast.LENGTH_SHORT
                        ).show();
                    }

                    @Override
                    public void onError(String message) {
                        TvMessageDialog.showInstallError(
                                EnvironmentActivity.this,
                                message
                        );
                    }
                });
            }
        });
        addAction(action);
    }

    private void addSectionTitle(String value) {
        TextView heading = text(value, 21, Color.WHITE, true);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.topMargin = dp(28);
        params.bottomMargin = dp(10);
        content.addView(heading, params);
    }

    private void addStatus(
            String title,
            String value,
            String description,
            boolean ready
    ) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(24), dp(16), dp(24), dp(16));
        GradientDrawable background = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{
                        ready
                                ? Color.argb(105, 15, 66, 65)
                                : Color.argb(112, 84, 43, 37),
                        Color.argb(82, 12, 22, 39)
                }
        );
        background.setCornerRadius(dp(16));
        background.setStroke(
                dp(1),
                ready
                        ? Color.argb(82, 105, 245, 208)
                        : Color.argb(82, 255, 183, 118)
        );
        row.setBackground(background);

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.addView(text(title, 17, Color.WHITE, true));
        TextView details = text(
                description,
                13,
                Color.argb(200, 255, 255, 255),
                false
        );
        details.setMaxLines(2);
        copy.addView(details);
        row.addView(copy, new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1
        ));

        TextView state = text(
                value,
                15,
                ready ? Color.rgb(151, 255, 213) : Color.rgb(255, 204, 145),
                true
        );
        state.setGravity(Gravity.END);
        row.addView(state, new LinearLayout.LayoutParams(dp(430), dp(52)));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.bottomMargin = dp(9);
        content.addView(row, params);
    }

    private void addAction(Button button) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(300), dp(54));
        params.topMargin = dp(7);
        params.bottomMargin = dp(8);
        content.addView(button, params);
    }

    private void openInstallPermission() {
        if (Build.VERSION.SDK_INT < 26) {
            return;
        }
        try {
            startActivity(new Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + getPackageName())
            ));
        } catch (ActivityNotFoundException exception) {
            openSystemSettings();
        }
    }

    private void showTelemetryConsent() {
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.telemetry_consent_title)
                .setMessage(R.string.telemetry_consent_message)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.telemetry_enable, (ignored, which) -> {
                    DeviceProfileReporter.enableAndReport(this);
                    Toast.makeText(this, R.string.telemetry_enabled_notice, Toast.LENGTH_SHORT)
                            .show();
                    rebuild();
                })
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .requestFocus());
        dialog.show();
    }

    private void disableTelemetryAndDelete() {
        DeviceProfileReporter.disableAndDelete(this, deleted -> {
            Toast.makeText(
                    this,
                    deleted
                            ? R.string.telemetry_deleted_notice
                            : R.string.telemetry_delete_pending_notice,
                    Toast.LENGTH_LONG
            ).show();
            rebuild();
        });
    }

    private void openSystemSettings() {
        try {
            startActivity(new Intent(Settings.ACTION_SETTINGS));
        } catch (ActivityNotFoundException ignored) {
            Toast.makeText(this, R.string.no_apps, Toast.LENGTH_SHORT).show();
        }
    }

    private boolean isInstalled(String packageName) {
        try {
            getPackageManager().getPackageInfo(packageName, 0);
            return true;
        } catch (PackageManager.NameNotFoundException ignored) {
            return false;
        }
    }

    private void launch(String packageName) {
        Intent intent = getPackageManager().getLeanbackLaunchIntentForPackage(packageName);
        if (intent == null) {
            intent = getPackageManager().getLaunchIntentForPackage(packageName);
        }
        if (intent != null) {
            startActivity(intent);
        }
    }

    private Button actionButton(String label, boolean primary) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(14);
        button.setTextColor(Color.WHITE);
        button.setAllCaps(false);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setFocusable(true);
        button.setBackground(buttonBackground(primary));
        button.setOnFocusChangeListener((view, focused) -> {
            button.setTextColor(primary && focused
                    ? Color.rgb(20, 28, 42)
                    : Color.WHITE);
            view.animate()
                    .scaleX(focused ? 1.06f : 1f)
                    .scaleY(focused ? 1.06f : 1f)
                    .setDuration(130)
                    .start();
        });
        return button;
    }

    private StateListDrawable buttonBackground(boolean primary) {
        StateListDrawable states = new StateListDrawable();
        states.addState(
                new int[]{android.R.attr.state_focused},
                rounded(
                        primary ? Color.WHITE : Color.rgb(107, 190, 255),
                        Color.WHITE
                )
        );
        states.addState(
                new int[]{},
                rounded(
                        primary
                                ? Color.argb(100, 255, 255, 255)
                                : Color.argb(46, 255, 255, 255),
                        Color.argb(70, 255, 255, 255)
                )
        );
        return states;
    }

    private GradientDrawable rounded(int fill, int stroke) {
        GradientDrawable value = new GradientDrawable();
        value.setColor(fill);
        value.setCornerRadius(dp(24));
        value.setStroke(dp(1), stroke);
        return value;
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) {
            view.setTypeface(Typeface.DEFAULT_BOLD);
        }
        return view;
    }

    private void hideSystemBars() {
        if (Build.VERSION.SDK_INT >= 30) {
            View decor = getWindow().getDecorView();
            WindowInsetsController controller = decor.getWindowInsetsController();
            if (controller != null) {
                controller.hide(WindowInsets.Type.statusBars());
                controller.setSystemBarsBehavior(
                        WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                );
            }
        } else {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            );
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
