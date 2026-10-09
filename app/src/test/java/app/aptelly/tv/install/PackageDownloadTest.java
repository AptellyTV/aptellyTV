package app.aptelly.tv.install;

import org.junit.Test;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import java.io.*;
import java.net.*;
import java.nio.file.Files;
import java.security.MessageDigest;
import static org.junit.Assert.*;

public class PackageDownloadTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    private final byte[] bytes = {1,2,3,4,5,6,7,8};
    private String hash(byte[] value) throws Exception {
        StringBuilder text = new StringBuilder();
        for (byte b : MessageDigest.getInstance("SHA-256").digest(value)) text.append(String.format("%02x",b & 255));
        return text.toString();
    }
    private static class Response extends HttpURLConnection {
        final int status; final InputStream body; final long size; final String range;
        boolean disconnected;
        Response(int status, InputStream body,long size,String range) throws Exception {
            super(new URL("https://example.com/app.apk"));this.status=status;this.body=body;this.size=size;this.range=range;
        }
        @Override public int getResponseCode() { return status; }
        @Override public long getContentLengthLong() { return size; }
        @Override public String getHeaderField(String key) { return "Content-Range".equals(key)?range:null; }
        @Override public InputStream getInputStream() { return body; }
        @Override public void connect() {}
        @Override public boolean usingProxy() { return false; }
        @Override public void disconnect() { disconnected=true; }
    }
    private File fetch(Response response,DownloadBudget budget,DownloadOperation operation) throws Exception {
        return PackageDownload.fetch(temporary.getRoot(),"app-0","https://example.com/app.apk","test",
                bytes.length,hash(bytes),budget,operation,url -> response);
    }
    private void checkpoint(byte[] value, String expected) throws Exception {
        Files.write(new File(temporary.getRoot(),"app-0.part").toPath(),value);
        DownloadCheckpoint.record(temporary.getRoot(),"app-0",expected,bytes.length);
    }
    @Test public void networkInterruptionResumesOnlyTheExactArtifact() throws Exception {
        InputStream broken=new InputStream() {
            int reads;
            @Override public int read() throws IOException { throw new IOException("Not used"); }
            @Override public int read(byte[] buffer) throws IOException {
                if (reads++ > 0) throw new IOException("Network interrupted");
                System.arraycopy(bytes,0,buffer,0,4);return 4;
            }
        };
        Response first=new Response(200,broken,8,null);
        try { fetch(first,new DownloadBudget(8),null);fail("Expected interruption"); } catch(IOException expected) {}
        assertTrue(first.disconnected);
        assertEquals(4,DownloadCheckpoint.cachedBytes(temporary.getRoot(),"app-0",hash(bytes),8));
        assertEquals(0,DownloadCheckpoint.cachedBytes(temporary.getRoot(),"app-0","f".repeat(64),8));
        DownloadBudget retry=new DownloadBudget(8);retry.cached(4);
        Response resumed=new Response(206,new ByteArrayInputStream(new byte[]{5,6,7,8}),4,"bytes 4-7/8");
        assertArrayEquals(bytes,Files.readAllBytes(fetch(resumed,retry,null).toPath()));
        assertEquals("bytes=4-",resumed.getRequestProperty("Range"));
        assertFalse(new File(temporary.getRoot(),"app-0.resume").exists());
    }
    @Test public void serverIgnoringRangeRestartsRatherThanAppendingAWholeApk() throws Exception {
        checkpoint(new byte[]{1,2,3,4},hash(bytes));
        DownloadBudget budget=new DownloadBudget(8);budget.cached(4);
        File complete=fetch(new Response(200,new ByteArrayInputStream(bytes),8,null),budget,null);
        assertEquals(8,complete.length());assertArrayEquals(bytes,Files.readAllBytes(complete.toPath()));
    }
    @Test public void inconsistentRangeOrCorruptCachedBytesNeverBecomeCandidates() throws Exception {
        for (boolean wrongRange : new boolean[]{true,false}) {
            checkpoint(new byte[]{9,9,9,9},hash(bytes));
            DownloadBudget budget=new DownloadBudget(8);budget.cached(4);
            try {
                fetch(new Response(206,new ByteArrayInputStream(new byte[]{5,6,7,8}),4,
                        wrongRange?"bytes 3-7/8":"bytes 4-7/8"),budget,null);
                fail("Invalid resumed bytes");
            } catch(DownloadBudget.Failure expected) {
                assertEquals(wrongRange?DownloadBudget.Reason.SIZE:DownloadBudget.Reason.INTEGRITY,expected.reason);
            }
            assertFalse(new File(temporary.getRoot(),"app-0.apk").exists());
            assertFalse(new File(temporary.getRoot(),"app-0.part").exists());
        }
    }
    @Test public void explicitCancellationDeletesTheCheckpoint() throws Exception {
        checkpoint(new byte[]{1,2,3,4},hash(bytes));
        DownloadBudget budget=new DownloadBudget(8);budget.cached(4);
        DownloadOperation operation=new DownloadOperation();operation.cancel();
        try { fetch(new Response(206,new ByteArrayInputStream(bytes),4,"bytes 4-7/8"),budget,operation);fail(); }
        catch(DownloadBudget.Failure expected) { assertEquals(DownloadBudget.Reason.CANCELLED,expected.reason); }
        assertFalse(new File(temporary.getRoot(),"app-0.part").exists());
        assertFalse(new File(temporary.getRoot(),"app-0.resume").exists());
    }
    @Test public void progressIncludesAllFilesAndCachedBytesWithoutGuessingUnknownTotals() throws Exception {
        DownloadBudget budget=new DownloadBudget(4,4);budget.cached(2);
        long[] observed=new long[2];budget.progress((current,total)->{observed[0]=current;observed[1]=total;});
        budget.copy(new ByteArrayInputStream(new byte[]{3,4}),new ByteArrayOutputStream(),4,2,()->Long.MAX_VALUE,null);
        assertEquals(4,observed[0]);assertEquals(8,observed[1]);
        budget.copy(new ByteArrayInputStream(new byte[4]),new ByteArrayOutputStream(),4,()->Long.MAX_VALUE,null);
        assertEquals(8,observed[0]);
        new DownloadBudget(-1).progress((current,total)->assertEquals(-1,total));
    }
}
