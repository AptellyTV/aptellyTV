package app.aptelly.tv.device;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.widget.Toast;
import app.aptelly.tv.R;
import org.json.JSONObject;
import java.util.WeakHashMap;
import java.util.ArrayList;
import java.util.List;

/** First-use local capability check before selecting an installation source. */
public final class EnvironmentCheck {
    private static final String PREFS = "environment_check";
    private static final WeakHashMap<Activity, List<Runnable>> ACTIVE = new WeakHashMap<>();
    private EnvironmentCheck() { }
    public static boolean ready(Context context) { return prefs(context).getInt("notice_version", 0) >= 1
            && prefs(context).getBoolean("check_complete", false); }

    public static boolean ensure(Activity activity, Runnable completed) {
        if (ready(activity)) return true;
        if (activity.isFinishing() || activity.isDestroyed()) return false;
        if (ACTIVE.containsKey(activity)) { ACTIVE.get(activity).add(completed); return false; }
        List<Runnable> callbacks = new ArrayList<>(); callbacks.add(completed);
        ACTIVE.put(activity, callbacks);
        if (prefs(activity).getInt("notice_version", 0) >= 1) { check(activity, completed); return false; }
        AlertDialog dialog = new AlertDialog.Builder(activity).setTitle(R.string.environment_check_title)
                .setMessage(R.string.environment_check_notice).setCancelable(false)
                .setPositiveButton(R.string.environment_check_start, (ignored, which) -> {
                    prefs(activity).edit().putInt("notice_version", 1).commit();
                    check(activity, completed);
                }).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).requestFocus());
        dialog.show();
        return false;
    }

    private static void check(Activity activity, Runnable completed) {
        Context context = activity.getApplicationContext();
        Toast.makeText(activity, R.string.environment_check_running, Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            boolean passed = false;
            try {
                StaticDeviceProfile profile = StaticDeviceProfile.collect(context);
                JSONObject device = new JSONObject();
                device.put("hardware_profile_id", profile.hardwareProfileId);
                device.put("api", profile.androidApi);
                device.put("android_release", profile.androidRelease);
                device.put("fingerprint", profile.buildFingerprint);
                device.put("runtime_versions", PlaybackCapabilities.runtimeVersions(context));
                device.put("reported_media_capabilities", PlaybackCapabilities.collect(context));
                device.put("environment_revision", EnvironmentRevision.calculate(device));
                passed = prefs(context).edit().putString("local_snapshot", device.toString())
                        .putBoolean("check_complete", true).putString("last_error", "").commit();
            } catch (Exception error) {
                prefs(context).edit().putBoolean("check_complete", true)
                        .putString("last_error", "CAPABILITY_UNKNOWN").commit();
            }
            boolean succeeded = passed;
            activity.runOnUiThread(() -> {
                List<Runnable> callbacks = ACTIVE.remove(activity);
                if (activity.isFinishing() || activity.isDestroyed()) return;
                Toast.makeText(activity, succeeded ? R.string.environment_check_complete : R.string.environment_check_unknown,
                        Toast.LENGTH_LONG).show();
                if (callbacks != null) for (Runnable callback : callbacks) callback.run();
            });
        }, "environment-check").start();
    }
    public static void repeat(Activity activity, Runnable completed) {
        prefs(activity).edit().putBoolean("check_complete", false).apply();
        ensure(activity, completed);
    }
    private static SharedPreferences prefs(Context context) { return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE); }
}
