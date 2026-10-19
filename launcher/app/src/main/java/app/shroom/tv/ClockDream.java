package app.shroom.tv;

import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.service.dreams.DreamService;
import android.text.format.DateFormat;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Calendar;
import java.util.Locale;
import java.util.Random;

/** Screensaver: a thin clock that drifts to a new spot every minute, so nothing burns into an OLED. */
public class ClockDream extends DreamService {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();
    private LinearLayout box;
    private TextView time, date;

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(false);
        setFullscreen(true);
        Ui ui = new Ui(this);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        time = ui.text(96, 200, 0xE6FFFFFF);
        date = ui.text(18, 400, 0x99FFFFFF);
        box.addView(time);
        box.addView(date);
        root.addView(box, new FrameLayout.LayoutParams(-2, -2));
        setContentView(root);
    }

    @Override
    public void onDreamingStarted() {
        super.onDreamingStarted();
        tick.run();
    }

    @Override
    public void onDreamingStopped() {
        super.onDreamingStopped();
        handler.removeCallbacks(tick);
    }

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            Calendar now = Calendar.getInstance();
            time.setText(DateFormat.format(DateFormat.is24HourFormat(ClockDream.this) ? "H:mm" : "h:mm", now));
            date.setText(DateFormat.format(DateFormat.getBestDateTimePattern(Locale.getDefault(), "EEEEMMMMd"), now));
            box.post(() -> {
                FrameLayout parent = (FrameLayout) box.getParent();
                int maxX = Math.max(1, parent.getWidth() - box.getWidth());
                int maxY = Math.max(1, parent.getHeight() - box.getHeight());
                box.animate().translationX(random.nextInt(maxX)).translationY(random.nextInt(maxY))
                        .alpha(1f).setDuration(1500).start();
            });
            long ms = 60_000 - (System.currentTimeMillis() % 60_000);
            handler.postDelayed(this, ms);
        }
    };
}
