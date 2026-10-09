package app.aptelly.tv.install;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/** HTTPS-only transport. Partial files never become install candidates. */
public final class PackageDownload {
    private PackageDownload() {}

    public static File fetch(File folder, String name, String address, String userAgent,
                             long expectedSize, DownloadBudget budget) throws IOException {
        HttpURLConnection connection = null;
        File partial = new File(folder, name + ".part");
        File target = new File(folder, name + ".apk");
        try {
            URL current = new URL(address);
            for (int redirects = 0; ; redirects++) {
                requireHttps(current);
                connection = (HttpURLConnection) current.openConnection();
                connection.setConnectTimeout(budget.timeoutMillis(20_000));
                connection.setReadTimeout(budget.timeoutMillis(30_000));
                connection.setInstanceFollowRedirects(false);
                connection.setRequestProperty("User-Agent", userAgent);
                connection.setRequestProperty("Accept-Encoding", "identity");
                int status = connection.getResponseCode();
                if (status == 301 || status == 302 || status == 303 || status == 307 || status == 308) {
                    if (redirects >= 3) throw new IOException("Too many download redirects");
                    String location = connection.getHeaderField("Location");
                    if (location == null || location.trim().isEmpty()) throw new IOException("Missing redirect");
                    current = new URL(current, location);
                    requireHttps(current);
                    connection.disconnect();
                    connection = null;
                    continue;
                }
                if (status != 200) throw new IOException("APK HTTP " + status);
                budget.checkLength(expectedSize, connection.getContentLengthLong());
                final HttpURLConnection active = connection;
                try (InputStream input = connection.getInputStream();
                     FileOutputStream output = new FileOutputStream(partial)) {
                    // A finite read timeout also bounds cancellation while a server stops sending.
                    budget.copy(input, output, expectedSize, folder::getUsableSpace,
                            () -> active.setReadTimeout(budget.timeoutMillis(30_000)));
                    output.getFD().sync();
                }
                budget.timeoutMillis(1);
                replace(partial, target);
                return target;
            }
        } finally {
            if (connection != null) connection.disconnect();
            if (partial.exists()) partial.delete();
        }
    }

    static void requireHttps(URL url) throws IOException {
        if (!"https".equalsIgnoreCase(url.getProtocol()) || url.getHost().isEmpty()
                || url.getUserInfo() != null) throw new IOException("Download URL must use HTTPS");
    }

    public static void replace(File partial, File target) throws IOException {
        if (target.exists() && !target.delete()) throw new IOException("Cannot replace old download");
        if (!partial.renameTo(target)) throw new IOException("Cannot finalize download");
    }
}
