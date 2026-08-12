package app.aptelly.tv.ui;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewParent;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

public final class TvCardView extends LinearLayout {
    private final TextView title;
    private final TextView subtitle;
    private final TextView badge;
    private final TextView badgeCompanion;
    private final TextView badgeFooter;
    private final Drawable normalBackground;
    private final Drawable focusedBackground;
    private OnFocusChangeListener cardFocusChangeListener;
    private boolean subtitleMarqueeEnabled;

    public TvCardView(
            Context context,
            String symbol,
            String titleText,
            String subtitleText,
            String badgeText,
            int startColor,
            int endColor
    ) {
        this(context, symbol, null, titleText, subtitleText, badgeText, startColor, endColor);
    }

    public TvCardView(
            Context context,
            Drawable iconDrawable,
            String titleText,
            String subtitleText,
            String badgeText,
            int startColor,
            int endColor
    ) {
        this(context, null, iconDrawable, titleText, subtitleText, badgeText, startColor, endColor);
    }

    private TvCardView(
            Context context,
            String symbol,
            Drawable iconDrawable,
            String titleText,
            String subtitleText,
            String badgeText,
            int startColor,
            int endColor
    ) {
        super(context);
        setOrientation(VERTICAL);
        setGravity(Gravity.BOTTOM);
        setPadding(dp(16), dp(14), dp(16), dp(14));
        setFocusable(true);
        setClickable(true);
        setClipToOutline(true);
        setElevation(dp(4));

        GradientDrawable normal = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{tone(startColor, 0.48f), tone(endColor, 0.34f)}
        );
        normal.setCornerRadius(dp(18));
        normal.setStroke(dp(1), Color.argb(42, 225, 239, 255));
        normalBackground = normal;

