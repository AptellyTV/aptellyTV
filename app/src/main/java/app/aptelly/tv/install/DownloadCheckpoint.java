package app.aptelly.tv.install;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/** Resumes only a bounded partial belonging to an exact trusted artifact identity. */
public final class DownloadCheckpoint {
    private DownloadCheckpoint() {}
    public static long cachedBytes(File folder, String name, String hash, long size) {
        if (!trusted(hash, size)) return 0;
        File partial = new File(folder, name + ".part"), marker = new File(folder, name + ".resume");
        long length = partial.length();
        if (!partial.isFile() || length <= 0 || length >= size || !marker.isFile() || marker.length() > 128) return 0;
        try {
            return identity(hash, size).equals(new String(Files.readAllBytes(marker.toPath()), StandardCharsets.UTF_8))
                    ? length : 0;
        } catch (IOException ignored) { return 0; }
    }
    static boolean trusted(String hash, long size) {
        return hash != null && hash.matches("[a-fA-F0-9]{64}") && size > 0 && size <= DownloadBudget.MAX_FILE_BYTES;
    }
    static void record(File folder, String name, String hash, long size) throws IOException {
        if (trusted(hash, size)) Files.write(new File(folder, name + ".resume").toPath(),
                identity(hash, size).getBytes(StandardCharsets.UTF_8));
    }
    private static String identity(String hash, long size) { return hash.toLowerCase(java.util.Locale.ROOT) + " " + size; }

    static void requireRange(String range, long offset, long size) throws IOException {
        if (range == null || !range.equals("bytes " + offset + "-" + (size - 1) + "/" + size)) {
            throw new DownloadBudget.Failure(DownloadBudget.Reason.SIZE);
        }
    }
}
