package app.aptelly.tv.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.view.View;

/**
 * Original orbital artwork for the launcher hero. It is deliberately abstract so the
 * product keeps its own identity while retaining a cinematic TV presentation.
 */
public final class AptellyArtworkView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF bounds = new RectF();

    public AptellyArtworkView(Context context) {
        super(context);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeCap(Paint.Cap.ROUND);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float width = getWidth();
        float height = getHeight();
        float centerX = width * 0.55f;
        float centerY = height * 0.50f;
        float radius = Math.min(width, height) * 0.33f;
        float phase = (SystemClock.uptimeMillis() % 18_000L) / 18_000f * 360f;

        paint.setShader(new RadialGradient(
                centerX,
                centerY,
                radius * 1.65f,
                new int[]{
                        Color.argb(130, 109, 100, 255),
                        Color.argb(65, 31, 205, 235),
                        Color.TRANSPARENT
                },
                new float[]{0f, 0.42f, 1f},
                Shader.TileMode.CLAMP
        ));
        canvas.drawCircle(centerX, centerY, radius * 1.65f, paint);
        paint.setShader(null);

        stroke.setStrokeWidth(dp(1f));
        stroke.setColor(Color.argb(55, 220, 238, 255));
        canvas.drawCircle(centerX, centerY, radius * 1.28f, stroke);
        canvas.drawCircle(centerX, centerY, radius * 0.92f, stroke);

        bounds.set(
                centerX - radius * 1.28f,
                centerY - radius * 1.28f,
                centerX + radius * 1.28f,
                centerY + radius * 1.28f
        );
        stroke.setStrokeWidth(dp(2.2f));
        stroke.setColor(Color.argb(205, 112, 208, 255));
        canvas.drawArc(bounds, phase, 76f, false, stroke);
        stroke.setColor(Color.argb(190, 135, 103, 255));
        canvas.drawArc(bounds, phase + 166f, 54f, false, stroke);

        bounds.inset(radius * 0.35f, radius * 0.35f);
        stroke.setStrokeWidth(dp(1.6f));
        stroke.setColor(Color.argb(170, 99, 240, 220));
        canvas.drawArc(bounds, -phase * 0.72f, 102f, false, stroke);

        float coreSize = radius * 1.15f;
        bounds.set(
                centerX - coreSize / 2f,
                centerY - coreSize / 2f,
                centerX + coreSize / 2f,
                centerY + coreSize / 2f
        );
        paint.setShader(new LinearGradient(
                bounds.left,
                bounds.top,
                bounds.right,
                bounds.bottom,
                new int[]{
                        Color.rgb(125, 221, 255),
                        Color.rgb(99, 113, 255),
                        Color.rgb(151, 81, 255)
                },
                null,
                Shader.TileMode.CLAMP
        ));
        canvas.drawRoundRect(bounds, coreSize * 0.28f, coreSize * 0.28f, paint);
        paint.setShader(null);

        stroke.setStrokeWidth(dp(1f));
        stroke.setColor(Color.argb(155, 255, 255, 255));
        canvas.drawRoundRect(bounds, coreSize * 0.28f, coreSize * 0.28f, stroke);

        paint.setColor(Color.WHITE);
        paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(radius * 0.40f);
        canvas.drawText("TV", centerX, centerY + radius * 0.14f, paint);

        drawNode(canvas, centerX + radius * 1.26f, centerY - radius * 0.18f, 4.5f);
        drawNode(canvas, centerX - radius * 0.78f, centerY + radius * 0.83f, 3.5f);
        drawNode(canvas, centerX - radius * 1.02f, centerY - radius * 0.66f, 2.8f);

        paint.setTextAlign(Paint.Align.LEFT);
        paint.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
        paint.setTextSize(dp(8.5f));
        paint.setLetterSpacing(0.16f);
        paint.setColor(Color.argb(155, 221, 237, 255));
        canvas.drawText("INSTALL  ·  MANAGE  ·  PLAY", dp(6), height - dp(12), paint);

        postInvalidateDelayed(40L);
    }

    private void drawNode(Canvas canvas, float x, float y, float radiusDp) {
        paint.setColor(Color.argb(220, 142, 238, 255));
        canvas.drawCircle(x, y, dp(radiusDp), paint);
        stroke.setColor(Color.argb(95, 194, 230, 255));
        stroke.setStrokeWidth(dp(1f));
        canvas.drawCircle(x, y, dp(radiusDp + 5f), stroke);
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
