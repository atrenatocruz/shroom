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
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final int HOME = 0, APPS = 1, SETTINGS = 2;
    private static final int GRID_COLUMNS = 5;
    private static final float TILE_W = 156, TILE_H = 88, GAP = 18, MARGIN = 48;

    private Ui ui;
    private Apps apps;
    private final Handler main = new Handler(Looper.getMainLooper());

    private FrameLayout root, content;
    private Backdrop backdrop;
    private TextView clock, eyebrow, title, dateLine, hint;
    private final TextView[] tabs = new TextView[3];
    private final ViewGroup[] pages = new ViewGroup[3];
    private LinearLayout favRow, appGrid;
    private HorizontalScrollView favScroll;
    private Tile[] settingsTiles;
    private int page = HOME;
    private int lastFav, lastApp, lastSetting;
    private int moving = -1;
    private FrameLayout menu;
    private int wallRes;

    // ---------------------------------------------------------------- lifecycle

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        ui = new Ui(this);
        apps = new Apps(this);
        readExtras(getIntent());
        buildUi();
        apps.reload();
        buildFavorites();
        buildAppGrid();
        showPage(HOME, false);
        focusFavorite(0);

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
    }

    @Override
    protected void onPause() {
        super.onPause();
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

    /** Pressing Home while already home: back to the Home page, first favourite. */
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        readExtras(intent);
        refreshTime();
        closeMenu();
        endMove();
        showPage(HOME, true);
        focusFavorite(0);
    }

    /** Nothing focused (first start, or the focused view went away): land on the current page. */
    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus && getCurrentFocus() == null && menu == null) focusPage(page);
    }

    /** {@code am start -n app.shroom.tv/.MainActivity --es name Renato} personalises the greeting. */
    private void readExtras(Intent i) {
        if (i != null && i.hasExtra("name")) apps.setName(i.getStringExtra("name"));
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
        buildFavorites();
        buildAppGrid();
    };

    // ---------------------------------------------------------------- layout

    private void buildUi() {
        root = new FrameLayout(this);
        root.setClipChildren(false);
        backdrop = new Backdrop(this);
        root.addView(backdrop, new FrameLayout.LayoutParams(-1, -1));

        content = new FrameLayout(this);
        content.setClipChildren(false);
        root.addView(content, new FrameLayout.LayoutParams(-1, -1));

        View topBar = buildTopBar();   // first: the pages point their Up focus at its tabs
        pages[HOME] = buildHomePage();
        pages[APPS] = buildAppsPage();
        pages[SETTINGS] = buildSettingsPage();
        for (ViewGroup p : pages) content.addView(p, new FrameLayout.LayoutParams(-1, -1));
        content.addView(topBar, new FrameLayout.LayoutParams(-1, ui.u(84), Gravity.TOP));

        setContentView(root);
    }

    private View buildTopBar() {
        FrameLayout bar = new FrameLayout(this);
        bar.setClipChildren(false);

        LinearLayout brand = new LinearLayout(this);
        brand.setGravity(Gravity.CENTER_VERTICAL);
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.ic_logo);
        brand.addView(logo, new LinearLayout.LayoutParams(ui.u(22), ui.u(22)));
        TextView name = ui.text(20, 600, Color.WHITE);
        name.setText("shroom");
        name.setLetterSpacing(-0.01f);
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(-2, -2);
        nlp.leftMargin = ui.u(8);
        brand.addView(name, nlp);
        FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(-2, -2, Gravity.START | Gravity.CENTER_VERTICAL);
        blp.leftMargin = ui.u(MARGIN);
        bar.addView(brand, blp);

        LinearLayout nav = new LinearLayout(this);
        nav.setGravity(Gravity.CENTER_VERTICAL);
        nav.setPadding(ui.u(4), ui.u(4), ui.u(4), ui.u(4));
        nav.setBackground(ui.round(0x33FFFFFF, 22));
        int[] labels = {R.string.tab_home, R.string.tab_apps, R.string.tab_settings};
        for (int i = 0; i < 3; i++) {
            final int idx = i;
            TextView t = ui.text(15, 500, Color.WHITE);
            t.setText(labels[i]);
            t.setGravity(Gravity.CENTER);
            t.setPadding(ui.u(20), 0, ui.u(20), 0);
            t.setFocusable(true);
            t.setId(View.generateViewId());
            t.setOnFocusChangeListener((v, f) -> {
                if (f && page != idx) showPage(idx, true);
                styleTabs();
            });
            t.setOnKeyListener((v, code, ev) -> {
                if (ev.getAction() != KeyEvent.ACTION_DOWN) return false;
                if (code == KeyEvent.KEYCODE_DPAD_DOWN || code == KeyEvent.KEYCODE_DPAD_CENTER
                        || code == KeyEvent.KEYCODE_ENTER) {
                    focusPage(idx);
                    return true;
                }
                if (code == KeyEvent.KEYCODE_DPAD_UP) return true;
                if (code == KeyEvent.KEYCODE_DPAD_LEFT && idx == 0) return true;
                return code == KeyEvent.KEYCODE_DPAD_RIGHT && idx == 2;
            });
            tabs[i] = t;
            nav.addView(t, new LinearLayout.LayoutParams(-2, ui.u(32)));
        }
        bar.addView(nav, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));

        clock = ui.text(24, 350, Color.WHITE);
        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(-2, -2, Gravity.END | Gravity.CENTER_VERTICAL);
        clp.rightMargin = ui.u(MARGIN);
        bar.addView(clock, clp);
        return bar;
    }

    private ViewGroup buildHomePage() {
        FrameLayout p = new FrameLayout(this);
        p.setClipChildren(false);

        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        eyebrow = ui.text(11, 600, 0xE6FFFFFF);
        eyebrow.setLetterSpacing(0.28f);
        title = ui.text(54, 600, Color.WHITE);
        title.setLetterSpacing(-0.02f);
        dateLine = ui.text(17, 400, 0xF2FFFFFF);
        hero.addView(eyebrow);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(-2, -2);
        tlp.topMargin = ui.u(14);
        hero.addView(title, tlp);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(-2, -2);
        dlp.topMargin = ui.u(14);
        hero.addView(dateLine, dlp);
        FrameLayout.LayoutParams hlp = new FrameLayout.LayoutParams(-2, -2);
        hlp.leftMargin = ui.u(MARGIN + 2);
        hlp.topMargin = ui.u(150);
        p.addView(hero, hlp);

        TextView favLabel = ui.text(14, 600, Color.WHITE);
        favLabel.setText(R.string.favorites);
        FrameLayout.LayoutParams flp = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.START);
        flp.leftMargin = ui.u(MARGIN + 2);
        flp.bottomMargin = ui.u(172);
        p.addView(favLabel, flp);

        hint = ui.text(11, 450, 0xB3FFFFFF);
        hint.setText(R.string.hint_hold);
        FrameLayout.LayoutParams hl = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.END);
        hl.rightMargin = ui.u(MARGIN);
        hl.bottomMargin = ui.u(172);
        p.addView(hint, hl);

        favScroll = new HorizontalScrollView(this);
        favScroll.setHorizontalScrollBarEnabled(false);
        favScroll.setClipChildren(false);
        favScroll.setClipToPadding(false);
        favScroll.setPadding(ui.u(MARGIN), ui.u(16), ui.u(MARGIN), ui.u(16));
        favScroll.setFillViewport(false);
        favRow = new LinearLayout(this);
        favRow.setClipChildren(false);
        favRow.setClipToPadding(false);
        favScroll.addView(favRow, new FrameLayout.LayoutParams(-2, -2));
        FrameLayout.LayoutParams slp = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        slp.bottomMargin = ui.u(30);
        p.addView(favScroll, slp);
        return p;
    }

    private ViewGroup buildAppsPage() {
        ScrollView s = new ScrollView(this);
        s.setVerticalScrollBarEnabled(false);
        s.setClipChildren(false);
        s.setClipToPadding(false);
        s.setVerticalFadingEdgeEnabled(true);
        s.setFadingEdgeLength(ui.u(40));
        s.setPadding(0, ui.u(84), 0, 0);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setClipChildren(false);
        col.setClipToPadding(false);
        col.setPadding(ui.u(MARGIN), ui.u(20), ui.u(MARGIN), ui.u(40));
        TextView h = ui.text(28, 600, Color.WHITE);
        h.setText(R.string.all_apps);
        col.addView(h);
        appGrid = new LinearLayout(this);
        appGrid.setOrientation(LinearLayout.VERTICAL);
        appGrid.setClipChildren(false);
        appGrid.setClipToPadding(false);
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(-1, -2);
        glp.topMargin = ui.u(22);
        col.addView(appGrid, glp);
        s.addView(col);
        return s;
    }

    private ViewGroup buildSettingsPage() {
        FrameLayout p = new FrameLayout(this);
        p.setClipChildren(false);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setClipChildren(false);
        TextView h = ui.text(28, 600, Color.WHITE);
        h.setText(R.string.tab_settings);
        col.addView(h);

        LinearLayout row = new LinearLayout(this);
        row.setClipChildren(false);
        float w = 128, hh = 92;
        List<Tile> t = new ArrayList<>();
        if (hasInputs()) t.add(action(R.drawable.ic_input, R.string.inputs, w, hh, this::showInputs));
        t.add(action(R.drawable.ic_wifi, R.string.network, w, hh, () -> openSettings(Settings.ACTION_WIFI_SETTINGS)));
        t.add(action(R.drawable.ic_settings, R.string.tv_settings, w, hh, () -> openSettings(Settings.ACTION_SETTINGS)));
        t.add(action(R.drawable.ic_apps, R.string.manage_apps, w, hh, () -> openSettings(Settings.ACTION_APPLICATION_SETTINGS)));
        t.add(action(R.drawable.ic_moon, R.string.screensaver, w, hh, () -> openSettings(Settings.ACTION_DREAM_SETTINGS)));
        t.add(action(R.drawable.ic_info, R.string.about, w, hh, this::showAbout));
        settingsTiles = t.toArray(new Tile[0]);
        for (int i = 0; i < settingsTiles.length; i++) {
            final int idx = i;
            Tile tile = settingsTiles[i];
            tile.setId(View.generateViewId());
            tile.setOnFocusChangeListener((v, f) -> {
                if (f) lastSetting = idx;
            });
            tile.setNextFocusDownId(tile.getId());
            tile.setNextFocusUpId(tabs[SETTINGS].getId());
            if (i == 0) tile.setNextFocusLeftId(tile.getId());
            if (i == settingsTiles.length - 1) tile.setNextFocusRightId(tile.getId());
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            if (i > 0) lp.leftMargin = ui.u(16);
            row.addView(tile, lp);
        }
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(-2, -2);
        rlp.topMargin = ui.u(22);
        col.addView(row, rlp);

        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(-2, -2);
        clp.leftMargin = ui.u(MARGIN);
        clp.topMargin = ui.u(84 + 20);   // same spot as the Apps heading
        p.addView(col, clp);
        return p;
    }

    private Tile action(int icon, int text, float w, float h, Runnable r) {
        Tile t = Tile.forAction(ui, icon, getString(text), w, h);
        t.setOnClickListener(v -> r.run());
        return t;
    }

    // ---------------------------------------------------------------- tiles

    private void buildFavorites() {
        int focused = focusedIndex(favRow);
        favRow.removeAllViews();
        List<Apps.Entry> favs = apps.favorites();
        if (favs.isEmpty()) {
            TextView empty = ui.text(14, 400, 0xB3FFFFFF);
            empty.setText(R.string.no_favorites);
            favRow.addView(empty, new LinearLayout.LayoutParams(-2, ui.u(TILE_H)));
            hint.setVisibility(View.INVISIBLE);
            return;
        }
        hint.setVisibility(View.VISIBLE);
        for (int i = 0; i < favs.size(); i++) {
            final int idx = i;
            final Apps.Entry e = favs.get(i);
            Tile t = Tile.forApp(ui, e, TILE_W, TILE_H);
            t.setId(View.generateViewId());
            t.setNextFocusUpId(tabs[HOME].getId());
            t.setNextFocusDownId(t.getId());
            t.setOnFocusChangeListener((v, f) -> {
                if (!f) return;
                lastFav = idx;
                backdrop.setGlow(e.color);
            });
            t.setOnClickListener(v -> launch(e, v));
            t.setOnLongClickListener(v -> {
                favoriteMenu(idx, e);
                return true;
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            if (i > 0) lp.leftMargin = ui.u(GAP);
            favRow.addView(t, lp);
        }
        // Left of the first tile and right of the last stay put instead of jumping to the top bar.
        View first = favRow.getChildAt(0), last = favRow.getChildAt(favRow.getChildCount() - 1);
        first.setNextFocusLeftId(first.getId());
        last.setNextFocusRightId(last.getId());
        if (focused >= 0) focusFavorite(Math.min(focused, favs.size() - 1));
    }

    private void buildAppGrid() {
        int focused = -1;
        View f = getCurrentFocus();
        if (f instanceof Tile && isDescendant(appGrid, f)) focused = lastApp;
        appGrid.removeAllViews();
        LinearLayout row = null;
        for (int i = 0; i < apps.all.size(); i++) {
            final int idx = i;
            final Apps.Entry e = apps.all.get(i);
            if (i % GRID_COLUMNS == 0) {
                row = new LinearLayout(this);
                row.setClipChildren(false);
                row.setClipToPadding(false);
                LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(-1, -2);
                if (i > 0) rlp.topMargin = ui.u(20);
                appGrid.addView(row, rlp);
            }
            Tile t = Tile.forApp(ui, e, TILE_W, TILE_H);
            if (i < GRID_COLUMNS) t.setNextFocusUpId(tabs[APPS].getId());
            t.setOnFocusChangeListener((v, gained) -> {
                if (gained) lastApp = idx;
            });
            t.setOnClickListener(v -> launch(e, v));
            t.setOnLongClickListener(v -> {
                appMenu(e);
                return true;
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            if (i % GRID_COLUMNS > 0) lp.leftMargin = ui.u(GAP);
            row.addView(t, lp);
        }
        if (focused >= 0) focusApp(Math.min(focused, apps.all.size() - 1));
    }

    private static boolean isDescendant(ViewGroup parent, View v) {
        for (View p = v; p != null; p = p.getParent() instanceof View ? (View) p.getParent() : null) {
            if (p == parent) return true;
        }
        return false;
    }

    private int focusedIndex(LinearLayout row) {
        View f = getCurrentFocus();
        for (int i = 0; i < row.getChildCount(); i++) if (row.getChildAt(i) == f) return i;
        return -1;
    }

    private void focusFavorite(int i) {
        if (favRow.getChildCount() == 0 || !(favRow.getChildAt(0) instanceof Tile)) {
            tabs[HOME].requestFocusFromTouch();
            return;
        }
        favRow.getChildAt(Math.max(0, Math.min(i, favRow.getChildCount() - 1))).requestFocusFromTouch();
    }

    private void focusApp(int i) {
        if (apps.all.isEmpty()) {
            tabs[APPS].requestFocusFromTouch();
            return;
        }
        i = Math.max(0, Math.min(i, apps.all.size() - 1));
        ViewGroup row = (ViewGroup) appGrid.getChildAt(i / GRID_COLUMNS);
        row.getChildAt(i % GRID_COLUMNS).requestFocusFromTouch();
    }

    private void focusPage(int p) {
        if (p == HOME) focusFavorite(lastFav);
        else if (p == APPS) focusApp(lastApp);
        else settingsTiles[Math.min(lastSetting, settingsTiles.length - 1)].requestFocusFromTouch();
    }

    // ---------------------------------------------------------------- pages

    private void showPage(int p, boolean animate) {
        page = p;
        for (int i = 0; i < 3; i++) {
            ViewGroup v = pages[i];
            boolean on = i == p;
            v.setDescendantFocusability(on ? ViewGroup.FOCUS_AFTER_DESCENDANTS : ViewGroup.FOCUS_BLOCK_DESCENDANTS);
            v.animate().cancel();
            if (!animate) {
                v.setVisibility(on ? View.VISIBLE : View.GONE);
                v.setAlpha(1f);
                v.setTranslationY(0);
                continue;
            }
            if (on) {
                v.setVisibility(View.VISIBLE);
                v.setAlpha(0f);
                v.setTranslationY(ui.u(14));
                v.animate().alpha(1f).translationY(0).setDuration(280)
                        .setInterpolator(new DecelerateInterpolator(1.6f)).start();
            } else if (v.getVisibility() == View.VISIBLE) {
                v.animate().alpha(0f).setDuration(160).withEndAction(() -> v.setVisibility(View.GONE)).start();
            }
        }
        backdrop.setDim(p == HOME ? 0f : 0.62f);
        if (p != HOME) backdrop.setGlow(0xFF1A1C24);
        styleTabs();
    }

    private void styleTabs() {
        for (int i = 0; i < 3; i++) {
            TextView t = tabs[i];
            boolean focused = t.isFocused(), selected = i == page;
            t.setBackground(focused ? ui.round(Color.WHITE, 16) : selected ? ui.round(0x40FFFFFF, 16) : null);
            t.setTextColor(focused ? 0xFF111114 : selected ? Color.WHITE : 0xD9FFFFFF);
            t.setTypeface(ui.font(focused || selected ? 600 : 450));
        }
    }

    // ---------------------------------------------------------------- time & sky

    private void refreshTime() {
        Calendar now = Calendar.getInstance();
        int h = now.get(Calendar.HOUR_OF_DAY);
        clock.setText(DateFormat.format(DateFormat.is24HourFormat(this) ? "H:mm" : "h:mm", now));
        int greet = h >= 5 && h < 12 ? R.string.greet_morning
                : h >= 12 && h < 18 ? R.string.greet_afternoon
                : h >= 18 && h < 22 ? R.string.greet_evening : R.string.greet_night;
        eyebrow.setText(greet);
        String name = apps.name();
        title.setText(name.isEmpty() ? getString(R.string.welcome) : getString(R.string.welcome_named, name));
        dateLine.setText(DateFormat.format(DateFormat.getBestDateTimePattern(Locale.getDefault(), "EEEEMMMMd"), now));

        int res = h >= 5 && h < 11 ? R.drawable.sky_dawn
                : h >= 11 && h < 17 ? R.drawable.sky_day
                : h >= 17 && h < 21 ? R.drawable.sky_dusk : R.drawable.sky_night;
        if (res != wallRes) {
            wallRes = res;
            new Thread(() -> {
                BitmapFactory.Options o = new BitmapFactory.Options();
                o.inPreferredConfig = Bitmap.Config.RGB_565;   // half the memory; grain in the image hides banding
                Bitmap b = BitmapFactory.decodeResource(getResources(), res, o);
                main.post(() -> backdrop.setWallpaper(b));
            }, "sky").start();
        }
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

    private void openSettings(String action) {
        open(new Intent(action));
    }

    private void open(Intent i) {
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(i);
        } catch (ActivityNotFoundException | SecurityException e) {
            if (!Settings.ACTION_SETTINGS.equals(i.getAction())) {
                // Many TVs ship a trimmed Settings app: fall back to its main page.
                try {
                    startActivity(new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
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

    private void favoriteMenu(int idx, Apps.Entry e) {
        showMenu(e.label, null,
                new String[]{getString(R.string.move), getString(R.string.remove_favorite), getString(R.string.app_info)},
                new Runnable[]{() -> startMove(idx), () -> {
                    apps.toggleFavorite(e.pkg);
                    buildFavorites();
                    focusFavorite(idx);
                }, () -> openAppInfo(e.pkg)});
    }

    private void appMenu(Apps.Entry e) {
        boolean fav = apps.isFavorite(e.pkg);
        showMenu(e.label, null,
                new String[]{getString(fav ? R.string.remove_favorite : R.string.add_favorite), getString(R.string.app_info)},
                new Runnable[]{() -> {
                    apps.toggleFavorite(e.pkg);
                    buildFavorites();
                    focusApp(lastApp);
                }, () -> openAppInfo(e.pkg)});
    }

    private void showAbout() {
        String version = "";
        try {
            version = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception ignored) {
        }
        showMenu(getString(R.string.about), getString(R.string.about_body, version),
                new String[]{getString(R.string.home_picker)},
                new Runnable[]{() -> openSettings(Settings.ACTION_HOME_SETTINGS)});
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
        return getPackageManager().resolveActivity(new Intent("com.android.tv.action.VIEW_INPUTS"), 0) != null;
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
        Intent panel = new Intent("com.android.tv.action.VIEW_INPUTS");
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

    private void showMenu(String heading, String body, String[] labels, Runnable[] actions) {
        closeMenu();
        View returnTo = getCurrentFocus();
        menu = new FrameLayout(this);
        menu.setBackgroundColor(0x99000000);
        menu.setClickable(true);

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackground(ui.round(0xF21C1D22, 20));
        panel.setPadding(ui.u(22), ui.u(22), ui.u(22), ui.u(18));
        panel.setElevation(ui.u(24));
        TextView h = ui.text(20, 600, Color.WHITE);
        h.setText(heading);
        panel.addView(h);
        if (body != null) {
            TextView b = ui.text(13, 400, 0xB3FFFFFF);
            b.setSingleLine(false);
            b.setLineSpacing(ui.u(4), 1f);
            b.setText(body);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.topMargin = ui.u(10);
            panel.addView(b, lp);
        }
        View first = null;
        for (int i = 0; i < labels.length; i++) {
            final Runnable r = actions[i];
            TextView btn = ui.text(15, 500, Color.WHITE);
            btn.setText(labels[i]);
            btn.setGravity(Gravity.CENTER_VERTICAL);
            btn.setPadding(ui.u(14), 0, ui.u(14), 0);
            btn.setFocusable(true);
            btn.setOnFocusChangeListener((v, f) -> {
                v.setBackground(f ? ui.round(Color.WHITE, 10) : null);
                ((TextView) v).setTextColor(f ? 0xFF111114 : Color.WHITE);
            });
            btn.setOnClickListener(v -> {
                closeMenu();
                r.run();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, ui.u(40));
            lp.topMargin = ui.u(i == 0 ? 16 : 4);
            panel.addView(btn, lp);
            if (first == null) first = btn;
        }
        menu.addView(panel, new FrameLayout.LayoutParams(ui.u(330), -2, Gravity.CENTER));
        menu.setTag(returnTo);
        content.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        root.addView(menu, new FrameLayout.LayoutParams(-1, -1));
        menu.setAlpha(0f);
        panel.setScaleX(0.94f);
        panel.setScaleY(0.94f);
        menu.animate().alpha(1f).setDuration(160).start();
        panel.animate().scaleX(1f).scaleY(1f).setDuration(220).setInterpolator(new DecelerateInterpolator()).start();
        if (first != null) first.requestFocus();
    }

    private void closeMenu() {
        if (menu == null) return;
        View returnTo = (View) menu.getTag();
        root.removeView(menu);
        menu = null;
        content.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        if (returnTo != null && returnTo.isAttachedToWindow()) returnTo.requestFocus();
        else focusPage(page);
    }

    // ---------------------------------------------------------------- rearranging

    private void startMove(int idx) {
        moving = idx;
        hint.setText(R.string.hint_move);
        focusFavorite(idx);
        View v = favRow.getChildAt(idx);
        if (v instanceof Tile) ((Tile) v).setMoving(true);
    }

    private void endMove() {
        if (moving < 0) return;
        View v = favRow.getChildAt(moving);
        if (v instanceof Tile) ((Tile) v).setMoving(false);
        moving = -1;
        hint.setText(R.string.hint_hold);
    }

    private void moveBy(int dir) {
        if (!apps.moveFavorite(moving, dir)) return;
        moving += dir;
        buildFavorites();
        focusFavorite(moving);
        ((Tile) favRow.getChildAt(moving)).setMoving(true);
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
        if (code == KeyEvent.KEYCODE_BACK) {
            if (!down) {
                if (menu != null) closeMenu();
                else if (page != HOME) {
                    showPage(HOME, true);
                    focusFavorite(lastFav);
                }
            }
            return true;   // a launcher never finishes on Back
        }
        return super.dispatchKeyEvent(e);
    }
}
