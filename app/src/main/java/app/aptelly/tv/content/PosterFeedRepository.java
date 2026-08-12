package app.aptelly.tv.content;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.LruCache;

import app.aptelly.tv.BuildConfig;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Loads a small, operator-controlled metadata feed. The feed contains artwork URLs and
 * editorial copy only; Aptelly never downloads or proxies programme video.
 */
public final class PosterFeedRepository {
    public interface FeedListener {
        void onFeedChanged();
    }

    public interface ArtworkListener {
        void onArtwork(Bitmap bitmap);
    }

    private static final String PREFS = "poster_feed";
    private static final String KEY_JSON = "cached_json";
    private static final int MAX_IMAGE_BYTES = 12 * 1024 * 1024;

    private final Context context;
    private final SharedPreferences preferences;
    private final ExecutorService executor = Executors.newFixedThreadPool(2);
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final LruCache<String, Bitmap> memoryCache = new LruCache<>(6);
    private volatile Map<String, PosterScene> remoteScenes = Collections.emptyMap();
    private volatile String attributionName = "";
    private volatile String attributionUrl = "";
    private volatile String attributionNotice = "";
    private volatile boolean closed;

    public PosterFeedRepository(Context context) {
        this.context = context.getApplicationContext();
        preferences = this.context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public void start(FeedListener listener) {
        String cached = preferences.getString(KEY_JSON, "");
        if (!TextUtils.isEmpty(cached)) {
            remoteScenes = parse(cached);
            if (!remoteScenes.isEmpty() && !closed) {
                mainHandler.post(listener::onFeedChanged);
            }
        }
    }

    public PosterScene scene(
            String category,
            String fallbackTitle,
            String fallbackSummary
    ) {
        PosterScene remote = remoteScenes.get(category);
        if (remote != null) {
            return remote;
        }
        int seed = Math.abs(category.hashCode());
        int accent = Color.rgb(
                82 + seed % 88,
                72 + (seed / 7) % 105,
                130 + (seed / 17) % 100
        );
        int deep = Color.rgb(
                8 + seed % 18,
                10 + (seed / 11) % 23,
                24 + (seed / 19) % 33
        );
        return new PosterScene(
                category,
                context.getString(R.string.editorial_eyebrow),
                fallbackTitle,
                fallbackSummary,
                "",
                fallbackTitle,
                "",
                accent,
                deep
        );
    }

    public String attributionName() {
        return attributionName;
    }

    public String attributionUrl() {
        return attributionUrl;
    }

    public String attributionNotice() {
        return attributionNotice;
    }

    public void loadArtwork(PosterScene scene, ArtworkListener listener) {
        loadImage(scene == null ? "" : scene.imageUrl, listener);
    }

    public void loadPoster(PosterScene scene, ArtworkListener listener) {
        loadImage(scene == null ? "" : scene.posterUrl, listener);
    }

    private void loadImage(String address, ArtworkListener listener) {
        if (TextUtils.isEmpty(address)) {
            listener.onArtwork(null);
            return;
        }
        Bitmap memory = memoryCache.get(address);
        if (memory != null && !memory.isRecycled()) {
            listener.onArtwork(memory);
            return;
        }
        executor.execute(() -> {
            Bitmap bitmap = null;
            try {
                File cached = artworkFile(address);
                if (cached.isFile() && cached.length() > 0) {
                    try (FileInputStream input = new FileInputStream(cached)) {
                        bitmap = BitmapFactory.decodeStream(input);
                    }
                }
                if (bitmap == null) {
                    byte[] bytes = downloadBytes(address, MAX_IMAGE_BYTES);
                    bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                    if (bitmap != null) {
                        File temporary = new File(cached.getAbsolutePath() + ".part");
                        try (FileOutputStream output = new FileOutputStream(temporary)) {
                            output.write(bytes);
                        }
                        if (cached.exists()) {
                            cached.delete();
                        }
                        if (!temporary.renameTo(cached)) {
                            temporary.delete();
                        }
                    }
                }
                if (bitmap != null) {
                    memoryCache.put(address, bitmap);
                }
            } catch (Exception ignored) {
                // The generated cinematic background is the permanent offline fallback.
            }
            Bitmap result = bitmap;
            if (!closed) {
                mainHandler.post(() -> listener.onArtwork(result));
            }
        });
    }

    public void close() {
        closed = true;
        mainHandler.removeCallbacksAndMessages(null);
        executor.shutdownNow();
    }

    private Map<String, PosterScene> parse(String json) {
        Map<String, PosterScene> result = new HashMap<>();
        try {
            JSONObject root = new JSONObject(json);
            JSONArray groups = root.optJSONArray("groups");
            if (groups == null) {
                return result;
            }
            for (int index = 0; index < groups.length(); index++) {
                JSONObject item = groups.optJSONObject(index);
                if (item == null) {
                    continue;
                }
                String category = item.optString("category", "");
                String image = item.optString("image", "");
                String poster = item.optString("poster", "");
                if (!category.matches("[a-z_]{2,32}")
                        || (!TextUtils.isEmpty(image) && !isHttps(image))
                        || (!TextUtils.isEmpty(poster) && !isHttps(poster))) {
                    continue;
                }
                result.put(category, new PosterScene(
                        category,
                        item.optString(
                                "eyebrow",
                                context.getString(R.string.trending_eyebrow)
                        ),
                        item.optString("title", ""),
                        item.optString("summary", ""),
                        image,
                        item.optString("posterTitle", item.optString("title", "")),
                        poster,
                        parseColor(item.optString("accent", ""), Color.rgb(115, 92, 220)),
                        parseColor(item.optString("deep", ""), Color.rgb(9, 13, 31))
                ));
            }
            if (!result.isEmpty()) {
                JSONObject attribution = root.optJSONObject("attribution");
                if (attribution != null) {
                    String providerUrl = attribution.optString("url", "");
                    attributionName = attribution.optString("name", "").trim();
                    attributionUrl = isHttps(providerUrl) ? providerUrl : "";
                    attributionNotice = attribution.optString("notice", "").trim();
                }
            }
        } catch (Exception ignored) {
            return Collections.emptyMap();
        }
        return result;
    }

    private byte[] downloadBytes(String address, int maximum) throws Exception {
        if (!isHttps(address)) {
            throw new IllegalArgumentException("HTTPS is required");
        }
        HttpURLConnection connection = (HttpURLConnection) new URL(address).openConnection();
        connection.setConnectTimeout(10_000);
        connection.setReadTimeout(15_000);
        connection.setRequestProperty("User-Agent", "Aptelly/" + BuildConfig.VERSION_NAME);
        connection.setRequestProperty(
                "Accept-Language",
                Locale.getDefault().toLanguageTag()
        );
        connection.setInstanceFollowRedirects(true);
        connection.connect();
        if (connection.getResponseCode() < 200 || connection.getResponseCode() >= 300) {
            connection.disconnect();
            throw new IllegalStateException("HTTP " + connection.getResponseCode());
        }
        URL finalUrl = connection.getURL();
        if (!"https".equalsIgnoreCase(finalUrl.getProtocol())) {
            connection.disconnect();
            throw new IllegalStateException("Insecure redirect");
        }
        try (InputStream input = connection.getInputStream();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[32 * 1024];
            int total = 0;
            while (true) {
                int count = input.read(buffer);
                if (count < 0) {
                    break;
                }
                total += count;
                if (total > maximum) {
                    throw new IllegalStateException("Response is too large");
                }
                output.write(buffer, 0, count);
            }
            return output.toByteArray();
        } finally {
            connection.disconnect();
        }
    }

    private File artworkFile(String address) throws Exception {
        File directory = new File(context.getCacheDir(), "poster_artwork");
        if (!directory.isDirectory() && !directory.mkdirs()) {
            throw new IllegalStateException("Unable to create artwork cache");
        }
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] value = digest.digest(address.getBytes(StandardCharsets.UTF_8));
        StringBuilder name = new StringBuilder();
        for (byte current : value) {
            name.append(String.format("%02x", current));
        }
        return new File(directory, name + ".img");
    }

    private boolean isHttps(String value) {
        try {
            return "https".equalsIgnoreCase(new URL(value).getProtocol());
        } catch (Exception ignored) {
            return false;
        }
    }

    private int parseColor(String value, int fallback) {
        try {
            return Color.parseColor(value);
        } catch (Exception ignored) {
            return fallback;
        }
    }
}
