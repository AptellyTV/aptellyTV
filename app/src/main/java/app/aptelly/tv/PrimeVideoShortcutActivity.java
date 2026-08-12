package app.aptelly.tv;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import app.aptelly.tv.catalog.InstalledAppResolver;

/** Transparent launcher entry that delegates to the signed Prime Video package. */
public final class PrimeVideoShortcutActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        PrimeVideoShortcutController.sync(this);
        Intent prime = InstalledAppResolver.launchIntent(
                this,
                PrimeVideoShortcutController.PRIME_PACKAGE
        );
        if (prime != null) {
            startActivity(prime);
        } else {
            Intent aptelly = new Intent(this, MainActivity.class);
            aptelly.putExtra(
                    MainActivity.EXTRA_FOCUS_PACKAGE,
                    PrimeVideoShortcutController.PRIME_PACKAGE
            );
            startActivity(aptelly);
        }
        finish();
    }
}
