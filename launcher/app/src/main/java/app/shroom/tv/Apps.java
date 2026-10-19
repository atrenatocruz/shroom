package app.shroom.tv;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** The installed TV apps and the user's Favorites, kept on the TV in SharedPreferences. */
final class Apps {

    static final class Entry {
        final String label;
        final String pkg;
        final ComponentName component;
        final Drawable banner;
        final Drawable icon;
        /** Your own picture for this app (assets/icons), drawn whole on a card of its corner colour. */
        final Bitmap custom;
        final int customBg;
        final int color;

        Entry(String label, ComponentName component, Drawable banner, Drawable icon, Bitmap custom) {
            this.label = label;
            this.pkg = component.getPackageName();
            this.component = component;
            this.banner = banner;
            this.icon = icon;
            this.custom = custom;
            this.customBg = custom != null ? custom.getPixel(2, 2) : 0;
            this.color = custom != null ? Ui.deepen(customBg) : Ui.signatureColor(banner != null ? banner : icon);
        }
    }

    /** First-run Favorites, in this order, when they are installed. */
    private static final List<String> SUGGESTED = Arrays.asList(
            "com.netflix.ninja",
            "com.google.android.youtube.tv",
            "com.amazon.firetv.youtube",
            "com.apple.atve.androidtv.appletv",
            "com.disney.disneyplus",
            "com.amazon.amazonvideo.livingroom",
            "com.wbd.stream",
            "com.hbo.hbonow",
            "com.spotify.tv.android",
            "com.plexapp.android",
            "org.xbmc.kodi");
    private static final int FIRST_RUN_COUNT = 5;
    /** Always offered in the Top Row on first run, after the streaming apps. */
    private static final String PLAY_STORE = "com.android.vending";
    /** Hidden until you choose otherwise: system stubs, operator demos and shortcuts nobody opens. */
    private static final List<String> HIDDEN_BY_DEFAULT = Arrays.asList(
            "com.amazon.spiderpork",
            "com.amazon.tv.earlyaccess",
            "com.amazon.smarthomemapviewapp",
            "com.mediatek.opapp",                // satellite operator portals
            "com.vewd.core.opapp",
            "com.vewd.core.fvpplayer");
    static final String DEFAULT_NAMES = "Raquel & Renato";

    private final Context ctx;
    private final SharedPreferences prefs;
    final List<Entry> all = new ArrayList<>();
    private final Map<String, Entry> byPkg = new HashMap<>();

    Apps(Context ctx) {
        this.ctx = ctx;
        this.prefs = ctx.getSharedPreferences("shroom", Context.MODE_PRIVATE);
    }

    void reload() {
        List<String> hidden = hiddenPkgs();
        PackageManager pm = ctx.getPackageManager();
        Intent probe = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        all.clear();
        byPkg.clear();
        for (ResolveInfo ri : pm.queryIntentActivities(probe, 0)) {
            if (ri.activityInfo == null) continue;
            String pkg = ri.activityInfo.packageName;
            if (pkg.equals(ctx.getPackageName()) || byPkg.containsKey(pkg) || hidden.contains(pkg)) continue;
            Drawable banner = ri.activityInfo.loadBanner(pm);
            if (banner == null) banner = ri.activityInfo.applicationInfo.loadBanner(pm);
            String label = customLabels().getProperty(pkg, String.valueOf(ri.loadLabel(pm)));
            Entry e = new Entry(label, new ComponentName(pkg, ri.activityInfo.name), banner, ri.loadIcon(pm),
                    customIcon(pkg));
            all.add(e);
            byPkg.put(pkg, e);
        }
        Collections.sort(all, (a, b) -> a.label.compareToIgnoreCase(b.label));
        if (!prefs.contains("favorites")) seedFavorites();
        if (!prefs.getBoolean("fix_prime_video", false)) showPrimeVideo();
    }

    /**
     * Fire TV's Prime Video is com.amazon.firebat, which an early build hid as "Luna". One time: unhide it
     * and put it in the Top Row.
     */
    private void showPrimeVideo() {
        final String prime = "com.amazon.firebat";
        prefs.edit().putBoolean("fix_prime_video", true).apply();
        String raw = prefs.getString("hidden", null);
        if (raw != null && Arrays.asList(raw.split(",")).contains(prime)) {
            List<String> h = new ArrayList<>(Arrays.asList(raw.split(",")));
            h.remove(prime);
            prefs.edit().putString("hidden", TextUtils.join(",", h)).apply();
        }
        List<String> f = favoritePkgs();
        if (!f.contains(prime)) {
            f.add(prime);   // ignored by favorites() while it isn't installed
            saveFavorites(f);
        }
        reload();   // it may have been filtered out of this pass
    }

