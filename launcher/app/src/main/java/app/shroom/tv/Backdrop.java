package app.shroom.tv;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

/**
 * The full-screen sky behind everything: wallpaper, an ambient glow tinted by the focused app, the
 * scrims that keep text readable, and a dim layer for the Apps and Settings pages. One view, no blur.
 */
final class Backdrop extends View {
    private Bitmap wall;
    private final Matrix wallMatrix = new Matrix();
    private final Paint wallPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Paint glowPaint = new Paint();
    private final Paint topScrim = new Paint();
    private final Paint bottomScrim = new Paint();
    private final Paint leftScrim = new Paint();
    private final Paint dimPaint = new Paint();
    private int glow = Color.TRANSPARENT;
    private float dim;
    private ValueAnimator glowAnim, dimAnim, wallAnim;

    Backdrop(Context c) {
        super(c);
        setWillNotDraw(false);
        dimPaint.setColor(Color.BLACK);
    }

    void setWallpaper(Bitmap b) {
        if (b == wall) return;
        wall = b;
        fitWall();
        if (wallAnim != null) wallAnim.cancel();
        wallAnim = ValueAnimator.ofInt(0, 255).setDuration(700);
        wallAnim.addUpdateListener(a -> {
            wallPaint.setAlpha((int) a.getAnimatedValue());
            invalidate();
        });
        wallAnim.start();
    }

    /** Fades the ambient glow toward {@code color} (opaque RGB; alpha is applied here). */
    void setGlow(int color) {
        int target = Color.argb(150, Color.red(color), Color.green(color), Color.blue(color));
        if (target == glow) return;
        if (glowAnim != null) glowAnim.cancel();
        glowAnim = ValueAnimator.ofObject(new ArgbEvaluator(), glow, target).setDuration(450);
        glowAnim.setInterpolator(new DecelerateInterpolator());
        glowAnim.addUpdateListener(a -> {
            glow = (int) a.getAnimatedValue();
            updateGlowShader();
            invalidate();
        });
        glowAnim.start();
    }

    void setDim(float target) {
        if (dimAnim != null) dimAnim.cancel();
        dimAnim = ValueAnimator.ofFloat(dim, target).setDuration(320);
        dimAnim.setInterpolator(new DecelerateInterpolator());
        dimAnim.addUpdateListener(a -> {
            dim = (float) a.getAnimatedValue();
            invalidate();
        });
        dimAnim.start();
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        fitWall();
        topScrim.setShader(new LinearGradient(0, 0, 0, h * 0.26f, 0x8C000000, 0x00000000, Shader.TileMode.CLAMP));
        bottomScrim.setShader(new LinearGradient(0, h * 0.5f, 0, h, 0x00000000, 0xE6000000, Shader.TileMode.CLAMP));
        leftScrim.setShader(new LinearGradient(0, 0, w * 0.62f, 0, 0x73000000, 0x00000000, Shader.TileMode.CLAMP));
        updateGlowShader();
    }

    private void fitWall() {
        if (wall == null || getWidth() == 0) return;
        float s = Math.max(getWidth() / (float) wall.getWidth(), getHeight() / (float) wall.getHeight());
        wallMatrix.setScale(s, s);
        wallMatrix.postTranslate((getWidth() - wall.getWidth() * s) / 2f, (getHeight() - wall.getHeight() * s) / 2f);
    }

    private void updateGlowShader() {
        int w = getWidth(), h = getHeight();
        if (w == 0 || Color.alpha(glow) == 0) {
            glowPaint.setShader(null);
            return;
        }
        int clear = glow & 0x00FFFFFF;
        glowPaint.setShader(new RadialGradient(w * 0.3f, h * 1.02f, w * 0.75f,
                new int[]{glow, Color.argb(Color.alpha(glow) / 3, Color.red(glow), Color.green(glow), Color.blue(glow)), clear},
                new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP));
    }

    @Override
    protected void onDraw(Canvas c) {
        int w = getWidth(), h = getHeight();
        c.drawColor(Color.BLACK);
        if (wall != null) c.drawBitmap(wall, wallMatrix, wallPaint);
        c.drawRect(0, 0, w, h, leftScrim);
        c.drawRect(0, 0, w, h, topScrim);
        c.drawRect(0, 0, w, h, bottomScrim);
        if (glowPaint.getShader() != null) c.drawRect(0, 0, w, h, glowPaint);
        if (dim > 0.001f) {
            dimPaint.setAlpha((int) (dim * 255));
            c.drawRect(0, 0, w, h, dimPaint);
        }
    }
}
