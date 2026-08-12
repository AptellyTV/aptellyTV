package app.aptelly.tv.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.view.View;

/**
 * A lightweight, code-generated cinematic backdrop. It avoids bundled artwork and
 * keeps the launcher visually consistent across regions and screen resolutions.
 */
public final class AmbientBackgroundView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int viewWidth;
    private int viewHeight;

    public AmbientBackgroundView(Context context) {
        super(context);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        gridPaint.setStrokeWidth(dp(0.5f));
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        viewWidth = width;
        viewHeight = height;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (viewWidth <= 0 || viewHeight <= 0) {
            return;
        }

        paint.setShader(new LinearGradient(
                0,
                0,
                0,
                viewHeight,
                new int[]{
                        Color.rgb(3, 7, 14),
                        Color.rgb(5, 17, 31),
                        Color.rgb(8, 13, 27)
                },
                new float[]{0f, 0.52f, 1f},
                Shader.TileMode.CLAMP
        ));
        canvas.drawRect(0, 0, viewWidth, viewHeight, paint);

        drawGlow(
                canvas,
                viewWidth * 0.82f,
                viewHeight * 0.08f,
                Math.max(viewWidth, viewHeight) * 0.58f,
                Color.argb(95, 64, 82, 255)
        );
        drawGlow(
                canvas,
                viewWidth * 0.18f,
                viewHeight * 0.48f,
                Math.max(viewWidth, viewHeight) * 0.52f,
                Color.argb(64, 0, 198, 209)
        );
        drawGlow(
                canvas,
                viewWidth * 0.62f,
                viewHeight * 0.84f,
                Math.max(viewWidth, viewHeight) * 0.48f,
                Color.argb(54, 145, 56, 255)
        );

        drawGrid(canvas);
        drawParticles(canvas);
    }

    private void drawGlow(Canvas canvas, float x, float y, float radius, int color) {
        paint.setShader(new RadialGradient(
                x,
                y,
                radius,
                color,
                Color.TRANSPARENT,
                Shader.TileMode.CLAMP
        ));
        canvas.drawCircle(x, y, radius, paint);
        paint.setShader(null);
    }

    private void drawGrid(Canvas canvas) {
        int spacing = Math.max(dp(42), viewWidth / 24);
        gridPaint.setColor(Color.argb(11, 188, 224, 255));
        for (int x = -spacing; x < viewWidth + spacing; x += spacing) {
            canvas.drawLine(x, 0, x + viewHeight / 5f, viewHeight, gridPaint);
        }
        for (int y = spacing; y < viewHeight; y += spacing) {
            canvas.drawLine(0, y, viewWidth, y, gridPaint);
        }
    }

    private void drawParticles(Canvas canvas) {
        paint.setShader(null);
        for (int index = 0; index < 42; index++) {
            int seed = index * 1103515245 + 12345;
            float x = Math.abs(seed % 10_000) / 10_000f * viewWidth;
            float y = Math.abs((seed / 31) % 10_000) / 10_000f * viewHeight;
            float radius = dp(index % 7 == 0 ? 1.2f : 0.65f);
            paint.setColor(Color.argb(index % 7 == 0 ? 80 : 38, 205, 231, 255));
            canvas.drawCircle(x, y, radius, paint);
        }
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