    private void seedFavorites() {
        List<String> picks = new ArrayList<>();
        for (String p : SUGGESTED) if (byPkg.containsKey(p) && picks.size() < FIRST_RUN_COUNT) picks.add(p);
        for (Entry e : all) {
            if (picks.size() < FIRST_RUN_COUNT && !picks.contains(e.pkg) && !e.pkg.equals(PLAY_STORE)) picks.add(e.pkg);
        }
        if (byPkg.containsKey(PLAY_STORE)) picks.add(PLAY_STORE);
        saveFavorites(picks);
    }

    /** Favourite package names in dock order, including ones not installed right now. */
    List<String> favoritePkgs() {
        String raw = prefs.getString("favorites", "");
        List<String> out = new ArrayList<>();
        if (raw.isEmpty()) return out;
        for (String p : raw.split(",")) if (!p.isEmpty() && !out.contains(p)) out.add(p);
        return out;
    }

    /** Favourites that are installed, in dock order. */
    List<Entry> favorites() {
        List<Entry> out = new ArrayList<>();
        for (String p : favoritePkgs()) {
            Entry e = byPkg.get(p);
            if (e != null) out.add(e);
        }
        return out;
    }

    boolean isFavorite(String pkg) {
        return favoritePkgs().contains(pkg);
    }

    void saveFavorites(List<String> pkgs) {
        prefs.edit().putString("favorites", TextUtils.join(",", pkgs)).apply();
    }

    private java.util.Properties labels;

    /** Renamed apps, from assets/icons/labels.txt (lines like {@code ar.tvplayer.tv=TV}). */
    private java.util.Properties customLabels() {
        if (labels == null) {
            labels = new java.util.Properties();
            try (java.io.InputStream in = ctx.getAssets().open("icons/labels.txt")) {
                labels.load(new java.io.InputStreamReader(in, "UTF-8"));
            } catch (java.io.IOException ignored) {
                // no renames
            }
        }
        return labels;
    }

    private Bitmap customIcon(String pkg) {
        try (java.io.InputStream in = ctx.getAssets().open("icons/" + pkg + ".jpg")) {
            return BitmapFactory.decodeStream(in);
        } catch (java.io.IOException e) {
            return null;
        }
    }

    List<String> hiddenPkgs() {
        String raw = prefs.getString("hidden", null);
        List<String> out = new ArrayList<>();
        if (raw == null) {
            out.addAll(HIDDEN_BY_DEFAULT);
            return out;
        }
        for (String p : raw.split(",")) if (!p.isEmpty()) out.add(p);
        return out;
    }

    void hide(String pkg) {
        List<String> h = hiddenPkgs();
        if (!h.contains(pkg)) h.add(pkg);
        prefs.edit().putString("hidden", TextUtils.join(",", h)).apply();
        List<String> f = favoritePkgs();
        if (f.remove(pkg)) saveFavorites(f);
    }

    /** Brings back everything you hid; the built-in hidden list stays hidden. */
    void unhideAll() {
        prefs.edit().putString("hidden", TextUtils.join(",", HIDDEN_BY_DEFAULT)).apply();
    }

    void toggleFavorite(String pkg) {
        List<String> f = favoritePkgs();
        if (!f.remove(pkg)) f.add(pkg);
        saveFavorites(f);
    }

    /** Swaps the installed favourite at {@code from} with its neighbour {@code from + dir}. */
    boolean moveFavorite(int from, int dir) {
        List<Entry> shown = favorites();
        int to = from + dir;
        if (from < 0 || to < 0 || from >= shown.size() || to >= shown.size()) return false;
        List<String> f = favoritePkgs();
        int a = f.indexOf(shown.get(from).pkg), b = f.indexOf(shown.get(to).pkg);
        Collections.swap(f, a, b);
        saveFavorites(f);
        return true;
    }

    /** Who the greeting is for; {@code am start -n app.shroom.tv/.MainActivity --es names "..."}. */
    String names() {
        return prefs.getString("names", DEFAULT_NAMES);
    }

    /** Weather location by city name; empty means home, Charneca da Caparica. */
    String city() {
        return prefs.getString("city", "");
    }

    void setText(String key, String value) {
        prefs.edit().putString(key, value == null ? "" : value.trim()).apply();
    }

    SharedPreferences prefs() {
        return prefs;
    }
}
