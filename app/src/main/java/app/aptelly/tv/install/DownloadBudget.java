package app.aptelly.tv.install;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.function.LongSupplier;

/** Limits the complete install set, including streams without Content-Length. */
public final class DownloadBudget {
    public static final long MAX_FILE_BYTES = 512L * 1024 * 1024;
    public static final long MAX_SET_BYTES = 1024L * 1024 * 1024;
    public static final long RESERVE_BYTES = 64L * 1024 * 1024;
    public static final long TIMEOUT_NANOS = 15L * 60 * 1_000_000_000;
    public enum Reason { SPACE, SIZE, TIMEOUT, CANCELLED }
    public interface ReadGuard { void run() throws IOException; }
    public static final class Failure extends IOException {
        public final Reason reason;
        Failure(Reason reason) { super(reason.name()); this.reason = reason; }
    }

    private final LongSupplier clock;
    private final long started;
    private final long declaredTotal;
    private long received;

    public DownloadBudget(long... sizes) throws Failure { this(System::nanoTime, sizes); }

    DownloadBudget(LongSupplier clock, long... sizes) throws Failure {
        this.clock = clock;
        this.started = clock.getAsLong();
        long total = 0;
        for (long size : sizes) {
            if (size == 0 || size < -1 || size > MAX_FILE_BYTES) throw new Failure(Reason.SIZE);
            if (size > 0) total += size;
            if (total > MAX_SET_BYTES) throw new Failure(Reason.SIZE);
        }
        declaredTotal = total;
    }

    public void preflight(long usableSpace) throws Failure {
        check();
        if (usableSpace < 2 * declaredTotal + RESERVE_BYTES) throw new Failure(Reason.SPACE);
    }

    public int timeoutMillis(int maximum) throws Failure {
        check();
        long remaining = TIMEOUT_NANOS - (clock.getAsLong() - started);
        if (remaining <= 0) throw new Failure(Reason.TIMEOUT);
        return (int) Math.max(1, Math.min(maximum, remaining / 1_000_000));
    }

    public void checkLength(long expected, long declared) throws Failure {
        if (declared > MAX_FILE_BYTES || (expected > 0 && declared >= 0 && declared != expected)) {
            throw new Failure(Reason.SIZE);
        }
    }

    public long copy(InputStream input, OutputStream output, long expected,
                     LongSupplier space, ReadGuard beforeRead) throws IOException {
        long fileBytes = 0;
        byte[] buffer = new byte[64 * 1024];
        while (true) {
            check();
            if (beforeRead != null) beforeRead.run();
            int count = input.read(buffer);
            check();
            if (count == -1) break;
            if (count == 0) continue;
            if (fileBytes + count > MAX_FILE_BYTES || received + count > MAX_SET_BYTES
                    || (expected > 0 && fileBytes + count > expected)) throw new Failure(Reason.SIZE);
            // Reserve a second copy for Android's installer, even for an unknown-size stream.
            if (space.getAsLong() < RESERVE_BYTES + Math.max(declaredTotal, received + count) + count) {
                throw new Failure(Reason.SPACE);
            }
            output.write(buffer, 0, count);
            fileBytes += count;
            received += count;
        }
        if (fileBytes == 0 || (expected > 0 && fileBytes != expected)) throw new Failure(Reason.SIZE);
        return fileBytes;
    }

    private void check() throws Failure {
        if (Thread.currentThread().isInterrupted()) throw new Failure(Reason.CANCELLED);
        if (clock.getAsLong() - started >= TIMEOUT_NANOS) throw new Failure(Reason.TIMEOUT);
    }
}
