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
        SUCCEEDED,
        FAILED
    }

    public static final class Task {
        public final String packageName;
        public final String appName;
        public final State state;
        public final long updatedAt;
        public final String message;
        public final InstallTarget target;
        public final int sessionId;
        public final String requestId;
        public final String expectedFiles;

        private Task(
                String packageName,
                String appName,
                State state,
                long updatedAt,
                String message,
                InstallTarget target, int sessionId, String requestId, String expectedFiles
        ) {
            this.packageName = packageName;
            this.appName = appName;
            this.state = state;
            this.updatedAt = updatedAt;
            this.message = message;
            this.target = target;
            this.sessionId = sessionId;
            this.requestId = requestId;
            this.expectedFiles = expectedFiles;
        }
    }

    private final SharedPreferences preferences;

    public PendingInstallStore(Context context) {
        this(context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE));
    }

    PendingInstallStore(SharedPreferences preferences) { this.preferences = preferences; }

    public void save(
            String packageName,
            String appName,
            State state,
            String message
    ) {
        SharedPreferences.Editor editor = preferences.edit();
        if (state == State.REQUESTED || !packageName.equals(preferences.getString("package_name", ""))) {
            editor.clear();
        }
        editor.putString("package_name", packageName)
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
                preferences.getString("message", ""),
                new InstallTarget(preferences.getString("actual_package_name", ""),
                        preferences.getLong("expected_version_code", 0),
                        preferences.getString("expected_certificate", ""),
                        preferences.getInt("expected_splits", 0)),
                preferences.getInt("session_id", -1),
                preferences.getString("request_id", ""),
                preferences.getString("expected_files", "")
        );
    }

    /** Persist before session.commit; callbacks must survive process death. */
    public void attachSession(InstallPlan plan, int sessionId, String requestId) {
        org.json.JSONArray files = new org.json.JSONArray();
        for (ArtifactFile file : plan.artifacts) {
            org.json.JSONObject row = new org.json.JSONObject();
            try {
                row.put("name", file.fileName);
                row.put("sha256", file.sha256);
                row.put("size", file.sizeBytes);
                row.put("kind", file.kind.name());
            } catch (org.json.JSONException exception) { throw new IllegalArgumentException(exception); }
            files.put(row);
        }
        InstallTarget target = InstalledTargetVerifier.target(plan);
        if (!preferences.edit().putString("actual_package_name", plan.packageName)
                .putLong("expected_version_code", plan.versionCode)
                .putString("expected_certificate", plan.expectedCertificateSha256)
                .putInt("expected_splits", target.requiredSplits)
                .putString("expected_files", files.toString())
                .putInt("session_id", sessionId).putString("request_id", requestId)
                .putString("state", State.WAITING_CONFIRMATION.name()).commit()) {
            throw new IllegalStateException("Unable to persist install session");
        }
    }

    public void recordResult(String requestId, int sessionId, State state, String message) {
        Task task = read();
        if (task == null || !task.requestId.equals(requestId) || task.sessionId != sessionId) return;
        preferences.edit().putString("state", state.name())
                .putString("message", message == null ? "" : message)
                .putLong("updated_at", System.currentTimeMillis()).commit();
    }

    public void clear() {
        preferences.edit().clear().apply();
    }
}
