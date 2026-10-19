package app.shroom.tv;

import android.app.Activity;
import android.app.ActivityOptions;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.format.DateFormat;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * The home screen, laid out like tvOS: the sky is the top shelf, the Top Row of favourites sits in a
 * frosted band below it, and the rest of the apps wait underneath. Pressing Down from the Top Row
 * slides everything up and dims the sky; Up, Back or Home brings it back.
 */
public class MainActivity extends Activity {
    private static final int COLUMNS = 5;
    private static final float TILE_W = 156, TILE_H = 88, GAP = 18;
    private static final float BAND_X = 32, BAND_PAD_X = 22, BAND_PAD_TOP = 20, BAND_PAD_BOTTOM = 14;
    private static final float DOCK_Y = 316;       // top of the Top Row band on the 960 x 540 grid
    private static final float GRID_GAP = 38;      // band bottom to first grid row
    private static final float ROW_GAP = 8;        // between grid rows (the name under a tile sits in it)
    private static final float FOCUS_ROW_Y = 96;   // where a focused grid row settles
    private static final String ACTION_VIEW_INPUTS = "com.android.tv.action.VIEW_INPUTS";

    private Ui ui;
    private Apps apps;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final PathInterpolator glide = new PathInterpolator(0.2f, 0.8f, 0.2f, 1f);

    private FrameLayout root, stage;
    private Backdrop backdrop;
    private TextView clock, greeting, dateLine, weather;
    private View header, settingsButton, topFade;
    private long weatherAt;
    private LinearLayout dockRow, grid;
    private FrameLayout band;
    private HorizontalScrollView dockScroll;
    private final List<Tile> dockTiles = new ArrayList<>();
    private final List<Tile> gridTiles = new ArrayList<>();
    private boolean inGrid;
    private int lastDock, lastGrid;
    private int moving = -1;
    private FrameLayout menu;

    // ---------------------------------------------------------------- lifecycle

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        ui = new Ui(this);
        apps = new Apps(this);
        readExtras(getIntent());
        buildUi();
        apps.reload();
        buildTiles();
        focusDock(0);

