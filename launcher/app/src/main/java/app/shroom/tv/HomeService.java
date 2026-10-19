package app.shroom.tv;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityEvent;

/**
 * For TVs that won't hand Home to another app (Fire OS): whenever the stock home screen comes to the
 * front, open Shroom over it. Only the home activity itself is redirected, so the stock launcher's
 * other screens (Settings, app library, profiles) still work. Opt-in, enabled by tools\install.ps1
 * -HomeHelper and removed by tools\restore.ps1.
 */
public class HomeService extends AccessibilityService {
    /** Stock home activities to step in front of. */
    private static final String[] STOCK_HOMES = {
            "com.amazon.tv.launcher.ui.HomeActivity_vNext",
            "com.amazon.tv.launcher.ui.HomeActivity",
    };
    /** If Shroom keeps failing to come up, stop trying instead of fighting the stock home in a loop. */
    private static final int MAX_TRIES = 3;
    private static final long TRY_WINDOW_MS = 10_000;

    private final long[] tries = new long[MAX_TRIES];
    private int next;

    @Override
    public void onAccessibilityEvent(AccessibilityEvent e) {
        if (e.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || e.getClassName() == null) return;
        String cls = e.getClassName().toString();
        boolean stock = false;
        for (String h : STOCK_HOMES) stock |= h.equals(cls);
        if (!stock) return;

        long now = SystemClock.elapsedRealtime();
        if (now - tries[next] < TRY_WINDOW_MS) return;   // MAX_TRIES already within the window
        tries[next] = now;
        next = (next + 1) % MAX_TRIES;
        startActivity(new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                        | Intent.FLAG_ACTIVITY_NO_ANIMATION));
    }

    @Override
    public void onInterrupt() {
    }
}
