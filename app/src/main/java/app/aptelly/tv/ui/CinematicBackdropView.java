package app.aptelly.tv.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;

import app.aptelly.tv.content.PosterScene;

public final class CinematicBackdropView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Rect source = new Rect();
    private final RectF target = new RectF();
    private Bitmap artwork;
    private int accentColor = Color.rgb(95, 92, 220);
    private int deepColor = Color.rgb(7, 11, 25);

    public CinematicBackdropView(Context context) {
        super(context);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    public void setScene(PosterScene scene) {
        if (scene != null) {
            accentColor = scene.accentColor;
            deepColor = scene.deepColor;
        }
        artwork = null;
        invalidate();
    }

    public void setArtwork(Bitmap bitmap) {
        artwork = bitmap;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int width = getWidth();
        int height = getHeight();
        if (width <= 0 || height <= 0) {
            return;
        }

        paint.setShader(new LinearGradient(
                0,
                0,
                width,
                height,
                new int[]{deepColor, mix(deepColor, accentColor, 0.28f), Color.rgb(4, 6, 12)},
                new float[]{0f, 0.64f, 1f},
                Shader.TileMode.CLAMP
        ));
        canvas.drawRect(0, 0, width, height, paint);
        paint.setShader(null);

        if (artwork != null && !artwork.isRecycled()) {
            source.set(0, 0, artwork.getWidth(), artwork.getHeight());
            float scale = Math.max(
                    width / (float) artwork.getWidth(),
                    height / (float) artwork.getHeight()
            );
            float drawnWidth = artwork.getWidth() * scale;
            float drawnHeight = artwork.getHeight() * scale;
            float left = width - drawnWidth;
            float top = (height - drawnHeight) / 2f;
            target.set(left, top, left + drawnWidth, top + drawnHeight);
            paint.setAlpha(224);
            canvas.drawBitmap(artwork, source, target, paint);
            paint.setAlpha(255);
        } else {
            drawGeneratedArtwork(canvas, width, height);
        }

        paint.setShader(new LinearGradient(
                0,
                0,
                width,
                0,
                new int[]{
                        Color.argb(246, 3, 6, 13),
                        Color.argb(225, 3, 7, 15),
                        Color.argb(92, 3, 7, 15),
                        Color.argb(28, 3, 7, 15)
                },
                new float[]{0f, 0.26f, 0.63f, 1f},
                Shader.TileMode.CLAMP
        ));
        canvas.drawRect(0, 0, width, height, paint);

        paint.setShader(new LinearGradient(
                0,
                0,
                0,
                height,
                new int[]{
                        Color.argb(44, 0, 0, 0),
                        Color.TRANSPARENT,
                        Color.argb(220, 2, 4, 10)
                },
                new float[]{0f, 0.56f, 1f},
                Shader.TileMode.CLAMP
        ));
        canvas.drawRect(0, 0, width, height, paint);
        paint.setShader(null);
    }

    private void drawGeneratedArtwork(Canvas canvas, int width, int height) {
        paint.setShader(new RadialGradient(
                width * 0.78f,
                height * 0.30f,
                Math.max(width, height) * 0.52f,
                withAlpha(accentColor, 205),
                Color.TRANSPARENT,
                Shader.TileMode.CLAMP
        ));
        canvas.drawCircle(
                width * 0.78f,
                height * 0.30f,
                Math.max(width, height) * 0.52f,
                paint
        );
        paint.setShader(null);

        for (int index = 0; index < 7; index++) {
            float cardWidth = width * 0.13f;
            float cardHeight = height * 0.54f;
            float left = width * 0.48f + index * cardWidth * 0.56f;
            float top = height * 0.14f + (index % 3) * height * 0.035f;
            paint.setColor(Color.argb(
                    65 - index * 5,
                    Math.min(255, Color.red(accentColor) + index * 7),
                    Math.min(255, Color.green(accentColor) + index * 5),
                    Math.min(255, Color.blue(accentColor) + index * 3)
            ));
            canvas.save();
            canvas.rotate(-7f + index * 2f, left + cardWidth / 2f, top + cardHeight / 2f);
            canvas.drawRoundRect(
                    left,
                    top,
                    left + cardWidth,
                    top + cardHeight,
                    dp(18),
                    dp(18),
                    paint
            );
            canvas.restore();
        }
    }

    private int mix(int first, int second, float amount) {
        return Color.rgb(
                Math.round(Color.red(first) * (1f - amount) + Color.red(second) * amount),
                Math.round(Color.green(first) * (1f - amount) + Color.green(second) * amount),
                Math.round(Color.blue(first) * (1f - amount) + Color.blue(second) * amount)
        );
    }

    private int withAlpha(int color, int alpha) {
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
