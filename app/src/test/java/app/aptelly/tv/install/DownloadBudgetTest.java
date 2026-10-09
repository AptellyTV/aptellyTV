package app.aptelly.tv.install;

import org.junit.Test;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.Assert.*;

public class DownloadBudgetTest {
    private void fails(DownloadBudget.Reason reason, Checked action) throws Exception {
        try { action.run(); fail("Expected " + reason); }
        catch (DownloadBudget.Failure error) { assertEquals(reason, error.reason); }
    }
    interface Checked { void run() throws Exception; }
    @Test public void reservesFullInstallSetAndInstallerCopy() throws Exception {
        DownloadBudget budget = new DownloadBudget(100, 200);
        fails(DownloadBudget.Reason.SPACE, () -> budget.preflight(DownloadBudget.RESERVE_BYTES + 599));
        budget.preflight(DownloadBudget.RESERVE_BYTES + 600);
        fails(DownloadBudget.Reason.SIZE, () -> new DownloadBudget(Long.MAX_VALUE));
        fails(DownloadBudget.Reason.SIZE, () -> new DownloadBudget(DownloadBudget.MAX_FILE_BYTES,
                DownloadBudget.MAX_FILE_BYTES, 1));
    }
    @Test public void rejectsTruncatedAndExcessStreamsBeforeWritingExcess() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        fails(DownloadBudget.Reason.SIZE, () -> new DownloadBudget(2).copy(
                new ByteArrayInputStream(new byte[3]), output, 2, () -> Long.MAX_VALUE, null));
        assertEquals(0, output.size());
        fails(DownloadBudget.Reason.SIZE, () -> new DownloadBudget(3).copy(
                new ByteArrayInputStream(new byte[2]), output, 3, () -> Long.MAX_VALUE, null));
    }
    @Test public void handlesChunkedLengthButRejectsConflictingHeader() throws Exception {
        DownloadBudget budget = new DownloadBudget(3);
        budget.checkLength(3, -1);
        fails(DownloadBudget.Reason.SIZE, () -> budget.checkLength(3, 0));
        fails(DownloadBudget.Reason.SIZE, () -> budget.checkLength(3, 4));
        assertEquals(3, budget.copy(new ByteArrayInputStream(new byte[3]),
                new ByteArrayOutputStream(), 3, () -> Long.MAX_VALUE, null));
    }
    @Test public void unknownLengthStillReservesInstallerSpaceAndIsBounded() throws Exception {
        fails(DownloadBudget.Reason.SPACE, () -> new DownloadBudget(-1).copy(
                new ByteArrayInputStream(new byte[3]), new ByteArrayOutputStream(), -1,
                () -> DownloadBudget.RESERVE_BYTES + 5, null));
        InputStream endless = new InputStream() {
            @Override public int read() { return 0; }
            @Override public int read(byte[] buffer) { return buffer.length; }
        };
        fails(DownloadBudget.Reason.SIZE, () -> new DownloadBudget(-1).copy(endless,
                OutputDiscard.INSTANCE, -1, () -> Long.MAX_VALUE, null));
    }
    private static class OutputDiscard extends java.io.OutputStream {
        static final OutputDiscard INSTANCE = new OutputDiscard();
        @Override public void write(int value) {}
        @Override public void write(byte[] value, int start, int length) {}
    }
    @Test public void deadlineCoversWholeSetAndTrickleStreams() throws Exception {
        AtomicLong time = new AtomicLong();
        DownloadBudget budget = new DownloadBudget(time::get, 1, 1);
        budget.copy(new ByteArrayInputStream(new byte[1]), new ByteArrayOutputStream(), 1,
                () -> Long.MAX_VALUE, () -> time.addAndGet(DownloadBudget.TIMEOUT_NANOS / 3));
        fails(DownloadBudget.Reason.TIMEOUT, () -> budget.copy(new ByteArrayInputStream(new byte[1]),
                new ByteArrayOutputStream(), 1, () -> Long.MAX_VALUE,
                () -> time.addAndGet(DownloadBudget.TIMEOUT_NANOS / 3)));
    }
    @Test public void cancellationCannotReturnAnInstallCandidate() throws Exception {
        Thread.currentThread().interrupt();
        try { fails(DownloadBudget.Reason.CANCELLED, () -> new DownloadBudget(1).preflight(Long.MAX_VALUE)); }
        finally { Thread.interrupted(); }
    }
    @Test public void rejectsHttpAndCredentialBearingRedirectTargets() throws Exception {
        PackageDownload.requireHttps(new URL("https://example.com/app.apk"));
        for (String unsafe : new String[]{"http://example.com/app.apk", "https://user:pass@example.com/app.apk"}) {
            try { PackageDownload.requireHttps(new URL(unsafe)); fail("Unsafe URL"); }
            catch (java.io.IOException expected) { }
        }
    }
}
