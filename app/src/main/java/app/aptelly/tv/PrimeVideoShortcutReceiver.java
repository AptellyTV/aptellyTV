package app.aptelly.tv;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Reconciles the optional launcher entry after boot or an Aptelly upgrade. */
public final class PrimeVideoShortcutReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        PrimeVideoShortcutController.sync(context);
        TvAppShortcutController.sync(context);
    }
}
