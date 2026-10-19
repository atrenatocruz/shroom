package app.shroom.tv;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * A tvOS-style app card. At rest: a flat rounded card, no name. Focused: it lifts toward you with a
 * spring and a soft shadow, catches a light sheen across the top, and its name fades in underneath.
 * "Moving" is the jiggle of rearrange mode.
 */
@SuppressLint("ViewConstructor")
final class Tile extends LinearLayout {
    static final float LIFT = 1.12f;

    final FrameLayout card;
    final TextView label;
    private final Ui ui;
    private final Drawable sheen;
    private ObjectAnimator jiggle;

    /** An app tile: its banner edge to edge, or its icon on a tinted card when it has no banner. */
    static Tile forApp(Ui ui, Apps.Entry e, float w, float h) {
        ImageView iv = new ImageView(ui.ctx);
        int bg;
        if (e.custom != null) {
            iv.setImageBitmap(e.custom);
            iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
            iv.setLayoutParams(new FrameLayout.LayoutParams(-1, -1));
            bg = e.customBg;
        } else if (e.banner != null) {
            iv.setImageDrawable(e.banner);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            iv.setLayoutParams(new FrameLayout.LayoutParams(-1, -1));
            bg = 0xFF1C1C1F;
        } else {
            iv.setImageDrawable(e.icon);
            iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
            int s = ui.u(h * 0.5f);
            iv.setLayoutParams(new FrameLayout.LayoutParams(s, s, Gravity.CENTER));
            bg = Ui.deepen(e.color);
        }
        return new Tile(ui, w, h, iv, bg, e.label);
    }

    /** A system tile: a white glyph on frosted glass. */
    static Tile forAction(Ui ui, int iconRes, String text, float w, float h) {
        ImageView iv = new ImageView(ui.ctx);
        iv.setImageResource(iconRes);
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int s = ui.u(h * 0.34f);
        iv.setLayoutParams(new FrameLayout.LayoutParams(s, s, Gravity.CENTER));
        return new Tile(ui, w, h, iv, 0x2EFFFFFF, text);
    }

    private Tile(Ui ui, float w, float h, View content, int bg, String text) {
        super(ui.ctx);
        this.ui = ui;
        setOrientation(VERTICAL);
        setGravity(Gravity.CENTER_HORIZONTAL);
        setFocusable(true);
        setClickable(true);
        setLongClickable(true);
        setClipChildren(false);
        setClipToPadding(false);

        card = new FrameLayout(ui.ctx);
        card.setBackground(ui.round(bg, 14));
        card.setOutlineProvider(ViewOutlineProvider.BACKGROUND);
        card.setClipToOutline(true);
        card.addView(content);
        addView(card, new LayoutParams(ui.u(w), ui.u(h)));

        label = ui.text(13, 500, Color.WHITE);
        label.setText(text);
        label.setGravity(Gravity.CENTER);
        label.setEllipsize(android.text.TextUtils.TruncateAt.END);
        label.setAlpha(0f);
        LayoutParams lp = new LayoutParams(ui.u(w), LayoutParams.WRAP_CONTENT);
        lp.topMargin = ui.u(14);
        addView(label, lp);

        // Light falling across glass: brighter at the top, gone by the middle.
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0x38FFFFFF, 0x0DFFFFFF, 0x00FFFFFF});
        g.setCornerRadius(ui.u(14));
        sheen = g;
    }

    @Override
    protected void onFocusChanged(boolean gainFocus, int direction, android.graphics.Rect previous) {
        super.onFocusChanged(gainFocus, direction, previous);
        float s = gainFocus ? LIFT : 1f;
        card.animate().scaleX(s).scaleY(s).translationZ(gainFocus ? ui.u(18) : 0)
                .setDuration(gainFocus ? 300 : 200)
                .setInterpolator(gainFocus ? new OvershootInterpolator(1.4f) : new DecelerateInterpolator())
                .start();
        label.animate().alpha(gainFocus ? 1f : 0f).translationY(gainFocus ? ui.u(4) : 0)
                .setDuration(gainFocus ? 220 : 120).start();
        card.setForeground(gainFocus ? sheen : null);
    }

    void setMoving(boolean m) {
        if (jiggle != null) {
            jiggle.cancel();
            jiggle = null;
            card.setRotation(0);
        }
        if (m) {
            jiggle = ObjectAnimator.ofFloat(card, View.ROTATION, -1.4f, 1.4f);
            jiggle.setDuration(130);
            jiggle.setRepeatMode(ValueAnimator.REVERSE);
            jiggle.setRepeatCount(ValueAnimator.INFINITE);
            jiggle.start();
        }
    }
}
