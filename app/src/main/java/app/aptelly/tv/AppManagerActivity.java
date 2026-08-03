package app.aptelly.tv;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.ContextCompat;

import app.aptelly.tv.catalog.AppCatalog;
import app.aptelly.tv.catalog.AppCompatibility;
import app.aptelly.tv.catalog.CatalogAvailability;
import app.aptelly.tv.catalog.CatalogAvailabilityClient;
import app.aptelly.tv.catalog.CatalogApp;
import app.aptelly.tv.catalog.InstalledAppResolver;
import app.aptelly.tv.device.DeviceProfile;
import app.aptelly.tv.install.MatchingApiClient;
import app.aptelly.tv.install.PendingInstallStore;
import app.aptelly.tv.install.SecurePackageInstaller;
import app.aptelly.tv.ui.AmbientBackgroundView;
import app.aptelly.tv.ui.TvMessageDialog;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class AppManagerActivity extends Activity {
    public static final String EXTRA_FOCUS_PACKAGE = "focus_package";

    private final ExecutorService updateExecutor = Executors.newFixedThreadPool(2);
    private ScrollView screenScroll;
    private LinearLayout content;
    private SecurePackageInstaller packageInstaller;
    private DeviceProfile deviceProfile;
    private String focusPackage;
    private boolean focusRequested;
    private boolean availabilityRequested;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        deviceProfile = DeviceProfile.detect(this);
        CatalogAvailability.configure(deviceProfile);
        packageInstaller = new SecurePackageInstaller(this);
        focusPackage = getIntent().getStringExtra(EXTRA_FOCUS_PACKAGE);
        setContentView(buildScreen());
        hideSystemBars();
    }

    @Override
    protected void onResume() {
        super.onResume();
        hideSystemBars();
        packageInstaller.onHostResume();
        deviceProfile = deviceProfile == null
                ? DeviceProfile.detect(this)
                : deviceProfile.refreshDynamic(this);
        verifyPendingInstall();
        rebuild();
        if (!availabilityRequested) {
            availabilityRequested = true;
            CatalogAvailabilityClient.refresh(this, () -> {
                if (!isFinishing() && !isDestroyed()) rebuild();
            });
        }
    }

    @Override
    protected void onDestroy() {
        packageInstaller.shutdown();
        updateExecutor.shutdownNow();
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

        screenScroll = new ScrollView(this);
        screenScroll.setFillViewport(true);
        screenScroll.setClipToPadding(false);
        screenScroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        screenScroll.setPadding(dp(62), dp(32), dp(62), dp(60));

        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        screenScroll.addView(content, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT
        ));
        root.addView(screenScroll, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));
        return root;
    }

    private void rebuild() {
        content.removeAllViews();
        focusRequested = false;

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(14), dp(10), dp(14), dp(10));
        header.setBackground(glassBackground());

        Button back = actionButton(getString(R.string.back), false);
        back.setOnClickListener(view -> finish());
        header.addView(back, new LinearLayout.LayoutParams(dp(116), dp(48)));

        LinearLayout heading = new LinearLayout(this);
        heading.setOrientation(LinearLayout.VERTICAL);
        TextView title = text(getString(R.string.manage_apps), 30, Color.WHITE, true);
        TextView subtitle = text(
                getString(R.string.manage_apps_desc),
                15,
                Color.argb(205, 255, 255, 255),
                false
        );
        heading.addView(title);
        LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        subtitleParams.topMargin = dp(5);
        heading.addView(subtitle, subtitleParams);

        LinearLayout.LayoutParams headingParams =
                new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        headingParams.leftMargin = dp(24);
        header.addView(heading, headingParams);

        Button environment = actionButton(
                getString(R.string.environment_title),
                false
        );
        environment.setOnClickListener(view ->
                startActivity(new Intent(this, EnvironmentActivity.class)));
        header.addView(environment, new LinearLayout.LayoutParams(dp(190), dp(48)));

        LinearLayout.LayoutParams headerParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        headerParams.bottomMargin = dp(25);
        content.addView(header, headerParams);

        addGroup(getString(R.string.app_stores), AppCatalog.stores(), true);
        addGroup(getString(R.string.popular_apps), AppCatalog.popular());
        addGroup(
                getString(R.string.china_mainland_apps),
                AppCatalog.chinaMainlandServices(),
                true
        );
        addGroup(getString(R.string.global_apps), AppCatalog.globalServices());
        addGroup(getString(R.string.regional_apps), AppCatalog.regionalServices());
        addGroup(getString(R.string.optional_apps), AppCatalog.optionalMedia());
        addGroup(getString(R.string.network_tools), AppCatalog.privacy());

        if (!focusRequested) {
            back.postDelayed(() -> {
                screenScroll.scrollTo(0, 0);
                back.requestFocus();
            }, 160);
        }
    }

    private void addGroup(String title, List<CatalogApp> apps) {
        addGroup(title, apps, false);
    }

    private void addGroup(
            String title,
            List<CatalogApp> apps,
            boolean keepUnavailable
    ) {
        if (!keepUnavailable) {
            apps = CatalogAvailability.filter(apps);
        }
        if (apps.isEmpty()) return;
        TextView heading = text(title, 21, Color.WHITE, true);
        LinearLayout.LayoutParams headingParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        headingParams.topMargin = dp(13);
        headingParams.bottomMargin = dp(11);
        content.addView(heading, headingParams);

        for (CatalogApp app : apps) {
            content.addView(buildAppRow(app), rowParams());
        }
    }

    private View buildAppRow(CatalogApp app) {
        String installedPackage =
                InstalledAppResolver.installedPackage(this, app.packageName);
        boolean installed = installedPackage != null;
        boolean unavailable = !installed && isUnavailableOnCurrentTv(app);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(22), dp(15), dp(18), dp(15));

        GradientDrawable rowBackground = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{
                        tone(app.startColor, 0.28f, 215),
                        tone(app.endColor, 0.18f, 195)
                }
        );
        rowBackground.setCornerRadius(dp(18));
        rowBackground.setStroke(dp(1), Color.argb(42, 218, 237, 255));
        row.setBackground(rowBackground);

        View accent = new View(this);
        GradientDrawable accentBackground = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{app.startColor, app.endColor}
        );
        accentBackground.setCornerRadius(dp(3));
        accent.setBackground(accentBackground);
        LinearLayout.LayoutParams accentParams =
                new LinearLayout.LayoutParams(dp(4), dp(68));
        accentParams.rightMargin = dp(18);
        row.addView(accent, accentParams);

        FrameLayout iconShell = new FrameLayout(this);
        GradientDrawable iconBackground = new GradientDrawable();
        iconBackground.setColor(Color.argb(235, 249, 251, 255));
        iconBackground.setCornerRadius(dp(15));
        iconBackground.setStroke(dp(1), Color.argb(120, 255, 255, 255));
        iconShell.setBackground(iconBackground);
        iconShell.setPadding(dp(8), dp(8), dp(8), dp(8));
        ImageView icon = new ImageView(this);
        icon.setImageDrawable(iconFor(app));
        icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        iconShell.addView(icon, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));
        row.addView(iconShell, new LinearLayout.LayoutParams(dp(70), dp(70)));

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setGravity(Gravity.CENTER_VERTICAL);

        TextView name = text(app.name, 19, Color.WHITE, true);
        copy.addView(name);

        TextView description = text(
                getString(app.descriptionRes),
                13,
                Color.argb(210, 255, 255, 255),
                false
        );
        description.setMaxLines(2);
        LinearLayout.LayoutParams descriptionParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        descriptionParams.topMargin = dp(4);
        copy.addView(description, descriptionParams);

        String state = installed
                ? getString(R.string.installed)
                : unavailable
                        ? getString(R.string.unavailable_on_this_tv)
                        : getString(R.string.not_installed);
        if (app.recommended) {
            state += "  •  " + getString(R.string.recommended);
        }
        if (!app.isPlatformStore()) {
            state += "  •  " + getString(R.string.free_app);
        }
        if (app.isBundledInstall()) {
            state += "  •  " + getString(R.string.bundled_offline);
        }
        state += "  •  " + getString(
                isTvReady(app, installed)
                        ? R.string.tv_ready
                        : R.string.not_tv_optimized
        );
        state += "  •  " + getString(unavailable
                ? R.string.compat_not_available_for_device
                : AppCompatibility.statusResource(app, deviceProfile, installed));
        TextView status = text(state, 11, Color.argb(190, 255, 255, 255), true);
        status.setSingleLine(true);
        status.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        statusParams.topMargin = dp(7);
        copy.addView(status, statusParams);

        LinearLayout.LayoutParams copyParams =
                new LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        1
                );
        copyParams.leftMargin = dp(22);
        copyParams.rightMargin = dp(22);
        row.addView(copy, copyParams);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.VERTICAL);
        actions.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);

        LinearLayout primaryActions = new LinearLayout(this);
        primaryActions.setOrientation(LinearLayout.HORIZONTAL);
        primaryActions.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        actions.addView(primaryActions, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(50)
        ));

        LinearLayout secondaryActions = new LinearLayout(this);
        secondaryActions.setOrientation(LinearLayout.HORIZONTAL);
        secondaryActions.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        LinearLayout.LayoutParams secondaryParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(50)
        );
        secondaryParams.topMargin = dp(8);
        actions.addView(secondaryActions, secondaryParams);

        Button primary = actionButton(
                getString(installed
                        ? R.string.open
                        : unavailable
                                ? R.string.unavailable_on_this_tv
                        : app.supportsOneClickInstall()
                                ? R.string.install
                                : R.string.not_adapted),
                true
        );
        primary.setOnClickListener(view -> handlePrimary(app));
        primaryActions.addView(primary, new LinearLayout.LayoutParams(dp(140), dp(50)));

        if (installed) {
            if (isDirectSource(app) && !app.isBundledInstall()) {
                Button directUpdate = actionButton(getString(R.string.update), false);
                directUpdate.setOnClickListener(view -> checkForUpdate(app));
                LinearLayout.LayoutParams updateParams =
                        new LinearLayout.LayoutParams(dp(140), dp(50));
                updateParams.leftMargin = dp(12);
                primaryActions.addView(directUpdate, updateParams);
            }

            if (!usesSharedRuntime(app)) {
                Button remove = actionButton(getString(R.string.uninstall), false);
                remove.setOnClickListener(view -> requestUninstall(app));
                LinearLayout.LayoutParams removeParams =
                        new LinearLayout.LayoutParams(dp(140), dp(50));
                removeParams.leftMargin = dp(12);
                secondaryActions.addView(remove, removeParams);
            }

        }

        row.addView(actions, new LinearLayout.LayoutParams(
                dp(444),
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        if (!focusRequested && app.packageName.equals(focusPackage)) {
            focusRequested = true;
            primary.postDelayed(() -> {
                if (!primary.requestFocus()) {
                    primary.requestFocusFromTouch();
                }
                screenScroll.smoothScrollTo(0, Math.max(0, row.getTop() - dp(180)));
            }, 170);
        }
        return row;
    }

    private void handlePrimary(CatalogApp app) {
        Intent launch = InstalledAppResolver.launchIntent(this, app.packageName);
        if (launch != null) {
            startActivity(launch);
            return;
        }

        if (isInstalled(app.packageName)) {
            Toast.makeText(
                    this,
                    R.string.install_verified_no_entry,
                    Toast.LENGTH_LONG
            ).show();
            return;
        }

        if (isUnavailableOnCurrentTv(app)) {
            Toast.makeText(
                    this,
                    R.string.catalog_unavailable_on_this_tv,
                    Toast.LENGTH_LONG
            ).show();
            return;
        }

        if (!app.supportsOneClickInstall()) {
            Toast.makeText(
                    this,
                    R.string.install_source_unverified,
                    Toast.LENGTH_LONG
            ).show();
            return;
        }

        rememberPendingInstall(app.packageName);
        installDirect(app, false);
    }

    private void installDirect(CatalogApp app, boolean allowUpdate) {
        if (!isInstalled(app.packageName)) {
            rememberPendingInstall(app.packageName);
        }
        SecurePackageInstaller.Listener listener = new SecurePackageInstaller.Listener() {
            @Override
            public void onStatus(String status) {
                Toast.makeText(AppManagerActivity.this, status, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onError(String message) {
                TvMessageDialog.showInstallError(AppManagerActivity.this, message);
            }
        };
        if (allowUpdate) {
            packageInstaller.install(app, listener);
        } else {
            packageInstaller.installIfMissing(app, listener);
        }
    }

    private void rememberPendingInstall(String packageName) {
        new PendingInstallStore(this).save(
                packageName,
                "",
                PendingInstallStore.State.REQUESTED,
                ""
        );
    }

    private void verifyPendingInstall() {
        PendingInstallStore store = new PendingInstallStore(this);
        PendingInstallStore.Task task = store.read();
        if (task == null) {
            return;
        }
        if (System.currentTimeMillis() - task.updatedAt > 24L * 60L * 60L * 1000L) {
            clearPendingInstall();
            return;
        }
        String installedPackage =
                InstalledAppResolver.installedPackage(this, task.packageName);
        if (installedPackage == null) {
            if (task.state == PendingInstallStore.State.FAILED
                    && task.message != null
                    && !task.message.isEmpty()) {
                TvMessageDialog.showInstallError(this, task.message);
                clearPendingInstall();
            }
            return;
        }

        Intent launcher = InstalledAppResolver.launchIntent(this, task.packageName);
        int message = launcher != null
                ? R.string.install_verified_tv
                : R.string.install_verified_no_entry;
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        clearPendingInstall();
    }

    private void clearPendingInstall() {
        new PendingInstallStore(this).clear();
    }

    private boolean isUnavailableOnCurrentTv(CatalogApp app) {
        return app.isPlatformStore()
                || !CatalogAvailability.isVisible(app.packageName);
    }

    private void requestUninstall(CatalogApp app) {
        String installedPackage =
                InstalledAppResolver.installedPackage(this, app.packageName);
        if (installedPackage == null) {
            return;
        }
        try {
            Intent intent = new Intent(
                    Intent.ACTION_DELETE,
                    Uri.parse("package:" + installedPackage)
            );
            startActivity(intent);
        } catch (ActivityNotFoundException exception) {
            Toast.makeText(this, R.string.no_apps, Toast.LENGTH_SHORT).show();
        }
    }

    private void checkForUpdate(CatalogApp app) {
        if (!isDirectSource(app)) {
            return;
        }
        Toast.makeText(
                this,
                getString(R.string.checking_update, app.name),
                Toast.LENGTH_SHORT
        ).show();
        updateExecutor.execute(() -> {
            try {
                String installedPackage =
                        InstalledAppResolver.installedPackage(this, app.packageName);
                if (installedPackage == null) {
                    runOnUiThread(() -> installDirect(app, false));
                    return;
                }
                PackageInfo current =
                        getPackageManager().getPackageInfo(installedPackage, 0);
                app.aptelly.tv.install.InstallPlan latest =
                        MatchingApiClient.resolve(this, app);
                boolean newer = app.source == CatalogApp.Source.FDROID_OPENVPN
                        ? latest.versionCode > installedVersionCode(current)
                        : compareVersions(latest.versionName, current.versionName) > 0;
                if (newer) {
                    runOnUiThread(() -> installDirect(app, true));
                } else {
                    runOnUiThread(() -> Toast.makeText(
                            this,
                            getString(R.string.up_to_date, app.name),
                            Toast.LENGTH_SHORT
                    ).show());
                }
            } catch (Exception exception) {
                boolean upToDate = exception instanceof
                        app.aptelly.tv.install.NoMatchingArtifactException
                        && "UP_TO_DATE".equals(
                        ((app.aptelly.tv.install.NoMatchingArtifactException) exception)
                                .reasonCode
                );
                runOnUiThread(() -> Toast.makeText(
                        this,
                        upToDate
                                ? getString(R.string.up_to_date, app.name)
                                : getString(
                                exception instanceof
                                        app.aptelly.tv.install.NoMatchingArtifactException
                                        ? R.string.install_source_unverified
                                        : R.string.network_not_validated
                        ),
                        Toast.LENGTH_LONG
                ).show());
            }
        });
    }

    private Drawable iconFor(CatalogApp app) {
        String installedPackage =
                InstalledAppResolver.installedPackage(this, app.packageName);
        try {
            ApplicationInfo info = getPackageManager().getApplicationInfo(
                    installedPackage == null ? app.packageName : installedPackage,
                    0
            );
            return info.loadIcon(getPackageManager());
        } catch (PackageManager.NameNotFoundException ignored) {
            return ContextCompat.getDrawable(this, app.iconRes);
        }
    }

    private boolean isInstalled(String packageName) {
        return InstalledAppResolver.installedPackage(this, packageName) != null;
    }

    private boolean isTvReady(CatalogApp app, boolean installed) {
        if (!installed) {
            return app.tvOptimized;
        }
        return InstalledAppResolver.launchIntent(this, app.packageName) != null;
    }

    private boolean isDirectSource(CatalogApp app) {
        return app.supportsOneClickInstall() && !usesSharedRuntime(app);
    }

    private boolean usesSharedRuntime(CatalogApp app) {
        return "com.google.android.youtube.tvkids".equals(app.packageName);
    }

    @SuppressWarnings("deprecation")
    private long installedVersionCode(PackageInfo info) {
        return android.os.Build.VERSION.SDK_INT >= 28
                ? info.getLongVersionCode()
                : info.versionCode;
    }

    private int compareVersions(String left, String right) {
        String[] leftParts = left == null ? new String[0] : left.split("[^0-9]+");
        String[] rightParts = right == null ? new String[0] : right.split("[^0-9]+");
        int count = Math.max(leftParts.length, rightParts.length);
        for (int index = 0; index < count; index++) {
            long leftValue = numberAt(leftParts, index);
            long rightValue = numberAt(rightParts, index);
            if (leftValue != rightValue) {
                return Long.compare(leftValue, rightValue);
            }
        }
        return 0;
    }

    private long numberAt(String[] values, int index) {
        if (index >= values.length || values[index].isEmpty()) {
            return 0;
        }
        try {
            return Long.parseLong(values[index]);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private LinearLayout.LayoutParams rowParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(140)
        );
        params.bottomMargin = dp(12);
        return params;
    }

    private Button actionButton(String label, boolean primary) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(13);
        button.setTextColor(Color.WHITE);
        button.setAllCaps(false);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setFocusable(true);
        button.setFocusableInTouchMode(true);
        button.setBackground(buttonBackground(primary));
        button.setOnFocusChangeListener((view, focused) -> {
            button.setTextColor(primary && focused
                    ? Color.rgb(20, 28, 42)
                    : Color.WHITE);
            view.animate()
                        .scaleX(focused ? 1.08f : 1f)
                        .scaleY(focused ? 1.08f : 1f)
                        .setDuration(130)
                        .start();
        });
        return button;
    }

    private StateListDrawable buttonBackground(boolean primary) {
        StateListDrawable states = new StateListDrawable();
        GradientDrawable focused = rounded(
                primary ? Color.WHITE : Color.argb(235, 107, 190, 255),
                Color.WHITE
        );
        GradientDrawable normal = rounded(
                primary ? Color.argb(100, 255, 255, 255) : Color.argb(46, 255, 255, 255),
                Color.argb(70, 255, 255, 255)
        );
        states.addState(new int[]{android.R.attr.state_focused}, focused);
        states.addState(new int[]{}, normal);
        return states;
    }

    private GradientDrawable glassBackground() {
        GradientDrawable value = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{
                        Color.argb(46, 255, 255, 255),
                        Color.argb(23, 255, 255, 255)
                }
        );
        value.setCornerRadius(dp(24));
        value.setStroke(dp(1), Color.argb(42, 226, 240, 255));
        return value;
    }

    private int tone(int color, float intensity, int alpha) {
        int red = Math.round(Color.red(color) * intensity + 7 * (1f - intensity));
        int green = Math.round(Color.green(color) * intensity + 13 * (1f - intensity));
        int blue = Math.round(Color.blue(color) * intensity + 27 * (1f - intensity));
        return Color.argb(
                alpha,
                Math.min(255, red),
                Math.min(255, green),
                Math.min(255, blue)
        );
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
        if (android.os.Build.VERSION.SDK_INT >= 30) {
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
