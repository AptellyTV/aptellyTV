package app.aptelly.tv.device;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.provider.Settings;
import android.widget.Toast;

import app.aptelly.tv.R;

import java.util.List;

public final class HomeController {
    private HomeController() {
    }

    public static void openHomeSettings(Activity activity) {
        try {
            activity.startActivity(new Intent(Settings.ACTION_HOME_SETTINGS));
        } catch (ActivityNotFoundException exception) {
            activity.startActivity(new Intent(Settings.ACTION_SETTINGS));
        }
    }

    public static void openOriginalHome(Activity activity) {
        Intent query = new Intent(Intent.ACTION_MAIN);
        query.addCategory(Intent.CATEGORY_HOME);
        List<ResolveInfo> homes = activity.getPackageManager().queryIntentActivities(query, 0);

        for (ResolveInfo info : homes) {
            if (info.activityInfo == null
                    || activity.getPackageName().equals(info.activityInfo.packageName)) {
                continue;
            }
            Intent launch = new Intent(Intent.ACTION_MAIN);
            launch.addCategory(Intent.CATEGORY_HOME);
            launch.setComponent(new ComponentName(
                    info.activityInfo.packageName,
                    info.activityInfo.name
            ));
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                activity.startActivity(launch);
                return;
            } catch (RuntimeException ignored) {
                // Some manufacturer launchers are private. Try the next candidate.
            }
        }

        Toast.makeText(activity, R.string.no_store, Toast.LENGTH_SHORT).show();
        openHomeSettings(activity);
    }
}