        IntentFilter pf = new IntentFilter();
        pf.addAction(Intent.ACTION_PACKAGE_ADDED);
        pf.addAction(Intent.ACTION_PACKAGE_REMOVED);
        pf.addAction(Intent.ACTION_PACKAGE_CHANGED);
        pf.addDataScheme("package");
        registerReceiver(packages, pf);
    }

    @Override
    protected void onResume() {
        super.onResume();
        IntentFilter tf = new IntentFilter(Intent.ACTION_TIME_TICK);
        tf.addAction(Intent.ACTION_TIME_CHANGED);
        tf.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        registerReceiver(tick, tf);
        refreshTime();
        if (System.currentTimeMillis() - weatherAt > WEATHER_EVERY) refreshWeather();
        main.postDelayed(weatherTimer, WEATHER_EVERY);
    }

    @Override
    protected void onPause() {
        super.onPause();
        main.removeCallbacks(weatherTimer);
        try {
            unregisterReceiver(tick);
        } catch (IllegalArgumentException ignored) {
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            unregisterReceiver(packages);
        } catch (IllegalArgumentException ignored) {
        }
    }

    /** Home pressed while already home: back to the first app in the Top Row. */
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (readExtras(intent)) refreshWeather();
        refreshTime();
        closeMenu();
        endMove();
        focusDock(0);
    }

    /** Nothing focused (first start, or the focused view went away): land in the Top Row. */
    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus && getCurrentFocus() == null && menu == null) focusDock(lastDock);
    }

    /**
     * Settings over adb, no rebuild: {@code am start -n app.shroom.tv/.MainActivity --es names "Raquel & Renato"
     * --es city Lisbon}. Returns whether anything changed.
     */
    private boolean readExtras(Intent i) {
        if (i == null) return false;
        boolean changed = false;
        for (String key : new String[]{"names", "city"}) {
            if (i.hasExtra(key)) {
                apps.setText(key, i.getStringExtra(key));
                i.removeExtra(key);
                changed = true;
            }
        }
        if (changed) apps.prefs().edit().remove("loc_key").apply();
        return changed;
    }

    private static final long WEATHER_EVERY = 30 * 60 * 1000L;

    private final Runnable weatherTimer = new Runnable() {
        @Override
        public void run() {
            refreshWeather();
            main.postDelayed(this, WEATHER_EVERY);
        }
    };

    private void refreshWeather() {
        weatherAt = System.currentTimeMillis();
        Weather.fetch(apps.prefs(), apps.city(), text -> {
            weather.setText(text == null ? "" : text);
            weather.setVisibility(text == null || text.isEmpty() ? View.GONE : View.VISIBLE);
        });
    }

    private final BroadcastReceiver tick = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            refreshTime();
        }
    };

    private final BroadcastReceiver packages = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            main.removeCallbacks(reloadApps);
            main.postDelayed(reloadApps, 400);   // installs fire several broadcasts in a row
        }
    };

    private final Runnable reloadApps = () -> {
        apps.reload();
        buildTiles();
    };

    // ---------------------------------------------------------------- layout

    private void buildUi() {
        root = new FrameLayout(this);
        root.setClipChildren(false);
        backdrop = new Backdrop(this);
        root.addView(backdrop, new FrameLayout.LayoutParams(-1, -1));

        // Everything that scrolls lives on one stage that slides as a whole: no ScrollView, so the
        // motion is exactly the one we animate.
        stage = new FrameLayout(this) {
            @Override
            protected void onMeasure(int widthSpec, int heightSpec) {
                // As tall as its content, even past the bottom of the screen: rows below the fold
                // must have real size to be focusable.
                super.onMeasure(widthSpec, MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
            }
        };
        stage.setClipChildren(false);
        stage.setClipToPadding(false);
        root.addView(stage, new FrameLayout.LayoutParams(-1, -2));

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setClipChildren(false);
        col.setClipToPadding(false);
        col.setPadding(0, ui.u(DOCK_Y), 0, ui.u(60));
        stage.addView(col, new FrameLayout.LayoutParams(-1, -2));

        band = new FrameLayout(this);
        band.setBackground(ui.round(0x29FFFFFF, 26));
        band.setClipChildren(false);
        band.setClipToPadding(false);
        dockScroll = new HorizontalScrollView(this);
        dockScroll.setHorizontalScrollBarEnabled(false);
        dockScroll.setClipChildren(false);
        dockScroll.setClipToPadding(false);
        dockScroll.setPadding(ui.u(BAND_PAD_X), ui.u(BAND_PAD_TOP), ui.u(BAND_PAD_X), ui.u(BAND_PAD_BOTTOM));
        dockScroll.setFillViewport(true);   // a short Top Row is centred in the band, a long one scrolls
        dockRow = new LinearLayout(this);
        dockRow.setGravity(Gravity.CENTER_HORIZONTAL);
        dockRow.setClipChildren(false);
        dockRow.setClipToPadding(false);
        dockScroll.addView(dockRow, new FrameLayout.LayoutParams(-2, -2));
        band.addView(dockScroll, new FrameLayout.LayoutParams(-1, -2));
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(-1, -2);
        blp.leftMargin = ui.u(BAND_X);
        blp.rightMargin = ui.u(BAND_X);
        col.addView(band, blp);

        grid = new LinearLayout(this);
        grid.setAlpha(0f);   // out of sight until Down: the home shows only the Top Row
        grid.setOrientation(LinearLayout.VERTICAL);
        grid.setClipChildren(false);
        grid.setClipToPadding(false);
        // The grid is a centred block of COLUMNS tiles, lined up under the centred Top Row.
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(ui.u(COLUMNS * TILE_W + (COLUMNS - 1) * GAP), -2);
        glp.gravity = Gravity.CENTER_HORIZONTAL;
        glp.topMargin = ui.u(GRID_GAP);
        col.addView(grid, glp);

        // While browsing the grid, rows sliding up under the header fade into this.
        topFade = new View(this);
        topFade.setBackground(new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xF20A0A10, 0xCC0A0A10, 0x000A0A10}));
        topFade.setAlpha(0f);
        root.addView(topFade, new FrameLayout.LayoutParams(-1, ui.u(FOCUS_ROW_Y + 8), Gravity.TOP));
        root.addView(buildHeader(), new FrameLayout.LayoutParams(-1, ui.u(96), Gravity.TOP));

        setContentView(root);
    }

    /** Greeting and date top left; weather, the settings button and the clock top right. */
    private View buildHeader() {
        FrameLayout bar = new FrameLayout(this);
        bar.setClipChildren(false);

        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        greeting = ui.text(25, 500, Color.WHITE);
        greeting.setShadowLayer(ui.u(10), 0, ui.u(1), 0x59000000);
        dateLine = ui.text(14, 400, 0xCCFFFFFF);
        dateLine.setShadowLayer(ui.u(8), 0, 0, 0x59000000);
        left.addView(greeting);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(-2, -2);
        dlp.topMargin = ui.u(7);
        left.addView(dateLine, dlp);
        // Fixed width: a wrap_content column kept the width of the first text it measured and cut the greeting.
        FrameLayout.LayoutParams llp = new FrameLayout.LayoutParams(ui.u(560), -2, Gravity.TOP | Gravity.START);
        llp.leftMargin = ui.u(BAND_X + 8);
        llp.topMargin = ui.u(30);
        bar.addView(left, llp);
        header = left;

        LinearLayout right = new LinearLayout(this);
        right.setGravity(Gravity.CENTER_VERTICAL);
        right.setClipChildren(false);
        weather = ui.text(16, 400, 0xE6FFFFFF);
        weather.setShadowLayer(ui.u(8), 0, 0, 0x59000000);
        weather.setVisibility(View.GONE);
        right.addView(weather);

        ImageView gear = new ImageView(this);
        gear.setImageResource(R.drawable.ic_settings);
        gear.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        gear.setPadding(ui.u(8), ui.u(8), ui.u(8), ui.u(8));
        gear.setBackground(ui.round(0x29FFFFFF, 17));
        gear.setFocusable(true);
        gear.setId(View.generateViewId());
        gear.setOnFocusChangeListener((v, f) -> {
            v.setBackground(ui.round(f ? Color.WHITE : 0x29FFFFFF, 17));
            ((ImageView) v).setColorFilter(f ? 0xFF111114 : Color.WHITE);
            v.animate().scaleX(f ? 1.12f : 1f).scaleY(f ? 1.12f : 1f).setDuration(200).start();
            if (f) showGrid(false);
        });
        gear.setOnClickListener(v -> homeMenu());
        gear.setOnKeyListener((v, code, ev) -> {
            if (ev.getAction() != KeyEvent.ACTION_DOWN) return false;
            if (code == KeyEvent.KEYCODE_DPAD_DOWN) {
                focusDock(lastDock);
                return true;
            }
            return code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_DPAD_LEFT
                    || code == KeyEvent.KEYCODE_DPAD_RIGHT;
        });
        settingsButton = gear;
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(ui.u(34), ui.u(34));
        glp.leftMargin = ui.u(18);
        right.addView(gear, glp);

        clock = ui.text(22, 400, Color.WHITE);
        clock.setShadowLayer(ui.u(8), 0, 0, 0x59000000);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(-2, -2);
        clp.leftMargin = ui.u(18);
        right.addView(clock, clp);

        FrameLayout.LayoutParams rlp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.END);
        rlp.rightMargin = ui.u(BAND_X + 8);
        rlp.topMargin = ui.u(28);
        bar.addView(right, rlp);
        return bar;
    }

    private void buildTiles() {
        View had = getCurrentFocus();
        boolean wasDock = dockTiles.contains(had), wasGrid = gridTiles.contains(had);

        dockRow.removeAllViews();
        dockTiles.clear();
        List<Apps.Entry> favs = apps.favorites();
        // Up to 7 apps fit the band by shrinking a little; more than that scroll.
        float bandInner = 960 - 2 * (BAND_X + BAND_PAD_X);
        int n = Math.max(1, Math.min(favs.size(), 7));
        float dockW = Math.min(TILE_W, (bandInner - (n - 1) * GAP) / n), dockH = dockW * TILE_H / TILE_W;
        for (int i = 0; i < favs.size(); i++) {
            final int idx = i;
            final Apps.Entry e = favs.get(i);
            Tile t = Tile.forApp(ui, e, dockW, dockH);
            t.setOnFocusChangeListener((v, f) -> {
                if (!f) return;
                lastDock = idx;
                backdrop.setGlow(e.color);
                showGrid(false);
            });
            t.setOnClickListener(v -> launch(e, v));
            t.setOnLongClickListener(v -> {
                dockMenu(idx, e);
                return true;
            });
            addSpaced(dockRow, t, i);
            dockTiles.add(t);
        }
        if (dockTiles.isEmpty()) {
            TextView empty = ui.text(14, 400, 0x99FFFFFF);
            empty.setText(R.string.no_favorites);
            empty.setGravity(Gravity.CENTER_VERTICAL);
            dockRow.addView(empty, new LinearLayout.LayoutParams(-2, ui.u(TILE_H)));
        }

        grid.removeAllViews();
        gridTiles.clear();
        List<Apps.Entry> rest = new ArrayList<>(apps.all);
        rest.removeAll(favs);
        for (final Apps.Entry e : rest) {
            Tile t = Tile.forApp(ui, e, TILE_W, TILE_H);
            t.setOnClickListener(v -> launch(e, v));
            t.setOnLongClickListener(v -> {
                gridMenu(e);
                return true;
            });
            gridTiles.add(t);
        }
        if (hasInputs()) {
            Tile t = Tile.forAction(ui, R.drawable.ic_input, getString(R.string.inputs), TILE_W, TILE_H);
            t.setOnClickListener(v -> showInputs());
            t.setOnLongClickListener(v -> {
                homeMenu();
                return true;
            });
            gridTiles.add(t);
        }
        LinearLayout row = null;
        for (int i = 0; i < gridTiles.size(); i++) {
            final int idx = i;
            if (i % COLUMNS == 0) {
                row = new LinearLayout(this);
                row.setClipChildren(false);
                row.setClipToPadding(false);
                LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(-2, -2);
                if (i > 0) rlp.topMargin = ui.u(ROW_GAP);
                grid.addView(row, rlp);
            }
            Tile t = gridTiles.get(i);
            View.OnFocusChangeListener prev = t.getOnFocusChangeListener();
            t.setOnFocusChangeListener((v, f) -> {
                if (prev != null) prev.onFocusChange(v, f);
                if (!f) return;
                lastGrid = idx;
                showGrid(true);
            });
            addSpaced(row, t, i % COLUMNS);
        }
        wireFocus();

        if (wasDock) focusDock(lastDock);
        else if (wasGrid) focusGrid(lastGrid);
    }

    private void addSpaced(LinearLayout row, View v, int indexInRow) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        if (indexInRow > 0) lp.leftMargin = ui.u(GAP);
        row.addView(v, lp);
    }

    /** Deterministic D-pad: edges stay put, Up from the grid's first row returns to the Top Row. */
    private void wireFocus() {
        for (Tile t : dockTiles) {
            t.setId(View.generateViewId());
            t.setNextFocusUpId(settingsButton.getId());
        }
        for (Tile t : gridTiles) t.setId(View.generateViewId());
        if (!dockTiles.isEmpty()) {
            Tile first = dockTiles.get(0), last = dockTiles.get(dockTiles.size() - 1);
            first.setNextFocusLeftId(first.getId());
            last.setNextFocusRightId(last.getId());
            if (gridTiles.isEmpty()) for (Tile t : dockTiles) t.setNextFocusDownId(t.getId());
        }
        for (int i = 0; i < gridTiles.size(); i++) {
            Tile t = gridTiles.get(i);
            if (i % COLUMNS == 0) t.setNextFocusLeftId(t.getId());
            if (i % COLUMNS == COLUMNS - 1 || i == gridTiles.size() - 1) t.setNextFocusRightId(t.getId());
            if (i + COLUMNS >= gridTiles.size()) {
                // Last row: Down goes to the tile below if the row under is shorter, else stays.
                t.setNextFocusDownId(t.getId());
            }
        }
    }

    // ---------------------------------------------------------------- focus & motion

    private void focusDock(int i) {
        if (dockTiles.isEmpty()) {
            if (!gridTiles.isEmpty()) focusGrid(0);
            return;
        }
        dockTiles.get(Math.max(0, Math.min(i, dockTiles.size() - 1))).requestFocusFromTouch();
    }

    private void focusGrid(int i) {
        if (gridTiles.isEmpty()) {
            focusDock(lastDock);
            return;
        }
        gridTiles.get(Math.max(0, Math.min(i, gridTiles.size() - 1))).requestFocusFromTouch();
    }

    /** Slides the stage so the focused row sits high on screen, or back so the sky shows. */
    private void showGrid(boolean show) {
        float target = 0;
        if (show) {
            View row = (View) gridTiles.get(lastGrid).getParent();
            int[] rowLoc = new int[2], stageLoc = new int[2];
            row.getLocationOnScreen(rowLoc);
            stage.getLocationOnScreen(stageLoc);
            float rowTopInStage = rowLoc[1] - stageLoc[1];
            target = ui.u(FOCUS_ROW_Y) - rowTopInStage;
        }
        if (show != inGrid) {
            inGrid = show;
            backdrop.setDim(show ? 0.6f : 0f);
            if (show) backdrop.setGlow(0xFF15161B);
            band.animate().alpha(show ? 0f : 1f).setDuration(show ? 220 : 320).start();
            topFade.animate().alpha(show ? 1f : 0f).setDuration(show ? 220 : 320).start();
            grid.animate().alpha(show ? 1f : 0f).setDuration(show ? 320 : 200).start();
            header.animate().alpha(show ? 0f : 1f).setDuration(show ? 200 : 320).start();
        }
        stage.animate().translationY(target).setDuration(420).setInterpolator(glide).start();
    }

    // ---------------------------------------------------------------- time & sky

    private void refreshTime() {
        Calendar now = Calendar.getInstance();
        int h = now.get(Calendar.HOUR_OF_DAY);
        clock.setText(DateFormat.format("HH:mm", now));
        int greet = h >= 5 && h < 12 ? R.string.greet_morning
                : h >= 12 && h < 18 ? R.string.greet_afternoon
                : h >= 18 && h < 22 ? R.string.greet_evening : R.string.greet_night;
        String names = apps.names();
        greeting.setText(names.isEmpty() ? getString(greet) : getString(greet) + ", " + names);
        dateLine.setText(DateFormat.format(DateFormat.getBestDateTimePattern(Locale.getDefault(), "EEEEMMMMd"), now));
        if (!backgroundLoaded) {
            backgroundLoaded = true;
            backgroundDay = now.get(Calendar.DAY_OF_YEAR);
            showBackground(currentBackground(), 900);
        } else if (dailyBackground() && now.get(Calendar.DAY_OF_YEAR) != backgroundDay) {
            backgroundDay = now.get(Calendar.DAY_OF_YEAR);
            showBackground(currentBackground(), 1500);   // a new day, a new picture
        }
    }

    // ---------------------------------------------------------------- backgrounds

    /** The built-in sky; every other background is a JPEG in assets/backgrounds (tools/backgrounds.py). */
    private static final String SUNSET = "sunset";
    private boolean backgroundLoaded;
    private int backgroundToken, backgroundDay;

    private boolean dailyBackground() {
        return apps.prefs().getBoolean("background_daily", false);
    }

    /** The chosen picture, or with "Change Every Day" on, the next one in the list each day. */
    private String currentBackground() {
        if (!dailyBackground()) return apps.prefs().getString("background", SUNSET);
        List<String> all = backgrounds();
        long day = System.currentTimeMillis() / 86_400_000L
                + java.util.TimeZone.getDefault().getOffset(System.currentTimeMillis()) / 86_400_000L;
        return all.get((int) (day % all.size()));
    }

    private List<String> backgrounds() {
        List<String> out = new ArrayList<>();
        out.add(SUNSET);
        try {
            String[] files = getAssets().list("backgrounds");
            if (files != null) {
                java.util.Arrays.sort(files);
                for (String f : files) if (f.endsWith(".jpg")) out.add(f.substring(0, f.length() - 4));
            }
        } catch (java.io.IOException ignored) {
        }
        return out;
    }

    private static String backgroundLabel(String key) {
        String t = key.replace('-', ' ');
        return Character.toUpperCase(t.charAt(0)) + t.substring(1);
    }

    /** Decodes off the main thread; only the latest request is shown, so fast scrolling never stutters. */
    private void showBackground(String key, int fadeMs) {
        final int token = ++backgroundToken;
        new Thread(() -> {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inPreferredConfig = Bitmap.Config.RGB_565;   // half the memory
            Bitmap b = null;
            try {
                if (!SUNSET.equals(key)) {
                    try (java.io.InputStream in = getAssets().open("backgrounds/" + key + ".jpg")) {
                        b = BitmapFactory.decodeStream(in, null, o);
                    }
                }
            } catch (java.io.IOException ignored) {
                // a picture that was removed: fall back to the sky
            }
            if (b == null) b = BitmapFactory.decodeResource(getResources(), R.drawable.sky_sunset, o);
            final Bitmap bitmap = b;
            main.post(() -> {
                if (token == backgroundToken) backdrop.setWallpaper(bitmap, fadeMs);
            });
        }, "background").start();
    }

    /** Settings > Backgrounds: moving through the list changes the screen behind it; OK keeps it, Back undoes. */
    private void backgroundPicker() {
        final boolean daily = dailyBackground();
        final String current = currentBackground();
        List<String> keys = backgrounds();
        int n = keys.size() + 1;
        String[] labels = new String[n];
        Runnable[] pick = new Runnable[n], preview = new Runnable[n];
        // First item: the daily switch. Choosing a picture below turns it off.
        labels[0] = getString(R.string.change_daily) + (daily ? "  ✓" : "");
        preview[0] = () -> showBackground(current, 450);
        pick[0] = () -> {
            apps.prefs().edit().putBoolean("background_daily", !daily).apply();
            showBackground(currentBackground(), 450);
        };
        int start = 0;
        for (int i = 0; i < keys.size(); i++) {
            final String key = keys.get(i);
            labels[i + 1] = backgroundLabel(key) + (!daily && key.equals(current) ? "  ✓" : "");
            if (!daily && key.equals(current)) start = i + 1;
            preview[i + 1] = () -> showBackground(key, 450);
            pick[i + 1] = () -> apps.prefs().edit().putString("background", key).putBoolean("background_daily", false).apply();
        }
        showMenu(getString(R.string.backgrounds), null, labels, pick, preview, () -> showBackground(current, 450), start);
    }

    // ---------------------------------------------------------------- actions

    private void launch(Apps.Entry e, View from) {
        Intent i = new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
                .setComponent(e.component)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        Bundle anim = null;
        if (from instanceof Tile) {
            View card = ((Tile) from).card;
            anim = ActivityOptions.makeScaleUpAnimation(card, 0, 0, card.getWidth(), card.getHeight()).toBundle();
        }
        try {
            startActivity(i, anim);
        } catch (ActivityNotFoundException | SecurityException ex) {
            Intent fallback = getPackageManager().getLeanbackLaunchIntentForPackage(e.pkg);
            if (fallback != null) open(fallback);
            else toast(R.string.not_available);
        }
    }

    /**
     * Fire OS answers the standard Settings action by bouncing through its home screen (which the Home
     * helper would cover); its real settings screen opens directly by name.
     */
    private static final android.content.ComponentName FIRE_SETTINGS =
            new android.content.ComponentName("com.amazon.tv.launcher", "com.amazon.tv.launcher.ui.MainSettingsActivity");

    private Intent tvSettingsIntent() {
        Intent fire = new Intent().setComponent(FIRE_SETTINGS);
        if (getPackageManager().resolveActivity(fire, 0) != null) return fire;
        return new Intent(Settings.ACTION_SETTINGS);
    }

    private boolean canOpen(String action) {
        return getPackageManager().resolveActivity(new Intent(action), 0) != null;
    }

    private void openSettings(String action) {
        open(Settings.ACTION_SETTINGS.equals(action) ? tvSettingsIntent() : new Intent(action));
    }

    private void open(Intent i) {
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(i);
        } catch (ActivityNotFoundException | SecurityException e) {
            if (!Settings.ACTION_SETTINGS.equals(i.getAction()) && !FIRE_SETTINGS.equals(i.getComponent())) {
                // Many TVs ship a trimmed Settings app: fall back to its main page.
                try {
                    startActivity(tvSettingsIntent().addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                    return;
                } catch (ActivityNotFoundException ignored) {
                }
            }
            toast(R.string.not_available);
        }
    }

    private void toast(int res) {
        Toast.makeText(this, res, Toast.LENGTH_SHORT).show();
    }

    private void openAppInfo(String pkg) {
        open(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + pkg)));
    }

    private void dockMenu(int idx, Apps.Entry e) {
        showMenu(e.label, null,
                new String[]{getString(R.string.move), getString(R.string.remove_favorite),
                        getString(R.string.hide), getString(R.string.app_info)},
                new Runnable[]{() -> startMove(idx), () -> {
                    apps.toggleFavorite(e.pkg);
                    buildTiles();
                    focusDock(idx);
                }, () -> hideApp(e), () -> openAppInfo(e.pkg)});
    }

    private void gridMenu(Apps.Entry e) {
        showMenu(e.label, null,
                new String[]{getString(R.string.add_favorite), getString(R.string.hide), getString(R.string.app_info)},
                new Runnable[]{() -> {
                    apps.toggleFavorite(e.pkg);
                    buildTiles();
                    focusDock(dockTiles.size() - 1);
                }, () -> hideApp(e), () -> openAppInfo(e.pkg)});
    }

    /** Hides a tile from Shroom only; the app itself stays installed and working. */
    private void hideApp(Apps.Entry e) {
        boolean wasGrid = inGrid;
        apps.hide(e.pkg);
        apps.reload();
        buildTiles();
        if (wasGrid) focusGrid(lastGrid);
        else focusDock(lastDock);
    }

    /** The launcher's own options; also on the remote's Menu key. Includes the way back to stock. */
    private void homeMenu() {
        String version = "";
        try {
            version = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception ignored) {
        }
        // Only offer what this TV actually has (Fire OS has no screensaver or home-picker screens).
        List<String> labels = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();
        labels.add(getString(R.string.tv_settings));
        actions.add(() -> openSettings(Settings.ACTION_SETTINGS));
        if (canOpen(Settings.ACTION_DREAM_SETTINGS)) {
            labels.add(getString(R.string.screensaver));
            actions.add(() -> openSettings(Settings.ACTION_DREAM_SETTINGS));
        }
        labels.add(getString(R.string.backgrounds));
        actions.add(this::backgroundPicker);
        labels.add(getString(R.string.show_hidden));
        actions.add(() -> {
            apps.unhideAll();
            apps.reload();
            buildTiles();
            focusDock(lastDock);
        });
        // Fire OS has the screen but locks Home to Amazon's launcher, so the picker closes at once.
        boolean fireOs = getPackageManager().resolveActivity(new Intent().setComponent(FIRE_SETTINGS), 0) != null;
        if (!fireOs && canOpen(Settings.ACTION_HOME_SETTINGS)) {
            labels.add(getString(R.string.home_picker));
            actions.add(() -> openSettings(Settings.ACTION_HOME_SETTINGS));
        }
        showMenu(getString(R.string.app_name), getString(R.string.about_body, version),
                labels.toArray(new String[0]), actions.toArray(new Runnable[0]));
    }

    // ---------------------------------------------------------------- inputs

    private boolean hasInputs() {
        TvInputManager tim = (TvInputManager) getSystemService(TV_INPUT_SERVICE);
        if (tim != null) {
            try {
                for (TvInputInfo info : tim.getTvInputList()) if (info.isPassthroughInput() || isTuner(info)) return true;
            } catch (Exception ignored) {
            }
        }
        return getPackageManager().resolveActivity(new Intent(ACTION_VIEW_INPUTS), 0) != null;
    }

    private static boolean isTuner(TvInputInfo info) {
        return info.getType() == TvInputInfo.TYPE_TUNER && info.getServiceInfo() != null
                && (info.getServiceInfo().applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
    }

    /** HDMI / AV ports and the tuner, switched the standard way: ACTION_VIEW on the input's channel URI. */
    private void showInputs() {
        List<String> labels = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();
        TvInputManager tim = (TvInputManager) getSystemService(TV_INPUT_SERVICE);
        if (tim != null) {
            try {
                for (TvInputInfo info : tim.getTvInputList()) {
                    boolean tuner = isTuner(info);
                    if (!info.isPassthroughInput() && !tuner) continue;
                    CharSequence l = info.loadLabel(this);
                    String label = tuner ? getString(R.string.live_tv) : l == null ? info.getId() : l.toString();
                    if (info.getType() == TvInputInfo.TYPE_HDMI && !label.toUpperCase(Locale.ROOT).contains("HDMI")) {
                        label = "HDMI " + label;
                    }
                    final Uri uri = TvContract.buildChannelUriForPassthroughInput(info.getId());
                    labels.add(label);
                    actions.add(() -> open(new Intent(Intent.ACTION_VIEW).setDataAndType(uri, "vnd.android.cursor.item/channel")));
                }
            } catch (Exception ignored) {
            }
        }
        Intent panel = new Intent(ACTION_VIEW_INPUTS);
        if (labels.isEmpty() && getPackageManager().resolveActivity(panel, 0) != null) {
            open(panel);
            return;
        }
        if (labels.isEmpty()) {
            toast(R.string.no_inputs);
            return;
        }
        showMenu(getString(R.string.inputs), null, labels.toArray(new String[0]), actions.toArray(new Runnable[0]));
    }

    // ---------------------------------------------------------------- menu overlay

    /** A tvOS-style context menu: a small dark glass card, centred, one white pill for the focused item. */
    private void showMenu(String heading, String body, String[] labels, Runnable[] actions) {
        showMenu(heading, body, labels, actions, null, null, 0);
    }

    /**
     * With {@code onFocus}, each item previews itself when focused: the menu moves to the side over a
     * light scrim so the screen behind shows, and Back runs {@code onCancel}.
     */
    private void showMenu(String heading, String body, String[] labels, Runnable[] actions,
                          Runnable[] onFocus, Runnable onCancel, int startAt) {
        View returnTo = menu != null ? (View) menu.getTag() : getCurrentFocus();
        closeMenu();
        boolean preview = onFocus != null;
        menuCancel = onCancel;
        menu = new FrameLayout(this);
        menu.setBackgroundColor(preview ? 0x1A000000 : 0x80000000);
        menu.setClickable(true);

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackground(ui.round(0xF0232428, 24));
        panel.setPadding(ui.u(10), ui.u(18), ui.u(10), ui.u(10));
        panel.setElevation(ui.u(30));
        TextView h = ui.text(13, 600, 0x99FFFFFF);
        h.setText(heading);
        h.setPadding(ui.u(14), 0, ui.u(14), 0);
        panel.addView(h);
        if (body != null) {
            TextView b = ui.text(13, 400, 0x80FFFFFF);
            b.setSingleLine(false);
            b.setLineSpacing(ui.u(3), 1f);
            b.setText(body);
            b.setPadding(ui.u(14), 0, ui.u(14), 0);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.topMargin = ui.u(6);
            panel.addView(b, lp);
        }
        View first = null;
        // Long lists scroll inside the card instead of running off the screen.
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        final int maxVisible = 7;
        if (labels.length > maxVisible) {
            android.widget.ScrollView sv = new android.widget.ScrollView(this);
            sv.setVerticalScrollBarEnabled(false);
            sv.setVerticalFadingEdgeEnabled(true);
            sv.setFadingEdgeLength(ui.u(24));
            sv.addView(list);
            panel.addView(sv, new LinearLayout.LayoutParams(-1, ui.u(12 + maxVisible * 44)));
        } else {
            panel.addView(list);
        }
        for (int i = 0; i < labels.length; i++) {
            final Runnable r = actions[i];
            final Runnable focusAction = preview ? onFocus[i] : null;
            TextView btn = ui.text(16, 500, Color.WHITE);
            btn.setText(labels[i]);
            btn.setGravity(Gravity.CENTER_VERTICAL);
            btn.setPadding(ui.u(14), 0, ui.u(14), 0);
            btn.setFocusable(true);
            btn.setOnFocusChangeListener((v, f) -> {
                v.setBackground(f ? ui.round(Color.WHITE, 14) : null);
                ((TextView) v).setTextColor(f ? 0xFF111114 : Color.WHITE);
                v.animate().scaleX(f ? 1.03f : 1f).scaleY(f ? 1.03f : 1f).setDuration(160).start();
                if (f && focusAction != null) focusAction.run();
            });
            btn.setOnClickListener(v -> {
                menuCancel = null;
                closeMenu();
                r.run();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, ui.u(42));
            lp.topMargin = ui.u(i == 0 ? 12 : 2);
            list.addView(btn, lp);
            if (first == null || i == startAt) first = btn;
        }
        FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(ui.u(preview ? 260 : 300), -2,
                preview ? Gravity.CENTER_VERTICAL | Gravity.END : Gravity.CENTER);
        if (preview) plp.rightMargin = ui.u(48);
        menu.addView(panel, plp);
        menu.setTag(returnTo);
        stage.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        root.addView(menu, new FrameLayout.LayoutParams(-1, -1));
        menu.setAlpha(0f);
        panel.setScaleX(0.92f);
        panel.setScaleY(0.92f);
        menu.animate().alpha(1f).setDuration(180).start();
        panel.animate().scaleX(1f).scaleY(1f).setDuration(260).setInterpolator(glide).start();
        if (first != null) first.requestFocus();
    }

    private Runnable menuCancel;

    /** Back in a menu: undo any live preview, then close. */
    private void cancelMenu() {
        Runnable c = menuCancel;
        menuCancel = null;
        closeMenu();
        if (c != null) c.run();
    }

    private void closeMenu() {
        menuCancel = null;
        if (menu == null) return;
        View returnTo = (View) menu.getTag();
        root.removeView(menu);
        menu = null;
        stage.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        if (returnTo != null && returnTo.isAttachedToWindow()) returnTo.requestFocus();
        else focusDock(lastDock);
    }

    // ---------------------------------------------------------------- rearranging

    private void startMove(int idx) {
        moving = idx;
        focusDock(idx);
        dockTiles.get(idx).setMoving(true);
    }

    private void endMove() {
        if (moving < 0) return;
        if (moving < dockTiles.size()) dockTiles.get(moving).setMoving(false);
        moving = -1;
    }

    private void moveBy(int dir) {
        if (!apps.moveFavorite(moving, dir)) return;
        moving += dir;
        buildTiles();
        focusDock(moving);
        dockTiles.get(moving).setMoving(true);
    }

    // ---------------------------------------------------------------- keys

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        int code = e.getKeyCode();
        boolean down = e.getAction() == KeyEvent.ACTION_DOWN;
        if (moving >= 0) {
            switch (code) {
                case KeyEvent.KEYCODE_DPAD_LEFT:
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    if (down) moveBy(code == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : 1);
                    return true;
                case KeyEvent.KEYCODE_DPAD_UP:
                case KeyEvent.KEYCODE_DPAD_DOWN:
                    return true;
                case KeyEvent.KEYCODE_DPAD_CENTER:
                case KeyEvent.KEYCODE_ENTER:
                case KeyEvent.KEYCODE_BACK:
                    if (!down) endMove();
                    return true;
                default:
                    break;
            }
        }
        if (code == KeyEvent.KEYCODE_MENU && menu == null) {
            if (!down) homeMenu();
            return true;
        }
        // Between the Top Row and the grid, Up and Down are explicit: the nearest-view search would
        // skip the grid when the focused column has no tile under it.
        if (down && menu == null) {
            View f = getCurrentFocus();
            if (code == KeyEvent.KEYCODE_DPAD_UP) {
                int i = gridTiles.indexOf(f);
                if (i >= 0 && i < COLUMNS) {
                    focusDock(lastDock);
                    return true;
                }
            } else if (code == KeyEvent.KEYCODE_DPAD_DOWN) {
                int i = dockTiles.indexOf(f);
                if (i >= 0) {
                    if (!gridTiles.isEmpty()) focusGrid(Math.min(i, Math.min(COLUMNS, gridTiles.size()) - 1));
                    return true;
                }
            }
        }
        if (code == KeyEvent.KEYCODE_BACK) {
            if (!down) {
                if (menu != null) cancelMenu();
                else if (inGrid) focusDock(lastDock);
            }
            return true;   // a launcher never finishes on Back
        }
        return super.dispatchKeyEvent(e);
    }
}
