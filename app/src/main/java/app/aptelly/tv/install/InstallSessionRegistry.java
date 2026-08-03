package app.aptelly.tv.install;

import java.util.concurrent.ConcurrentHashMap;

final class InstallSessionRegistry {
    interface Callback {
        void onSuccess();

        void onFailure(String message);
    }

    private static final ConcurrentHashMap<String, Callback> CALLBACKS =
            new ConcurrentHashMap<>();

    private InstallSessionRegistry() {
    }

    static void register(String requestId, Callback callback) {
        CALLBACKS.put(requestId, callback);
    }

    static void success(String requestId) {
        Callback callback = CALLBACKS.remove(requestId);
        if (callback != null) {
            callback.onSuccess();
        }
    }

    static void failure(String requestId, String message) {
        Callback callback = CALLBACKS.remove(requestId);
        if (callback != null) {
            callback.onFailure(message);
        }
    }
}
