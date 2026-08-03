package app.aptelly.tv.install;

import android.content.Context;
import android.content.SharedPreferences;

public final class PendingInstallStore {
    private static final String PREFERENCES = "pending_install";

    public enum State {
        REQUESTED,
        WAITING_PERMISSION,
        DOWNLOADING,
        WAITING_CONFIRMATION,
        FAILED
    }

    public static final class Task {
        public final String packageName;
        public final String appName;
        public final State state;
        public final long updatedAt;
        public final String message;

        private Task(
                String packageName,
                String appName,
                State state,
                long updatedAt,
                String message
        ) {
            this.packageName = packageName;
            this.appName = appName;
            this.state = state;
            this.updatedAt = updatedAt;
            this.message = message;
        }
    }

    private final SharedPreferences preferences;

    public PendingInstallStore(Context context) {
        preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
    }

    public void save(
            String packageName,
            String appName,
            State state,
            String message
    ) {
        preferences.edit()
                .putString("package_name", packageName)
                .putString("app_name", appName)
                .putString("state", state.name())
                .putLong("updated_at", System.currentTimeMillis())
                .putString("message", message == null ? "" : message)
                .apply();
    }

    public Task read() {
        String packageName = preferences.getString("package_name", "");
        if (packageName == null || packageName.isEmpty()) {
            return null;
        }
        String stateName = preferences.getString("state", State.REQUESTED.name());
        State state;
        try {
            state = State.valueOf(stateName);
        } catch (Exception ignored) {
            state = State.REQUESTED;
        }
        return new Task(
                packageName,
                preferences.getString("app_name", ""),
                state,
                preferences.getLong("updated_at", 0),
                preferences.getString("message", "")
        );
    }

    public void clear() {
        preferences.edit().clear().apply();
    }
}
