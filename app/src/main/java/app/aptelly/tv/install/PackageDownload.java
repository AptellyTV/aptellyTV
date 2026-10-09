package app.aptelly.tv.install;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.FileInputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** HTTPS-only transport. Partial files never become install candidates. */
public final class PackageDownload {
    interface Connections { HttpURLConnection open(URL url) throws IOException; }
    private PackageDownload() {}

    public static File fetch(File folder, String name, String address, String userAgent,
                             long expectedSize, DownloadBudget budget) throws IOException {
        return fetch(folder, name, address, userAgent, expectedSize, budget, null);
    }

    public static File fetch(File folder, String name, String address, String userAgent,
                             long expectedSize, DownloadBudget budget, DownloadOperation operation) throws IOException {
        return fetch(folder, name, address, userAgent, expectedSize, "", budget, operation);
    }

    public static File fetch(File folder, String name, String address, String userAgent,
                             long expectedSize, String hash, DownloadBudget budget, DownloadOperation operation) throws IOException {
        return fetch(folder, name, address, userAgent, expectedSize, hash, budget, operation,
                url -> (HttpURLConnection) url.openConnection());
    }

    static File fetch(File folder, String name, String address, String userAgent,
                      long expectedSize, String hash, DownloadBudget budget, DownloadOperation operation,
                      Connections connections) throws IOException {
        HttpURLConnection connection = null;
        File partial = new File(folder, name + ".part");
        File marker = new File(folder, name + ".resume");
        File target = new File(folder, name + ".apk");
        long offset = DownloadCheckpoint.cachedBytes(folder, name, hash, expectedSize);
        boolean preserve = false;
        boolean writingStarted = false;
        try {
            URL current = new URL(address);
            for (int redirects = 0; ; redirects++) {
                if (operation != null) operation.check();
                requireHttps(current);
                connection = connections.open(current);
                if (operation != null) operation.bindConnection(connection);
                connection.setConnectTimeout(budget.timeoutMillis(20_000));
                connection.setReadTimeout(budget.timeoutMillis(30_000));
                connection.setInstanceFollowRedirects(false);
                connection.setRequestProperty("User-Agent", userAgent);
                connection.setRequestProperty("Accept-Encoding", "identity");
                if (offset > 0) connection.setRequestProperty("Range", "bytes=" + offset + "-");
                int status = connection.getResponseCode();
                if (status == 301 || status == 302 || status == 303 || status == 307 || status == 308) {
                    if (redirects >= 3) throw new IOException("Too many download redirects");
                    String location = connection.getHeaderField("Location");
                    if (location == null || location.trim().isEmpty()) throw new IOException("Missing redirect");
                    current = new URL(current, location);
                    requireHttps(current);
                    connection.disconnect();
                    if (operation != null) operation.clearConnection();
                    connection = null;
                    continue;
                }
                if (status == 206 && offset > 0) {
                    DownloadCheckpoint.requireRange(connection.getHeaderField("Content-Range"), offset, expectedSize);
                    budget.checkLength(expectedSize - offset, connection.getContentLengthLong());
                } else if (status == 200) {
                    if (offset > 0) { budget.discardCached(offset); offset = 0; }
                    budget.checkLength(expectedSize, connection.getContentLengthLong());
                } else throw new IOException("APK HTTP " + status);
                DownloadCheckpoint.record(folder, name, hash, expectedSize);
                final HttpURLConnection active = connection;
                writingStarted = true;
                try (InputStream input = connection.getInputStream();
                     FileOutputStream output = new FileOutputStream(partial, offset > 0)) {
                    budget.preflight(folder.getUsableSpace());
                    // A finite read timeout also bounds cancellation while a server stops sending.
                    budget.copy(input, output, expectedSize, offset, folder::getUsableSpace,
                            () -> {
                                if (operation != null) operation.check();
                                active.setReadTimeout(budget.timeoutMillis(30_000));
                            });
                    output.getFD().sync();
                }
                budget.timeoutMillis(1);
                if (operation != null) operation.check();
                if (DownloadCheckpoint.trusted(hash, expectedSize)) requireDigest(partial, hash, budget, operation);
                replace(partial, target);
                return target;
            }
        } catch (IOException failure) {
            boolean resumableFailure = failure instanceof DownloadBudget.Failure
                    ? ((DownloadBudget.Failure) failure).reason == DownloadBudget.Reason.TIMEOUT
                    : !writingStarted || failure instanceof DownloadBudget.ReadFailure
                        || failure instanceof java.net.SocketTimeoutException;
            preserve = resumableFailure && (operation == null || !operation.cancelled())
                    && DownloadCheckpoint.cachedBytes(folder, name, hash, expectedSize) > 0;
            throw failure;
        } finally {
            if (operation != null) operation.clearConnection();
            if (connection != null) connection.disconnect();
            if (!preserve) {
                if (partial.exists()) partial.delete();
                if (marker.exists()) marker.delete();
            }
        }
    }

    static void requireHttps(URL url) throws IOException {
        if (!"https".equalsIgnoreCase(url.getProtocol()) || url.getHost().isEmpty()
                || url.getUserInfo() != null) throw new IOException("Download URL must use HTTPS");
    }

    private static void requireDigest(File file, String hash, DownloadBudget budget,
                                      DownloadOperation operation) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = new FileInputStream(file)) {
                byte[] bytes = new byte[64 * 1024];
                int count;
                while ((count = input.read(bytes)) != -1) {
                    budget.timeoutMillis(1);
                    if (operation != null) operation.check();
                    digest.update(bytes, 0, count);
                }
            }
            StringBuilder actual = new StringBuilder();
            for (byte value : digest.digest()) actual.append(String.format(java.util.Locale.ROOT, "%02x", value & 255));
            if (!actual.toString().equalsIgnoreCase(hash)) throw new DownloadBudget.Failure(DownloadBudget.Reason.INTEGRITY);
        } catch (NoSuchAlgorithmException impossible) { throw new IOException(impossible); }
    }

    public static void replace(File partial, File target) throws IOException {
        if (target.exists() && !target.delete()) throw new IOException("Cannot replace old download");
        if (!partial.renameTo(target)) throw new IOException("Cannot finalize download");
    }
}
