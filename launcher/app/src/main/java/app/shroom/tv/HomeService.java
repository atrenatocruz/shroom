package app.shroom.tv;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityEvent;

/**
 * For TVs that won't hand Home to another app (Fire OS): whenever the stock home screen comes to the
 * front and stays there, open Shroom over it. Fire OS also passes through its home activity on the way
 * to other launcher screens (Settings opens HomeActivity_vNext, which then opens MainSettingsActivity),
 * so the redirect waits a moment and stands down if another screen of the launcher follows. Opt-in,
 * enabled by tools\install.ps1 -HomeHelper and removed by tools\restore.ps1.
 */
public class HomeService extends AccessibilityService {
    /** Stock home activities to step in front of. */
    private static final String[] STOCK_HOMES = {
            "com.amazon.tv.launcher.ui.HomeActivity_vNext",
            "com.amazon.tv.launcher.ui.HomeActivity",
    };
    /** Long enough for a trampoline to open its real screen, short enough not to show the stock home. */
    private static final long SETTLE_MS = 450;
    /** If Shroom keeps failing to come up, stop trying instead of fighting the stock home in a loop. */
    private static final int MAX_TRIES = 3;
    private static final long TRY_WINDOW_MS = 10_000;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final long[] tries = new long[MAX_TRIES];
    private int next;

    private final Runnable redirect = () -> {
        long now = SystemClock.elapsedRealtime();
        if (now - tries[next] < TRY_WINDOW_MS) return;   // MAX_TRIES already within the window
        tries[next] = now;
        next = (next + 1) % MAX_TRIES;
        startActivity(new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                        | Intent.FLAG_ACTIVITY_NO_ANIMATION));
    };

    @Override
    public void onAccessibilityEvent(AccessibilityEvent e) {
        if (e.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || e.getClassName() == null) return;
        String cls = e.getClassName().toString();
        boolean stock = false;
        for (String h : STOCK_HOMES) stock |= h.equals(cls);
        handler.removeCallbacks(redirect);
        // Any other launcher screen (Settings, the app library) cancels a pending redirect.
        if (stock) handler.postDelayed(redirect, SETTLE_MS);
    }

    @Override
    public void onInterrupt() {
        handler.removeCallbacks(redirect);
    }
}
