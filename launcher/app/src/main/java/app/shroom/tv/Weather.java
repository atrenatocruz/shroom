package app.shroom.tv;

import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Locale;

/**
 * Current temperature and conditions from Open-Meteo (free, no account, no key). The location is a
 * city you set, or else an approximate one from the TV's public IP (geojs.io), remembered for a day.
 * The only network traffic the launcher makes.
 */
final class Weather {
    interface Callback {
        void onWeather(String text);
    }

    private static final long LOCATION_TTL = 24 * 60 * 60 * 1000L;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    static void fetch(SharedPreferences prefs, String city, Callback cb) {
        new Thread(() -> {
            try {
                double[] at = location(prefs, city);
                boolean fahrenheit = "US".equals(Locale.getDefault().getCountry());
                JSONObject cur = json("https://api.open-meteo.com/v1/forecast?latitude=" + at[0]
                        + "&longitude=" + at[1] + "&current=temperature_2m,weather_code,is_day"
                        + (fahrenheit ? "&temperature_unit=fahrenheit" : "")).getJSONObject("current");
                long t = Math.round(cur.getDouble("temperature_2m"));
                String text = t + "°  " + describe(cur.getInt("weather_code"), cur.optInt("is_day", 1) == 1);
                MAIN.post(() -> cb.onWeather(text));
            } catch (Exception e) {
                MAIN.post(() -> cb.onWeather(null));   // offline: the weather simply isn't shown
            }
        }, "weather").start();
    }

    private static double[] location(SharedPreferences prefs, String city) throws Exception {
        String key = city.isEmpty() ? "@ip" : city;
        if (key.equals(prefs.getString("loc_key", ""))
                && System.currentTimeMillis() - prefs.getLong("loc_time", 0) < LOCATION_TTL) {
            return new double[]{prefs.getFloat("loc_lat", 0), prefs.getFloat("loc_lon", 0)};
        }
        double lat, lon;
        if (city.isEmpty()) {
            JSONObject geo = json("https://get.geojs.io/v1/ip/geo.json");
            lat = Double.parseDouble(geo.getString("latitude"));
            lon = Double.parseDouble(geo.getString("longitude"));
        } else {
            JSONArray r = json("https://geocoding-api.open-meteo.com/v1/search?count=1&name="
                    + URLEncoder.encode(city, "UTF-8")).getJSONArray("results");
            lat = r.getJSONObject(0).getDouble("latitude");
            lon = r.getJSONObject(0).getDouble("longitude");
        }
        prefs.edit().putString("loc_key", key).putLong("loc_time", System.currentTimeMillis())
                .putFloat("loc_lat", (float) lat).putFloat("loc_lon", (float) lon).apply();
        return new double[]{lat, lon};
    }

    private static JSONObject json(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(8000);
        try (InputStream in = c.getInputStream()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
            return new JSONObject(out.toString("UTF-8"));
        } finally {
            c.disconnect();
        }
    }

    /** WMO weather interpretation codes, as Open-Meteo reports them. */
    private static String describe(int code, boolean day) {
        if (code == 0) return day ? "Sunny" : "Clear";
        if (code <= 2) return "Partly Cloudy";
        if (code == 3) return "Cloudy";
        if (code == 45 || code == 48) return "Fog";
        if (code >= 51 && code <= 57) return "Drizzle";
        if (code >= 61 && code <= 67) return "Rain";
        if (code >= 71 && code <= 77) return "Snow";
        if (code >= 80 && code <= 82) return "Showers";
        if (code == 85 || code == 86) return "Snow Showers";
        if (code >= 95) return "Thunderstorm";
        return "";
    }
}
