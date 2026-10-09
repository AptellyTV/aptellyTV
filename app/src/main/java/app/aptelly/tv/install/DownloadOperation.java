package app.aptelly.tv.install;

import java.net.HttpURLConnection;

/** A cancellation token which cannot cancel an already submitted Android installation. */
public final class DownloadOperation {
    private enum State { ACTIVE, CANCELLED, SUBMITTED, FINISHED }
    private State state = State.ACTIVE;
    private Thread worker;
    private HttpURLConnection connection;

    public synchronized void bindWorker() throws DownloadBudget.Failure {
        check();
        worker = Thread.currentThread();
    }

    public synchronized void unbindWorker() { worker = null; connection = null; }

    public synchronized void bindConnection(HttpURLConnection value) throws DownloadBudget.Failure {
        check();
        connection = value;
    }

    public synchronized void clearConnection() { connection = null; }

    public boolean cancel() {
        HttpURLConnection closing;
        synchronized (this) {
            if (state != State.ACTIVE) return false;
            state = State.CANCELLED;
            if (worker != null) worker.interrupt();
            closing = connection;
            connection = null;
        }
        // Closing a blocked socket must not freeze the remote-control UI.
        if (closing != null) {
            Thread closer = new Thread(() -> {
                try { closing.disconnect(); } catch (RuntimeException ignored) { }
            }, "aptelly-cancel-download");
            closer.setDaemon(true);
            closer.start();
        }
        return true;
    }

    public synchronized void check() throws DownloadBudget.Failure {
        if (state == State.CANCELLED) throw new DownloadBudget.Failure(DownloadBudget.Reason.CANCELLED);
    }

    public synchronized boolean submit() {
        if (state != State.ACTIVE) return false;
        state = State.SUBMITTED;
        worker = null;
        connection = null;
        return true;
    }

    public synchronized void finish() { state = State.FINISHED; worker = null; connection = null; }
    public synchronized boolean cancelled() { return state == State.CANCELLED; }
}
