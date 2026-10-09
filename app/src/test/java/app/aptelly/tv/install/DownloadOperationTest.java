package app.aptelly.tv.install;
import org.junit.Test;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import static org.junit.Assert.*;

public class DownloadOperationTest {
    @Test public void cancelledQueuedCommitCannotSubmit() throws Exception {
        DownloadOperation operation = new DownloadOperation();
        assertTrue(operation.cancel());
        assertFalse(operation.submit());
        try { operation.check(); fail("Cancellation must propagate"); }
        catch (DownloadBudget.Failure error) { assertEquals(DownloadBudget.Reason.CANCELLED,error.reason); }
    }
    @Test public void cancellationInterruptsOnlyTheBoundWorker() throws Exception {
        DownloadOperation operation = new DownloadOperation();
        CountDownLatch started = new CountDownLatch(1), interrupted = new CountDownLatch(1);
        Thread worker = new Thread(() -> {
            try { operation.bindWorker(); started.countDown(); Thread.sleep(10000); }
            catch (InterruptedException expected) { interrupted.countDown(); }
            catch (DownloadBudget.Failure error) { throw new AssertionError(error); }
            finally { operation.unbindWorker(); }
        });
        worker.start(); assertTrue(started.await(2,TimeUnit.SECONDS));
        assertTrue(operation.cancel()); assertTrue(interrupted.await(2,TimeUnit.SECONDS)); worker.join(2000);
        assertFalse(worker.isAlive()); assertFalse(Thread.currentThread().isInterrupted());
    }
    @Test public void submittedAndFinishedOperationsCannotCancelAnotherJob() throws Exception {
        DownloadOperation operation = new DownloadOperation();
        operation.bindWorker(); assertTrue(operation.submit()); assertFalse(operation.cancel());
        operation.finish(); assertFalse(operation.cancel()); assertFalse(Thread.currentThread().isInterrupted());
    }
}
