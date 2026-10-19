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
    private Bitmap wall, oldWall;
    private final Matrix wallMatrix = new Matrix(), oldMatrix = new Matrix();
    private final Paint wallPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Paint oldPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    /** Extra darkening for bright pictures, so white text stays readable; animated with the crossfade. */
    private float shade, oldShade;
    private final Paint glowPaint = new Paint();
    private final Paint topScrim = new Paint();
    private final Paint bottomScrim = new Paint();
    private final Paint dimPaint = new Paint();
    private int glow = Color.TRANSPARENT;
    private float dim;
    private ValueAnimator glowAnim, dimAnim, wallAnim;

    Backdrop(Context c) {
        super(c);
        setWillNotDraw(false);
        dimPaint.setColor(Color.BLACK);
    }

    /** Crossfades to {@code b}; {@code shadeFor} decides how much it is darkened. */
    void setWallpaper(Bitmap b, int fadeMs) {
        if (b == wall) return;
        if (wallAnim != null) wallAnim.cancel();
        oldWall = wall;
        oldMatrix.set(wallMatrix);
        oldShade = shade;
        wall = b;
        float target = shadeFor(b);
        fitWall();
        wallAnim = ValueAnimator.ofFloat(0f, 1f).setDuration(fadeMs);
        wallAnim.setInterpolator(new DecelerateInterpolator());
        wallAnim.addUpdateListener(a -> {
            float t = (float) a.getAnimatedValue();
            wallPaint.setAlpha((int) (t * 255));
            shade = oldShade + (target - oldShade) * t;
            if (t >= 1f) oldWall = null;
            invalidate();
        });
        wallAnim.start();
    }

    /** Average brightness, sampled small: bright pictures get up to 45% black over them. */
    private static float shadeFor(Bitmap b) {
        Bitmap s = Bitmap.createScaledBitmap(b, 32, 18, true);
        float sum = 0;
        for (int y = 0; y < 18; y++) {
            for (int x = 0; x < 32; x++) {
                int c = s.getPixel(x, y);
                sum += (0.299f * Color.red(c) + 0.587f * Color.green(c) + 0.114f * Color.blue(c)) / 255f;
            }
        }
        if (s != b) s.recycle();
        float lum = sum / (32 * 18);
        return Math.max(0f, Math.min(0.45f, (lum - 0.3f) * 0.9f));
    }

    /** Fades the ambient glow toward {@code color} (opaque RGB; alpha is applied here). */
    void setGlow(int color) {
        int target = Color.argb(100, Color.red(color), Color.green(color), Color.blue(color));
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
        topScrim.setShader(new LinearGradient(0, 0, 0, h * 0.18f, 0x59000000, 0x00000000, Shader.TileMode.CLAMP));
        bottomScrim.setShader(new LinearGradient(0, h * 0.55f, 0, h, 0x00000000, 0x8C000000, Shader.TileMode.CLAMP));
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
        if (oldWall != null) c.drawBitmap(oldWall, oldMatrix, oldPaint);
        if (wall != null) c.drawBitmap(wall, wallMatrix, wallPaint);
        if (shade > 0.001f) {
            dimPaint.setAlpha((int) (shade * 255));
            c.drawRect(0, 0, w, h, dimPaint);
        }
        c.drawRect(0, 0, w, h, topScrim);
        c.drawRect(0, 0, w, h, bottomScrim);
        if (glowPaint.getShader() != null) c.drawRect(0, 0, w, h, glowPaint);
        if (dim > 0.001f) {
            dimPaint.setAlpha((int) (dim * 255));
            c.drawRect(0, 0, w, h, dimPaint);
        }
    }
}
