package app.aptelly.tv.catalog;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import app.aptelly.tv.BuildConfig;
import app.aptelly.tv.device.DeviceProfilePayload;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public final class CatalogAvailabilityClient {
    private CatalogAvailabilityClient() {
    }

    public static void refresh(Context context, Runnable completed) {
        Context appContext = context.getApplicationContext();
        new Thread(() -> {
            boolean changed = false;
            try {
                JSONObject request = new JSONObject();
                request.put("device", DeviceProfilePayload.collect(appContext));
                HttpURLConnection connection = (HttpURLConnection) new URL(
                        BuildConfig.MATCH_API_BASE_URL + "/v1/catalog/availability"
                ).openConnection();
                connection.setRequestMethod("POST");
                connection.setConnectTimeout(8000);
                connection.setReadTimeout(12000);
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                try (OutputStream output = connection.getOutputStream()) {
                    output.write(request.toString().getBytes(StandardCharsets.UTF_8));
                }
                int status = connection.getResponseCode();
                InputStream stream = status >= 200 && status < 300
                        ? connection.getInputStream()
                        : connection.getErrorStream();
                StringBuilder body = new StringBuilder();
                if (stream != null) {
                    try (BufferedReader reader = new BufferedReader(
                            new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                        String line;
                        while ((line = reader.readLine()) != null) body.append(line);
                    }
                }
                if (status >= 200 && status < 300) {
                    changed = CatalogAvailability.apply(new JSONObject(body.toString()));
                }
                connection.disconnect();
            } catch (Exception ignored) {
                // The device-specific built-in gate remains active when the cloud is offline.
            }
            if (changed && completed != null) {
                new Handler(Looper.getMainLooper()).post(completed);
            }
        }, "catalog-availability").start();
    }
}
