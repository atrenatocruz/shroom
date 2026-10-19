package app.shroom.tv;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * A rounded card with its name underneath. Focus lifts the card with a small spring, rings it in
 * white and brightens the name; "wiggle" is the rearrange mode.
 */
@SuppressLint("ViewConstructor")
final class Tile extends LinearLayout {
    static final float LIFT = 1.1f;

    final FrameLayout card;
    final TextView label;
    private final Ui ui;
    private final Drawable ringWhite, ringMove;
    private ObjectAnimator wiggle;
    private boolean moving;

    /** An app tile: its banner edge to edge, or its icon on a tinted card when it has no banner. */
    static Tile forApp(Ui ui, Apps.Entry e, float w, float h) {
        View content;
        int bg;
        if (e.banner != null) {
            ImageView iv = new ImageView(ui.ctx);
            iv.setImageDrawable(e.banner);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            content = iv;
            bg = 0xFF16171C;
        } else {
            ImageView iv = new ImageView(ui.ctx);
            iv.setImageDrawable(e.icon);
            iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
            int s = ui.u(h * 0.52f);
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(s, s, Gravity.CENTER);
            iv.setLayoutParams(lp);
            content = iv;
            bg = Ui.deepen(e.color);
        }
        return new Tile(ui, w, h, content, bg, e.label);
    }

    /** A system tile: a white glyph on frosted glass. */
    static Tile forAction(Ui ui, int iconRes, String text, float w, float h) {
        ImageView iv = new ImageView(ui.ctx);
        iv.setImageResource(iconRes);
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int s = ui.u(h * 0.36f);
        iv.setLayoutParams(new FrameLayout.LayoutParams(s, s, Gravity.CENTER));
        return new Tile(ui, w, h, iv, 0x40FFFFFF, text);
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
        card.setBackground(ui.round(bg, 12));
        card.setOutlineProvider(ViewOutlineProvider.BACKGROUND);
        card.setClipToOutline(true);
        card.setElevation(ui.u(2));
        if (!(content.getLayoutParams() instanceof FrameLayout.LayoutParams)) {
            content.setLayoutParams(new FrameLayout.LayoutParams(-1, -1));
        }
        card.addView(content);
        addView(card, new LayoutParams(ui.u(w), ui.u(h)));

        label = ui.text(12.5f, 450, 0xB3FFFFFF);
        label.setText(text);
        label.setGravity(Gravity.CENTER);
        label.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LayoutParams lp = new LayoutParams(ui.u(w), LayoutParams.WRAP_CONTENT);
        lp.topMargin = ui.u(12);
        addView(label, lp);

        ringWhite = ui.ring(Color.WHITE, 2.5f, 12);
        ringMove = ui.ring(0xFFFFC46B, 3f, 12);
        setFocused(false, false);
    }

    @Override
    protected void onFocusChanged(boolean gainFocus, int direction, android.graphics.Rect previous) {
        super.onFocusChanged(gainFocus, direction, previous);
        setFocused(gainFocus, true);
    }

    private void setFocused(boolean f, boolean animate) {
        float s = f ? LIFT : 1f;
        float z = f ? ui.u(14) : 0;
        if (animate) {
            card.animate().scaleX(s).scaleY(s).translationZ(z).setDuration(f ? 260 : 180)
                    .setInterpolator(new OvershootInterpolator(f ? 1.6f : 0.8f)).start();
            label.animate().translationY(f ? ui.u(6) : 0).setDuration(200).start();
        } else {
            card.setScaleX(s);
            card.setScaleY(s);
            card.setTranslationZ(z);
            label.setTranslationY(f ? ui.u(6) : 0);
        }
        card.setForeground(moving ? ringMove : f ? ringWhite : null);
        label.setTextColor(f ? Color.WHITE : 0xB3FFFFFF);
        label.setTypeface(ui.font(f ? 600 : 450));
    }

    void setMoving(boolean m) {
        moving = m;
        if (wiggle != null) {
            wiggle.cancel();
            wiggle = null;
            card.setRotation(0);
        }
        if (m) {
            wiggle = ObjectAnimator.ofFloat(card, View.ROTATION, -1.2f, 1.2f);
            wiggle.setDuration(140);
            wiggle.setRepeatMode(ValueAnimator.REVERSE);
            wiggle.setRepeatCount(ValueAnimator.INFINITE);
            wiggle.start();
        }
        setFocused(isFocused(), false);
    }
}
