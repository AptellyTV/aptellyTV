package app.aptelly.tv;

import android.app.Activity;
import android.app.role.RoleManager;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.LauncherApps;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.ContextCompat;

import app.aptelly.tv.catalog.AppCatalog;
import app.aptelly.tv.catalog.AppCompatibility;
import app.aptelly.tv.catalog.CatalogAvailability;
import app.aptelly.tv.catalog.CatalogApp;
import app.aptelly.tv.catalog.InstalledAppResolver;
import app.aptelly.tv.catalog.InstalledAppPresentation;
import app.aptelly.tv.content.PosterFeedRepository;
import app.aptelly.tv.content.PosterScene;
import app.aptelly.tv.device.DeviceProfile;
import app.aptelly.tv.device.HomeController;
import app.aptelly.tv.install.SecurePackageInstaller;
import app.aptelly.tv.install.StoreInstallRouter;
import app.aptelly.tv.ui.CinematicBackdropView;
import app.aptelly.tv.ui.TvCardView;
import app.aptelly.tv.ui.TvMessageDialog;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class MainActivity extends Activity {
    public static final String EXTRA_FOCUS_PACKAGE = "focus_package";
    private enum CategoryKind {
        INSTALLED,
        CATALOG,
        SETTINGS
    }

    private static final class Category {
        final String key;
        final String title;
        final String subtitle;
        final CategoryKind kind;
        final List<CatalogApp> apps;

        Category(
                String key,
                String title,
                String subtitle,
                CategoryKind kind,
                List<CatalogApp> apps
        ) {
            this.key = key;
            this.title = title;
            this.subtitle = subtitle;
            this.kind = kind;
            this.apps = apps;
        }
    }

    private static final class InstalledAppEntry {
        final String packageName;
        final String label;
        final Drawable icon;

        InstalledAppEntry(String packageName, String label, Drawable icon) {
            this.packageName = packageName;
            this.label = label;
            this.icon = icon;
        }
    }

    private final Handler clockHandler = new Handler(Looper.getMainLooper());
    private final Map<String, TextView> navigationViews = new LinkedHashMap<>();
    private final Map<String, TvCardView> applicationCards = new LinkedHashMap<>();

    private CinematicBackdropView backdropView;
    private LinearLayout content;
    private TextView clockView;
    private Button heroPrimaryButton;
    private TextView heroEyebrowView;
    private TextView heroTitleView;
    private TextView heroFunctionView;
    private TextView heroFeatureView;
    private TextView heroAdvantageView;
    private PosterFeedRepository posterRepository;
    private SecurePackageInstaller packageInstaller;
    private DeviceProfile deviceProfile;
    private String selectedCategory = "installed";
    private String selectedPackage;
    private String selectedLabel;
    private View firstFocusable;
    private boolean hasResumed;
    private boolean contentDirty;
    private boolean restoreSidebarFocusOnResume;
    private boolean packageReceiverRegistered;
    private boolean awaitingExternalInstall;
    private String pendingExternalPackage;
    private boolean renderingCategory;

    private final BroadcastReceiver packageChangeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            PrimeVideoShortcutController.sync(context);
            TvAppShortcutController.sync(context);
            contentDirty = true;
            if (content == null) {
                return;
            }
            content.postDelayed(() -> {
                if (!contentDirty || isFinishing() || isDestroyed()) {
                    return;
                }
                String packageToRestore = selectedPackage;
                boolean restoreCardFocus = false;
                TvCardView previousCard = applicationCards.get(packageToRestore);
                if (previousCard != null) {
                    restoreCardFocus = previousCard.hasFocus();
                }
                contentDirty = false;
                renderCategory(selectedCategory, false);
                if (packageToRestore != null) {
                    selectApp(packageToRestore, null);
                    TvCardView refreshedCard = applicationCards.get(packageToRestore);
                    if (restoreCardFocus && refreshedCard != null) {
                        refreshedCard.post(refreshedCard::requestFocus);
                    }
                }
            }, 250);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        PrimeVideoShortcutController.sync(this);
        TvAppShortcutController.sync(this);
        deviceProfile = DeviceProfile.detect(this);
        CatalogAvailability.configure(deviceProfile);
        posterRepository = new PosterFeedRepository(this);
        packageInstaller = new SecurePackageInstaller(this);
        setContentView(buildScreen());
        registerPackageChangeReceiver();
        hideSystemBars();
        updateClock();
        posterRepository.start(() -> {
            updateBackdrop(category(selectedCategory));
        });
        handleFocusIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleFocusIntent(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        PrimeVideoShortcutController.sync(this);
        TvAppShortcutController.sync(this);
        if (deviceProfile != null) {
            deviceProfile = deviceProfile.refreshDynamic(this);
        }
        packageInstaller.onHostResume();
        hideSystemBars();
        if (!hasResumed) {
            hasResumed = true;
            requestSidebarFocus(selectedCategory);
        } else if (restoreSidebarFocusOnResume) {
            restoreSidebarFocusOnResume = false;
            contentDirty = false;
            selectedCategory = "installed";
            selectedPackage = null;
            renderCategory(selectedCategory, false);
            requestSidebarFocus(selectedCategory);
        } else if (contentDirty) {
            contentDirty = false;
            renderCategory(selectedCategory, false);
        }
        if (awaitingExternalInstall) {
            awaitingExternalInstall = false;
            if (pendingExternalPackage != null
                    && !isInstalled(pendingExternalPackage)) {
                Toast.makeText(
                        this,
                        R.string.install_not_completed,
                        Toast.LENGTH_LONG
                ).show();
            }
            pendingExternalPackage = null;
        }
    }

    @Override
    protected void onDestroy() {
        clockHandler.removeCallbacksAndMessages(null);
        posterRepository.close();
        packageInstaller.shutdown();
        if (packageReceiverRegistered) {
            unregisterReceiver(packageChangeReceiver);
            packageReceiverRegistered = false;
        }
        super.onDestroy();
    }

    private void handleFocusIntent(Intent intent) {
        if (intent == null) {
            return;
        }
        String packageName = intent.getStringExtra(EXTRA_FOCUS_PACKAGE);
        if (packageName == null || packageName.isEmpty()) {
            return;
        }
        intent.removeExtra(EXTRA_FOCUS_PACKAGE);
        content.post(() -> openAppManager(packageName));
    }

    private View buildScreen() {
        FrameLayout root = new FrameLayout(this);
        backdropView = new CinematicBackdropView(this);
        root.addView(backdropView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));

        LinearLayout shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.HORIZONTAL);
        shell.setPadding(dp(28), dp(18), dp(32), dp(18));
        root.addView(shell, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));

        shell.addView(buildSidebar(), new LinearLayout.LayoutParams(dp(224),
                LinearLayout.LayoutParams.MATCH_PARENT));

        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams contentParams = new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.MATCH_PARENT,
                1
        );
        contentParams.leftMargin = dp(28);
        shell.addView(content, contentParams);

        renderCategory(selectedCategory, false);
        return root;
    }

    private View buildSidebar() {
        LinearLayout sidebar = new LinearLayout(this);
        sidebar.setOrientation(LinearLayout.VERTICAL);
        sidebar.setPadding(dp(14), dp(14), dp(14), dp(14));
        sidebar.setBackground(sidebarBackground());
        sidebar.setElevation(dp(18));

        LinearLayout profile = new LinearLayout(this);
        profile.setGravity(Gravity.CENTER_VERTICAL);
        ImageView mark = new ImageView(this);
        mark.setImageResource(R.drawable.aptelly_tv_launcher);
        mark.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        profile.addView(mark, new LinearLayout.LayoutParams(dp(42), dp(42)));

        LinearLayout identity = new LinearLayout(this);
        identity.setOrientation(LinearLayout.VERTICAL);
        TextView brand = text(getString(R.string.brand_intro_name), 13, Color.WHITE, true);
        brand.setLetterSpacing(0.11f);
        identity.addView(brand);
        TextView mode = text(
                deviceProfile.hyperOs ? "HYPEROS" : getString(R.string.portal_mode),
                9,
                Color.argb(160, 220, 235, 250),
                true
        );
        mode.setLetterSpacing(0.12f);
        identity.addView(mode);
        LinearLayout.LayoutParams identityParams = new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1
        );
        identityParams.leftMargin = dp(11);
        profile.addView(identity, identityParams);
        LinearLayout.LayoutParams profileParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(50)
        );
        profileParams.bottomMargin = dp(8);
        sidebar.addView(profile, profileParams);

        List<TextView> sidebarItems = new ArrayList<>();
        for (Category item : categories()) {
            TextView navigation = navigationItem(item);
            navigation.setId(View.generateViewId());
            navigationViews.put(item.key, navigation);
            sidebarItems.add(navigation);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp(39)
            );
            params.bottomMargin = dp(3);
            sidebar.addView(navigation, params);
        }
        for (int index = 0; index < sidebarItems.size(); index++) {
            TextView navigation = sidebarItems.get(index);
            TextView up = sidebarItems.get(Math.max(0, index - 1));
            TextView down = sidebarItems.get(Math.min(sidebarItems.size() - 1, index + 1));
            navigation.setNextFocusUpId(up.getId());
            navigation.setNextFocusDownId(down.getId());
        }

        View spacer = new View(this);
        sidebar.addView(spacer, new LinearLayout.LayoutParams(1, 0, 1));
        return sidebar;
    }

    private TextView navigationItem(Category category) {
        TextView item = text(navigationSymbol(category.key) + "   " + category.title,
                13, Color.argb(210, 245, 248, 255), false);
        item.setGravity(Gravity.CENTER_VERTICAL);
        item.setPadding(dp(15), 0, dp(12), 0);
        item.setFocusable(true);
        item.setFocusableInTouchMode(true);
        item.setClickable(true);
        item.setSingleLine(true);
        item.setBackground(navigationBackground(
                category.key.equals(selectedCategory),
                false
        ));
        item.setOnClickListener(view -> renderCategory(category.key, false));
        item.setOnFocusChangeListener((view, focused) -> {
            if (focused && !category.key.equals(selectedCategory)) {
                renderCategory(category.key, false);
            }
            styleNavigation(item, category.key, focused);
            view.animate()
                    .scaleX(focused ? 1.025f : 1f)
                    .scaleY(focused ? 1.025f : 1f)
                    .setDuration(130)
                    .start();
        });
        return item;
    }

    private void renderCategory(String key, boolean requestContentFocus) {
        if (content == null || renderingCategory) {
            return;
        }
        renderingCategory = true;
        try {
        Category current = category(key);
        selectedCategory = current.key;
        heroPrimaryButton = null;
        heroEyebrowView = null;
        heroTitleView = null;
        heroFunctionView = null;
        heroFeatureView = null;
        heroAdvantageView = null;
        content.removeAllViews();
        applicationCards.clear();
        firstFocusable = null;

        for (Map.Entry<String, TextView> entry : navigationViews.entrySet()) {
            styleNavigation(
                    entry.getValue(),
                    entry.getKey(),
                    entry.getValue().hasFocus()
            );
        }

        List<InstalledAppEntry> installedApps = current.kind == CategoryKind.INSTALLED
                ? launchableApps()
                : new ArrayList<>();
        if (current.kind == CategoryKind.INSTALLED && !installedApps.isEmpty()) {
            selectApp(
                    installedApps.get(0).packageName,
                    installedApps.get(0).label
            );
        } else if (current.kind == CategoryKind.CATALOG && !current.apps.isEmpty()) {
            selectApp(current.apps.get(0).packageName, current.apps.get(0).name);
        } else {
            selectApp(null, null);
        }

        updateBackdrop(current);
        content.addView(buildTopBar(current), new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(34)
        ));
        LinearLayout.LayoutParams heroParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(210)
        );
        heroParams.topMargin = dp(8);
        heroParams.bottomMargin = dp(10);
        content.addView(buildHero(current, installedApps), heroParams);

        if (current.kind == CategoryKind.SETTINGS) {
            content.addView(buildSettingsShelf());
        } else {
            content.addView(buildApplicationShelf(current, installedApps));
        }

        content.addView(new View(this), new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1
        ));
        content.addView(buildHomeRiskNotice(), new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(44)
        ));

        if (requestContentFocus && firstFocusable != null) {
            firstFocusable.postDelayed(firstFocusable::requestFocus, 170);
        }
        configureContentFocusGraph(current);
        } finally {
            renderingCategory = false;
        }
    }

    private View buildHomeRiskNotice() {
        TextView notice = text(
                getString(R.string.home_risk_notice),
                9,
                Color.argb(158, 224, 233, 246),
                false
        );
        notice.setGravity(Gravity.CENTER_VERTICAL);
        notice.setPadding(dp(14), 0, dp(14), 0);
        notice.setMaxLines(2);
        notice.setEllipsize(android.text.TextUtils.TruncateAt.END);
        notice.setBackground(rounded(
                Color.argb(20, 255, 255, 255),
                Color.argb(28, 255, 255, 255),
                12
        ));
        notice.setFocusable(false);
        notice.setClickable(false);
        return notice;
    }

    private View buildTopBar(Category category) {
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        TextView section = text(category.title, 14, Color.WHITE, true);
        bar.addView(section, new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.MATCH_PARENT,
                1
        ));

        clockView = text("", 13, Color.argb(220, 255, 255, 255), false);
        clockView.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        bar.addView(clockView, new LinearLayout.LayoutParams(dp(198),
                LinearLayout.LayoutParams.MATCH_PARENT));
        updateClockText();
        return bar;
    }

    private View buildHero(Category category, List<InstalledAppEntry> installedApps) {
        PosterScene scene = posterRepository.scene(
                category.key,
                category.title,
                category.subtitle
        );
        FrameLayout hero = new FrameLayout(this);
        hero.setPadding(dp(26), dp(12), dp(18), dp(12));
        hero.setBackground(heroGlassBackground(scene.accentColor));
        hero.setClipToOutline(true);

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setGravity(Gravity.CENTER_VERTICAL);

        heroEyebrowView = text(
                getString(R.string.app_detail_eyebrow),
                9,
                Color.rgb(197, 229, 255),
                true
        );
        heroEyebrowView.setLetterSpacing(0.14f);
        copy.addView(heroEyebrowView);

        TextView title = text(
                category.title,
                26,
                Color.WHITE,
                true
        );
        heroTitleView = title;
        title.setMaxLines(1);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        titleParams.topMargin = dp(2);
        copy.addView(title, titleParams);

        heroFunctionView = text(
                category.subtitle,
                12,
                Color.argb(215, 235, 241, 250),
                false
        );
        heroFunctionView.setMaxLines(2);
        LinearLayout.LayoutParams detailParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        detailParams.topMargin = dp(3);
        copy.addView(heroFunctionView, detailParams);

        heroFeatureView = text("", 11, Color.argb(205, 225, 237, 250), false);
        heroFeatureView.setSingleLine(true);
        heroFeatureView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams featureParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        featureParams.topMargin = dp(3);
        copy.addView(heroFeatureView, featureParams);

        heroAdvantageView = text("", 11, Color.argb(205, 225, 237, 250), false);
        heroAdvantageView.setSingleLine(true);
        heroAdvantageView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams advantageParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        advantageParams.topMargin = dp(2);
        copy.addView(heroAdvantageView, advantageParams);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        heroPrimaryButton = heroButton(
                getString(R.string.open),
                true
        );
        heroPrimaryButton.setId(View.generateViewId());
        heroPrimaryButton.setOnClickListener(view -> handleHeroAction(category));
        actions.addView(heroPrimaryButton, new LinearLayout.LayoutParams(dp(190), dp(38)));

        LinearLayout.LayoutParams actionsParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                dp(38)
        );
        actionsParams.topMargin = dp(8);
        copy.addView(actions, actionsParams);

        FrameLayout.LayoutParams copyParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        );
        copyParams.rightMargin = dp(18);
        hero.addView(copy, copyParams);
        updateHeroSelection();
        return hero;
    }

    private View buildApplicationShelf(
            Category category,
            List<InstalledAppEntry> installedApps
    ) {
        LinearLayout section = new LinearLayout(this);
        section.setOrientation(LinearLayout.VERTICAL);
        section.setClipChildren(false);
        section.setClipToPadding(false);

        LinearLayout heading = new LinearLayout(this);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = text(
                getString(category.kind == CategoryKind.INSTALLED
                        ? R.string.installed_shelf
                        : R.string.category_shelf),
                20,
                Color.WHITE,
                true
        );
        heading.addView(title, new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1
        ));
        section.addView(heading, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(26)
        ));

        HorizontalScrollView scroller = new HorizontalScrollView(this);
        scroller.setHorizontalScrollBarEnabled(false);
        scroller.setClipChildren(false);
        scroller.setClipToPadding(false);
        scroller.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroller.setPadding(dp(12), dp(10), dp(28), dp(12));
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setClipChildren(false);
        row.setClipToPadding(false);

        if (category.kind == CategoryKind.INSTALLED) {
            if (installedApps.isEmpty()) {
                LinearLayout empty = new LinearLayout(this);
                empty.setOrientation(LinearLayout.VERTICAL);
                empty.setGravity(Gravity.CENTER_VERTICAL);
                TextView message = text(getString(R.string.installed_empty), 17,
                        Color.WHITE, true);
                empty.addView(message);
                TextView detail = text(getString(R.string.installed_empty_desc), 12,
                        Color.argb(185, 228, 238, 250), false);
                empty.addView(detail);
                Button browse = heroButton(getString(R.string.hero_action), true);
                browse.setOnClickListener(view -> renderCategory("entertainment", true));
                LinearLayout.LayoutParams browseParams =
                        new LinearLayout.LayoutParams(dp(150), dp(44));
                browseParams.topMargin = dp(9);
                empty.addView(browse, browseParams);
                row.addView(empty, new LinearLayout.LayoutParams(dp(560), dp(150)));
                firstFocusable = browse;
            } else {
                for (InstalledAppEntry info : installedApps) {
                    addInstalledCard(row, info);
                }
            }
        } else {
            for (CatalogApp app : category.apps) {
                addCatalogCard(row, app);
            }
        }
        scroller.addView(row);
        section.addView(scroller);
        return section;
    }

    private void addInstalledCard(LinearLayout row, InstalledAppEntry info) {
        String packageName = info.packageName;
        String label = info.label;
        InstalledAppPresentation presentation = InstalledAppPresentation.resolve(
                this,
                packageName,
                label
        );
        TvCardView card = new TvCardView(
                this,
                info.icon,
                label,
                presentation.description,
                getString(R.string.open),
                colorForPackage(packageName, false),
                colorForPackage(packageName, true)
        );
        card.setBadgeCompanionText(presentation.languages);
        card.setBadgeFooterText(getString(
                R.string.app_regions_format,
                presentation.regions
        ));
        card.setSubtitleMarqueeEnabled(true);
        card.setOnCardFocusChangeListener((view, focused) -> {
            if (focused) {
                selectApp(packageName, label);
            }
        });
        card.setOnClickListener(view -> launchPackage(packageName));
        applicationCards.put(packageName, card);
        addCard(row, card, dp(238), dp(142));
    }

    private void addCatalogCard(LinearLayout row, CatalogApp app) {
        boolean installed = isInstalled(app.packageName);
        boolean launchable =
                InstalledAppResolver.launchIntent(this, app.packageName) != null;
        boolean unavailable = !installed && isUnavailableOnCurrentTv(app);
        String badge = getString(launchable
                ? R.string.open
                : installed
                        ? R.string.installed
                        : unavailable
                                ? R.string.unavailable_on_this_tv
                        : app.supportsOneClickInstall()
                                ? R.string.find_install_source
                                : R.string.not_adapted);
        TvCardView card = new TvCardView(
                this,
                iconFor(app),
                app.name,
                getString(app.descriptionRes),
                badge,
                app.startColor,
                app.endColor
        );
        card.setOnCardFocusChangeListener((view, focused) -> {
            if (focused) {
                selectApp(app.packageName, app.name);
            }
        });
        card.setOnClickListener(view -> handleCatalogAction(app));
        applicationCards.put(app.packageName, card);
        addCard(row, card, dp(205), dp(125));
    }

    private View buildSettingsShelf() {
        LinearLayout section = new LinearLayout(this);
        section.setOrientation(LinearLayout.VERTICAL);
        section.setClipChildren(false);
        section.setClipToPadding(false);
        TextView heading = text(getString(R.string.settings_shelf), 20, Color.WHITE, true);
        section.addView(heading, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(26)
        ));

        HorizontalScrollView scroller = new HorizontalScrollView(this);
        scroller.setHorizontalScrollBarEnabled(false);
        scroller.setClipChildren(false);
        scroller.setClipToPadding(false);
        scroller.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroller.setPadding(dp(12), dp(10), dp(28), dp(12));
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setClipChildren(false);
        row.setClipToPadding(false);

        TvCardView original = new TvCardView(
                this,
                "↩",
                getString(R.string.original_home),
                getString(R.string.original_home_desc),
                getString(R.string.open),
                Color.rgb(75, 88, 110),
                Color.rgb(20, 25, 36)
        );
        original.setOnClickListener(view -> HomeController.openOriginalHome(this));
        addCard(row, original, dp(212), dp(125));

        TvCardView defaultHome = new TvCardView(
                this,
                "⌂",
                getString(R.string.set_default_home),
                getString(R.string.set_default_home_desc),
                getString(R.string.settings),
                Color.rgb(62, 125, 226),
                Color.rgb(48, 35, 128)
        );
        defaultHome.setOnClickListener(view -> requestHomeRole());
        addCard(row, defaultHome, dp(212), dp(125));

        TvCardView environment = new TvCardView(
                this,
                "↻",
                getString(R.string.aptelly_update_section),
                getString(R.string.about_update_subtitle),
                getString(R.string.open),
                Color.rgb(35, 186, 142),
                Color.rgb(10, 71, 73)
        );
        environment.setOnClickListener(view ->
                startActivity(new Intent(this, EnvironmentActivity.class)));
        addCard(row, environment, dp(212), dp(125));

        TvCardView appManager = new TvCardView(
                this,
                "▦",
                getString(R.string.manage_apps),
                getString(R.string.manage_apps_desc),
                getString(R.string.open),
                Color.rgb(111, 81, 214),
                Color.rgb(37, 27, 89)
        );
        appManager.setOnClickListener(view -> openAppManager(null));
        addCard(row, appManager, dp(212), dp(125));

        scroller.addView(row);
        section.addView(scroller);
        return section;
    }

    private void addCard(
            LinearLayout row,
            TvCardView card,
            int width,
            int height
    ) {
        if (card.getId() == View.NO_ID) {
            card.setId(View.generateViewId());
        }
        card.setNextFocusDownId(card.getId());
        if (heroPrimaryButton != null && heroPrimaryButton.getId() != View.NO_ID) {
            card.setNextFocusUpId(heroPrimaryButton.getId());
        }
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(width, height);
        params.rightMargin = dp(15);
        row.addView(card, params);
        if (firstFocusable == null) {
            firstFocusable = card;
        }
    }

    private void configureContentFocusGraph(Category category) {
        TextView navigation = navigationViews.get(category.key);
        if (navigation == null || firstFocusable == null) {
            return;
        }
        if (firstFocusable.getId() == View.NO_ID) {
            firstFocusable.setId(View.generateViewId());
        }
        navigation.setNextFocusRightId(firstFocusable.getId());
        firstFocusable.setNextFocusLeftId(navigation.getId());
        firstFocusable.setNextFocusDownId(firstFocusable.getId());
        if (heroPrimaryButton != null) {
            if (heroPrimaryButton.getId() == View.NO_ID) {
                heroPrimaryButton.setId(View.generateViewId());
            }
            heroPrimaryButton.setNextFocusDownId(firstFocusable.getId());
            firstFocusable.setNextFocusUpId(heroPrimaryButton.getId());
        }
    }

    private void updateBackdrop(Category category) {
        if (category == null || backdropView == null || posterRepository == null) {
            return;
        }
        PosterScene scene = posterRepository.scene(
                category.key,
                category.title,
                category.subtitle
        );
        backdropView.setScene(scene);
        String expectedCategory = category.key;
        posterRepository.loadArtwork(scene, bitmap -> {
            if (expectedCategory.equals(selectedCategory)) {
                backdropView.setArtwork(bitmap);
            }
        });
    }

    private void selectApp(String packageName, String label) {
        String previousPackage = selectedPackage;
        selectedPackage = packageName;
        if (label != null || previousPackage == null || !previousPackage.equals(packageName)) {
            selectedLabel = label;
        }
        updateHeroSelection();
    }

    private CatalogApp selectedCatalogApp(Category category) {
        if (category == null || category.kind != CategoryKind.CATALOG) {
            return null;
        }
        for (CatalogApp app : category.apps) {
            if (app.packageName.equals(selectedPackage)) {
                return app;
            }
        }
        return category.apps.isEmpty() ? null : category.apps.get(0);
    }

    private void updateHeroSelection() {
        if (heroTitleView == null || heroPrimaryButton == null) {
            return;
        }
        Category current = category(selectedCategory);
        if (current.kind == CategoryKind.SETTINGS) {
            setHeroCopy(
                    getString(R.string.settings),
                    current.title,
                    current.subtitle,
                    getString(R.string.app_detail_feature, getString(R.string.settings_shelf)),
                    getString(R.string.app_detail_advantage, getString(R.string.about_update_subtitle)),
                    getString(R.string.aptelly_update_section),
                    true
            );
            return;
        }

        CatalogApp catalogApp = selectedCatalogApp(current);
        if (catalogApp != null) {
            boolean installed = isInstalled(catalogApp.packageName);
            boolean launchable = InstalledAppResolver.launchIntent(
                    this,
                    catalogApp.packageName
            ) != null;
            boolean unavailable = !installed && isUnavailableOnCurrentTv(catalogApp);
            String feature = getString(catalogApp.tvOptimized
                    ? R.string.tv_ready
                    : R.string.not_tv_optimized);
            String advantage = getString(
                    unavailable
                            ? R.string.unavailable_on_this_tv
                            : launchable || installed
                                    ? R.string.app_detail_ready_advantage
                                    : catalogApp.supportsOneClickInstall()
                                            ? R.string.app_detail_install_advantage
                                            : R.string.install_source_unverified
            );
            String action = getString(
                    launchable
                            ? R.string.open
                            : installed
                                    ? R.string.installed
                                    : unavailable
                                            ? R.string.unavailable_on_this_tv
                                            : catalogApp.supportsOneClickInstall()
                                                    ? R.string.install
                                                    : R.string.not_adapted
            );
            setHeroCopy(
                    getString(R.string.app_detail_eyebrow),
                    catalogApp.name,
                    getString(R.string.app_detail_function, getString(catalogApp.descriptionRes)),
                    getString(R.string.app_detail_feature, feature),
                    getString(R.string.app_detail_advantage, advantage),
                    action,
                    launchable || (!installed && !unavailable
                            && catalogApp.supportsOneClickInstall())
            );
            return;
        }

        if (selectedPackage != null) {
            String label = selectedLabel == null ? selectedPackage : selectedLabel;
            InstalledAppPresentation presentation = InstalledAppPresentation.resolve(
                    this,
                    selectedPackage,
                    label
            );
            setHeroCopy(
                    getString(R.string.app_detail_eyebrow),
                    label,
                    getString(R.string.app_detail_function, presentation.description),
                    getString(R.string.app_detail_feature, getString(R.string.installed_card_subtitle)),
                    getString(R.string.app_detail_advantage, getString(R.string.app_detail_ready_advantage)),
                    getString(R.string.open),
                    true
            );
            return;
        }

        setHeroCopy(
                getString(R.string.app_detail_eyebrow),
                current.title,
                current.subtitle,
                "",
                "",
                getString(R.string.open),
                false
        );
    }

    private void setHeroCopy(
            String eyebrow,
            String title,
            String function,
            String feature,
            String advantage,
            String action,
            boolean actionEnabled
    ) {
        heroEyebrowView.setText(eyebrow);
        heroTitleView.setText(title);
        heroFunctionView.setText(function);
        heroFeatureView.setText(feature);
        heroAdvantageView.setText(advantage);
        heroPrimaryButton.setText(action);
        heroPrimaryButton.setEnabled(actionEnabled);
    }

    private void handleHeroAction(Category category) {
        if (category.kind == CategoryKind.SETTINGS) {
            startActivity(new Intent(this, EnvironmentActivity.class));
            return;
        }
        CatalogApp catalogApp = selectedCatalogApp(category);
        if (catalogApp != null) {
            handleCatalogAction(catalogApp);
        } else if (selectedPackage != null) {
            launchPackage(selectedPackage);
        }
    }

    private void handleCatalogAction(CatalogApp app) {
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

        if (blockForMissingGoogleRuntime(app)) {
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

        TvMessageDialog.confirmInstall(this, app.name, () -> installCatalogApp(app));
    }

    private void installCatalogApp(CatalogApp app) {
        if (blockForMissingGoogleRuntime(app)) {
            return;
        }
        packageInstaller.installIfMissing(app, new SecurePackageInstaller.Listener() {
            @Override
            public void onStatus(String status) {
                Toast.makeText(MainActivity.this, status, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onError(String message) {
                TvMessageDialog.showInstallError(MainActivity.this, message);
            }
        });
    }

    private boolean blockForMissingGoogleRuntime(CatalogApp app) {
        deviceProfile = deviceProfile.refreshDynamic(this);
        if (!AppCompatibility.isGoogleNativeBlocked(app, deviceProfile)) {
            return false;
        }
        TvMessageDialog.showInstallError(
                this,
                getString(R.string.install_google_play_first, app.name)
        );
        return true;
    }

    private List<Category> categories() {
        List<Category> result = new ArrayList<>();
        result.add(new Category(
                "installed",
                getString(R.string.my_apps),
                getString(R.string.category_installed_desc),
                CategoryKind.INSTALLED,
                new ArrayList<>()
        ));
        result.add(new Category(
                "entertainment",
                getString(R.string.tv_entertainment),
                getString(R.string.category_entertainment_desc),
                CategoryKind.CATALOG,
                AppCatalog.tvEntertainment()
        ));
        result.add(new Category(
                "china",
                getString(R.string.china_mainland_apps),
                getString(R.string.category_china_desc),
                CategoryKind.CATALOG,
                AppCatalog.chinaMainlandServices()
        ));
        result.add(new Category(
                "media",
                getString(R.string.media_players),
                getString(R.string.category_media_players_desc),
                CategoryKind.CATALOG,
                CatalogAvailability.filter(AppCatalog.mediaPlayers())
        ));
        result.add(new Category(
                "games",
                getString(R.string.game_streaming_tools),
                getString(R.string.category_game_streaming_desc),
                CategoryKind.CATALOG,
                CatalogAvailability.filter(AppCatalog.gameStreaming())
        ));
        result.add(new Category(
                "web",
                getString(R.string.web_tools),
                getString(R.string.category_web_tools_desc),
                CategoryKind.CATALOG,
                CatalogAvailability.filter(AppCatalog.webTools())
        ));
        result.add(new Category(
                "stores",
                getString(R.string.app_stores),
                getString(R.string.category_stores_desc),
                CategoryKind.CATALOG,
                AppCatalog.stores()
        ));
        result.add(new Category(
                "network",
                getString(R.string.network_tools),
                getString(R.string.category_network_desc),
                CategoryKind.CATALOG,
                CatalogAvailability.filter(AppCatalog.privacy())
        ));
        result.add(new Category(
                "settings",
                getString(R.string.settings),
                getString(R.string.category_settings_desc),
                CategoryKind.SETTINGS,
                new ArrayList<>()
        ));
        return result;
    }

    private Category category(String key) {
        for (Category item : categories()) {
            if (item.key.equals(key)) {
                return item;
            }
        }
        return categories().get(0);
    }

    private String navigationSymbol(String key) {
        switch (key) {
            case "installed":
                return "●";
            case "entertainment":
                return "✦";
            case "media":
                return "▶";
            case "games":
                return "✣";
            case "web":
                return "◎";
            case "stores":
                return "↓";
            case "network":
                return "◇";
            case "china":
                return "中";
            default:
                return "⚙";
        }
    }

    private void styleNavigation(TextView item, String key, boolean focused) {
        boolean active = key.equals(selectedCategory);
        item.setBackground(navigationBackground(active, focused));
        item.setTextColor(focused
                ? Color.rgb(13, 20, 32)
                : Color.argb(active ? 255 : 205, 245, 248, 255));
        item.setTypeface(Typeface.DEFAULT, active ? Typeface.BOLD : Typeface.NORMAL);
    }

    private void launchPackage(String packageName) {
        Intent launch = InstalledAppResolver.launchIntent(this, packageName);
        if (launch == null) {
            Toast.makeText(this, R.string.install_verified_no_entry, Toast.LENGTH_LONG).show();
            return;
        }
        startActivity(launch);
    }

    private void openAppManager(String packageName) {
        restoreSidebarFocusOnResume = true;
        Intent intent = new Intent(this, AppManagerActivity.class);
        if (packageName != null) {
            intent.putExtra(AppManagerActivity.EXTRA_FOCUS_PACKAGE, packageName);
        }
        startActivity(intent);
    }

    private void requestSidebarFocus(String categoryKey) {
        TextView navigation = navigationViews.get(categoryKey);
        if (navigation == null) {
            navigation = navigationViews.get("installed");
        }
        if (navigation == null) {
            return;
        }
        TextView target = navigation;
        String targetKey = navigationViews.containsKey(categoryKey)
                ? categoryKey
                : "installed";
        target.post(() -> {
            if (!target.requestFocus()) {
                target.requestFocusFromTouch();
            }
            styleNavigation(target, targetKey, true);
        });
    }

    private void registerPackageChangeReceiver() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_PACKAGE_ADDED);
        filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        filter.addAction(Intent.ACTION_PACKAGE_REPLACED);
        filter.addDataScheme("package");
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(packageChangeReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(packageChangeReceiver, filter);
        }
        packageReceiverRegistered = true;
    }

    private void requestHomeRole() {
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            RoleManager roleManager = (RoleManager) getSystemService(Context.ROLE_SERVICE);
            if (roleManager != null
                    && roleManager.isRoleAvailable(RoleManager.ROLE_HOME)
                    && !roleManager.isRoleHeld(RoleManager.ROLE_HOME)) {
                startActivityForResult(
                        roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME),
                        110
                );
                return;
            }
        }
        HomeController.openHomeSettings(this);
    }

    private List<InstalledAppEntry> launchableApps() {
        LauncherApps launcherApps =
                (LauncherApps) getSystemService(Context.LAUNCHER_APPS_SERVICE);
        Set<String> tvPackages = tvLaunchablePackages();
        Set<String> seen = new HashSet<>();
        List<InstalledAppEntry> result = new ArrayList<>();
        if (launcherApps != null) {
            for (LauncherActivityInfo info : launcherApps.getActivityList(
                    null,
                    Process.myUserHandle()
            )) {
                ApplicationInfo application = info.getApplicationInfo();
                if (application == null
                        || getPackageName().equals(application.packageName)
                        || !tvPackages.contains(application.packageName)
                        || isRejectedCatalogVariant(application.packageName)
                        || !seen.add(application.packageName)) {
                    continue;
                }
                result.add(new InstalledAppEntry(
                        application.packageName,
                        info.getLabel().toString(),
                        info.getBadgedIcon(0)
                ));
            }
        }

        Intent leanback = new Intent(Intent.ACTION_MAIN);
        leanback.addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        PackageManager manager = getPackageManager();
        for (ResolveInfo info : manager.queryIntentActivities(leanback, 0)) {
            if (info.activityInfo == null
                    || getPackageName().equals(info.activityInfo.packageName)
                    || isRejectedCatalogVariant(info.activityInfo.packageName)
                    || !seen.add(info.activityInfo.packageName)) {
                continue;
            }
            CharSequence label = info.loadLabel(manager);
            Drawable icon = info.loadIcon(manager);
            if (icon == null && info.activityInfo.applicationInfo != null) {
                icon = info.activityInfo.applicationInfo.loadIcon(manager);
            }
            result.add(new InstalledAppEntry(
                    info.activityInfo.packageName,
                    label == null ? info.activityInfo.packageName : label.toString(),
                    icon
            ));
        }
        result.sort(Comparator.comparing(
                info -> info.label.toLowerCase(Locale.ROOT)
        ));
        return result;
    }

    private boolean isRejectedCatalogVariant(String actualPackage) {
        for (CatalogApp app : AppCatalog.all()) {
            if (!InstalledAppResolver.isKnownProductVariant(
                    app.packageName,
                    actualPackage
            )) {
                continue;
            }
            return !actualPackage.equals(
                    InstalledAppResolver.installedPackage(this, app.packageName)
            );
        }
        return false;
    }

    private Set<String> tvLaunchablePackages() {
        Intent leanback = new Intent(Intent.ACTION_MAIN);
        leanback.addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        Set<String> packages = new HashSet<>();
        for (ResolveInfo info : getPackageManager().queryIntentActivities(leanback, 0)) {
            if (info.activityInfo != null) {
                packages.add(info.activityInfo.packageName);
            }
        }
        return packages;
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

    private boolean isUnavailableOnCurrentTv(CatalogApp app) {
        return !StoreInstallRouter.canOpen(this, app)
                && !CatalogAvailability.isVisible(app.packageName);
    }

    private TextView metadataChip(String value) {
        TextView chip = text(value, 9, Color.argb(225, 239, 246, 255), true);
        chip.setGravity(Gravity.CENTER);
        chip.setPadding(dp(10), 0, dp(10), 0);
        chip.setBackground(rounded(
                Color.argb(32, 255, 255, 255),
                Color.argb(45, 255, 255, 255),
                14
        ));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                dp(24)
        );
        params.rightMargin = dp(7);
        chip.setLayoutParams(params);
        return chip;
    }

    private Button heroButton(String label, boolean primary) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(11);
        button.setTextColor(primary ? Color.rgb(13, 19, 31) : Color.WHITE);
        button.setAllCaps(false);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setFocusable(true);

        StateListDrawable states = new StateListDrawable();
        states.addState(
                new int[]{android.R.attr.state_focused},
                rounded(
                        primary ? Color.WHITE : Color.rgb(130, 211, 255),
                        Color.WHITE,
                        24
                )
        );
        states.addState(
                new int[]{},
                rounded(
                        primary ? Color.argb(238, 255, 255, 255)
                                : Color.argb(34, 255, 255, 255),
                        Color.argb(primary ? 175 : 70, 255, 255, 255),
                        24
                )
        );
        button.setBackground(states);
        button.setOnFocusChangeListener((view, focused) -> {
            if (!primary) {
                button.setTextColor(focused ? Color.rgb(9, 29, 45) : Color.WHITE);
            }
            view.animate()
                    .scaleX(focused ? 1.06f : 1f)
                    .scaleY(focused ? 1.06f : 1f)
                    .translationZ(focused ? dp(14) : 0)
                    .setDuration(145)
                    .start();
        });
        return button;
    }

    private GradientDrawable sidebarBackground() {
        GradientDrawable value = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{
                        Color.argb(152, 15, 16, 23),
                        Color.argb(208, 8, 10, 18)
                }
        );
        value.setCornerRadius(dp(26));
        value.setStroke(dp(1), Color.argb(38, 255, 255, 255));
        return value;
    }

    private GradientDrawable navigationBackground(boolean active, boolean focused) {
        if (focused) {
            return rounded(Color.argb(242, 255, 255, 255), Color.WHITE, 13);
        }
        if (active) {
            return rounded(
                    Color.argb(42, 255, 255, 255),
                    Color.argb(48, 255, 255, 255),
                    13
            );
        }
        return rounded(Color.TRANSPARENT, Color.TRANSPARENT, 13);
    }

    private GradientDrawable heroGlassBackground(int accent) {
        GradientDrawable value = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{
                        Color.argb(158, 7, 11, 20),
                        Color.argb(93, Color.red(accent), Color.green(accent), Color.blue(accent)),
                        Color.argb(30, 255, 255, 255)
                }
        );
        value.setCornerRadius(dp(26));
        value.setStroke(dp(1), Color.argb(60, 231, 242, 255));
        return value;
    }

    private GradientDrawable rounded(int fill, int stroke, int radius) {
        GradientDrawable value = new GradientDrawable();
        value.setColor(fill);
        value.setCornerRadius(dp(radius));
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

    private void updateClock() {
        updateClockText();
        clockHandler.postDelayed(this::updateClock, 30_000);
    }

    private void updateClockText() {
        if (clockView != null) {
            clockView.setText(DateFormat.getDateTimeInstance(
                    DateFormat.MEDIUM,
                    DateFormat.SHORT
            ).format(System.currentTimeMillis()));
        }
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

    private int colorForPackage(String packageName, boolean dark) {
        int hash = Math.abs(packageName.hashCode());
        int red = dark ? 20 + (hash % 70) : 50 + (hash % 140);
        int green = dark ? 28 + ((hash / 7) % 70) : 65 + ((hash / 7) % 130);
        int blue = dark ? 35 + ((hash / 13) % 75) : 80 + ((hash / 13) % 130);
        return Color.rgb(Math.min(red, 220), Math.min(green, 220), Math.min(blue, 220));
    }

    private String TextUtilsOr(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
