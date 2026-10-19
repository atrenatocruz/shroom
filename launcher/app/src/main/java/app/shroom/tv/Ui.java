package app.shroom.tv;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.util.DisplayMetrics;
import android.util.SparseArray;
import android.util.TypedValue;
import android.widget.TextView;

/**
 * Layout is drawn on a 960 x 540 grid scaled to the real screen, so the home looks the same on a
 * 720p box, a 1080p TV at any density, and a 4K panel.
 */
final class Ui {
    final Context ctx;
    final float unit;
    private final SparseArray<Typeface> fonts = new SparseArray<>();

    Ui(Context ctx) {
        this.ctx = ctx;
        DisplayMetrics dm = ctx.getResources().getDisplayMetrics();
        unit = Math.max(dm.widthPixels, dm.heightPixels) / 960f;
    }

    int u(float v) {
        return Math.round(v * unit);
    }

    /** Outfit is a variable font: one file, any weight from 100 to 900. */
    Typeface font(int weight) {
        Typeface t = fonts.get(weight);
        if (t != null) return t;
        try {
            t = new Typeface.Builder(ctx.getAssets(), "outfit.ttf")
                    .setFontVariationSettings("'wght' " + weight).build();
        } catch (Exception e) {
            t = null;
        }
        if (t == null) t = Typeface.create("sans-serif", weight >= 600 ? Typeface.BOLD : Typeface.NORMAL);
        fonts.put(weight, t);
        return t;
    }

    TextView text(float size, int weight, int color) {
        TextView v = new TextView(ctx);
        v.setTextSize(TypedValue.COMPLEX_UNIT_PX, size * unit);
        v.setTypeface(font(weight));
        v.setTextColor(color);
        v.setIncludeFontPadding(false);
        v.setSingleLine(true);
        return v;
    }

    GradientDrawable round(int color, float radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(radius * unit);
        return g;
    }

    GradientDrawable ring(int color, float stroke, float radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(Color.TRANSPARENT);
        g.setStroke(Math.max(2, u(stroke)), color);
        g.setCornerRadius(radius * unit);
        return g;
    }

    /**
     * The colour an app "is": the dominant saturated hue of its banner or icon, used for the ambient
     * glow behind the dock and for tiles that have no banner. Falls back to a cool slate.
     */
    static int signatureColor(Drawable d) {
        final int fallback = 0xFF3A4A6B;
        if (d == null) return fallback;
        final int n = 16;
        Bitmap b = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888);
        try {
            Drawable copy = d.getConstantState() != null ? d.getConstantState().newDrawable().mutate() : d;
            copy.setBounds(0, 0, n, n);
            copy.draw(new Canvas(b));
        } catch (Exception e) {
            return fallback;
        }
        float[] hsv = new float[3];
        float[] weight = new float[12];
        float[][] sum = new float[12][3];
        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) {
                int c = b.getPixel(x, y);
                if (Color.alpha(c) < 128) continue;
                Color.colorToHSV(c, hsv);
                if (hsv[1] < 0.35f || hsv[2] < 0.25f) continue;
                int bin = Math.min(11, (int) (hsv[0] / 30f));
                float w = hsv[1] * hsv[2];
                weight[bin] += w;
                sum[bin][0] += Color.red(c) * w;
                sum[bin][1] += Color.green(c) * w;
                sum[bin][2] += Color.blue(c) * w;
            }
        }
        b.recycle();
        int best = -1;
        for (int i = 0; i < 12; i++) if (weight[i] > 0.6f && (best < 0 || weight[i] > weight[best])) best = i;
        if (best < 0) return fallback;
        float w = weight[best];
        return Color.rgb((int) (sum[best][0] / w), (int) (sum[best][1] / w), (int) (sum[best][2] / w));
    }

    /** Same hue, pushed darker: tile backgrounds behind a white icon. */
    static int deepen(int color) {
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        hsv[2] = Math.min(hsv[2], 0.55f);
        return Color.HSVToColor(hsv);
    }
}