        GradientDrawable focusedStyle = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{tone(startColor, 0.74f), tone(endColor, 0.58f)}
        );
        focusedStyle.setCornerRadius(dp(18));
        focusedStyle.setStroke(dp(2), Color.argb(235, 255, 255, 255));
        focusedBackground = focusedStyle;
        setBackground(normalBackground);

        LinearLayout top = new LinearLayout(context);
        top.setOrientation(HORIZONTAL);
        top.setGravity(Gravity.TOP | Gravity.CENTER_VERTICAL);

        FrameLayout iconShell = new FrameLayout(context);
        GradientDrawable shellBackground = new GradientDrawable();
        shellBackground.setColor(Color.argb(232, 248, 251, 255));
        shellBackground.setCornerRadius(dp(13));
        shellBackground.setStroke(dp(1), Color.argb(120, 255, 255, 255));
        iconShell.setBackground(shellBackground);
        iconShell.setPadding(dp(6), dp(6), dp(6), dp(6));
        if (iconDrawable != null) {
            ImageView icon = new ImageView(context);
            icon.setImageDrawable(iconDrawable);
            icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            iconShell.addView(icon, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
            ));
        } else {
            TextView icon = new TextView(context);
            icon.setText(symbol);
            icon.setTextColor(Color.rgb(30, 37, 51));
            icon.setTextSize(23);
            icon.setGravity(Gravity.CENTER);
            icon.setTypeface(icon.getTypeface(), android.graphics.Typeface.BOLD);
            iconShell.addView(icon, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
            ));
        }
        top.addView(iconShell, new LayoutParams(dp(50), dp(50)));

        View topSpacer = new View(context);
        top.addView(topSpacer, new LayoutParams(0, dp(1), 1));

        LinearLayout badgeStack = new LinearLayout(context);
        badgeStack.setOrientation(VERTICAL);
        badgeStack.setGravity(Gravity.END);

        LinearLayout badgeRow = new LinearLayout(context);
        badgeRow.setOrientation(HORIZONTAL);
        badgeRow.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);

        badge = new TextView(context);
        badge.setText(badgeText);
        badge.setTextColor(Color.WHITE);
        badge.setTextSize(9);
        badge.setAllCaps(false);
        badge.setGravity(Gravity.CENTER);
        badge.setPadding(dp(9), dp(3), dp(9), dp(3));
        GradientDrawable badgeBackground = new GradientDrawable();
        badgeBackground.setColor(Color.argb(42, 255, 255, 255));
        badgeBackground.setCornerRadius(dp(14));
        badgeBackground.setStroke(dp(1), Color.argb(45, 255, 255, 255));
        badge.setBackground(badgeBackground);
        badgeRow.addView(badge, new LayoutParams(LayoutParams.WRAP_CONTENT, dp(23)));

        badgeCompanion = new TextView(context);
        badgeCompanion.setTextColor(Color.argb(225, 239, 246, 255));
        badgeCompanion.setTextSize(8);
        badgeCompanion.setMaxLines(1);
        badgeCompanion.setGravity(Gravity.CENTER);
        badgeCompanion.setPadding(dp(7), dp(3), dp(7), dp(3));
        GradientDrawable companionBackground = new GradientDrawable();
        companionBackground.setColor(Color.argb(28, 255, 255, 255));
        companionBackground.setCornerRadius(dp(14));
        companionBackground.setStroke(dp(1), Color.argb(38, 255, 255, 255));
        badgeCompanion.setBackground(companionBackground);
        badgeCompanion.setVisibility(GONE);
        LayoutParams companionParams = new LayoutParams(
                LayoutParams.WRAP_CONTENT,
                dp(23)
        );
        companionParams.leftMargin = dp(5);
        badgeRow.addView(badgeCompanion, companionParams);
        badgeStack.addView(badgeRow, new LayoutParams(
                LayoutParams.WRAP_CONTENT,
                dp(23)
        ));

        badgeFooter = new TextView(context);
        badgeFooter.setTextColor(Color.argb(195, 226, 238, 250));
        badgeFooter.setTextSize(8);
        badgeFooter.setMaxLines(1);
        badgeFooter.setEllipsize(android.text.TextUtils.TruncateAt.END);
        badgeFooter.setGravity(Gravity.END);
        badgeFooter.setVisibility(GONE);
        LayoutParams footerParams = new LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT
        );
        footerParams.topMargin = dp(4);
        badgeStack.addView(badgeFooter, footerParams);

        top.addView(badgeStack, new LayoutParams(
                LayoutParams.WRAP_CONTENT,
                dp(50)
        ));
        addView(top, new LayoutParams(LayoutParams.MATCH_PARENT, dp(50)));

        View spacer = new View(context);
        addView(spacer, new LayoutParams(LayoutParams.MATCH_PARENT, 0, 1));

        title = new TextView(context);
        title.setText(titleText);
        title.setTextColor(Color.WHITE);
        title.setTextSize(17);
        title.setMaxLines(1);
        title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
        addView(title, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        subtitle = new TextView(context);
        subtitle.setText(subtitleText);
        subtitle.setTextColor(Color.argb(188, 229, 238, 248));
        subtitle.setTextSize(10);
        subtitle.setMaxLines(1);
        subtitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LayoutParams subtitleParams =
                new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        subtitleParams.topMargin = dp(3);
        addView(subtitle, subtitleParams);

        setOnFocusChangeListener((view, focused) -> {
            animateFocus(view, focused);
            subtitle.setSelected(subtitleMarqueeEnabled && focused);
            if (focused) {
                revealFocusMargin();
            }
            if (cardFocusChangeListener != null) {
                cardFocusChangeListener.onFocusChange(view, focused);
            }
        });
    }

    private void revealFocusMargin() {
        post(() -> {
            ViewParent ancestor = getParent();
            while (ancestor != null && !(ancestor instanceof HorizontalScrollView)) {
                ancestor = ancestor.getParent();
            }
            if (!(ancestor instanceof HorizontalScrollView)) {
                return;
            }
            int margin = dp(12);
            Rect focusBounds = new Rect(
                    -margin,
                    -margin,
                    getWidth() + margin,
                    getHeight() + margin
            );
            ((HorizontalScrollView) ancestor).requestChildRectangleOnScreen(
                    this,
                    focusBounds,
                    false
            );
        });
    }

    public void setBadgeText(String value) {
        badge.setText(value);
    }

    public void setBadgeCompanionText(String value) {
        badgeCompanion.setText(value);
        badgeCompanion.setVisibility(
                value == null || value.trim().isEmpty() ? GONE : VISIBLE
        );
    }

    public void setBadgeFooterText(String value) {
        badgeFooter.setText(value);
        badgeFooter.setVisibility(
                value == null || value.trim().isEmpty() ? GONE : VISIBLE
        );
    }

    public void setSubtitleMarqueeEnabled(boolean enabled) {
        subtitleMarqueeEnabled = enabled;
        subtitle.setEllipsize(enabled
                ? android.text.TextUtils.TruncateAt.MARQUEE
                : android.text.TextUtils.TruncateAt.END);
        subtitle.setMarqueeRepeatLimit(enabled ? -1 : 0);
        subtitle.setHorizontallyScrolling(enabled);
        subtitle.setSelected(enabled && hasFocus());
    }

    public void setOnCardFocusChangeListener(OnFocusChangeListener listener) {
        cardFocusChangeListener = listener;
    }

    private void animateFocus(View view, boolean focused) {
        setBackground(focused ? focusedBackground : normalBackground);
        float scale = focused ? 1.06f : 1f;
        AnimatorSet set = new AnimatorSet();
        set.playTogether(
                ObjectAnimator.ofFloat(view, View.SCALE_X, view.getScaleX(), scale),
                ObjectAnimator.ofFloat(view, View.SCALE_Y, view.getScaleY(), scale),
                ObjectAnimator.ofFloat(
                        view,
                        View.TRANSLATION_Z,
                        view.getTranslationZ(),
                        focused ? dp(18) : 0
                )
        );
        set.setDuration(150);
        set.start();
    }

    private int tone(int color, float intensity) {
        int red = Math.round(Color.red(color) * intensity + 6 * (1f - intensity));
        int green = Math.round(Color.green(color) * intensity + 10 * (1f - intensity));
        int blue = Math.round(Color.blue(color) * intensity + 20 * (1f - intensity));
        return Color.rgb(
                Math.min(255, red),
                Math.min(255, green),
                Math.min(255, blue)
        );
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
