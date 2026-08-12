package app.aptelly.tv;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import app.aptelly.tv.catalog.InstalledAppResolver;

/** Transparent MFTR0 launcher shim that delegates to an installed signed TV package. */
public abstract class TvAppShortcutActivity extends Activity {
    protected abstract String[] targetPackages();

    @Override
    protected final void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        TvAppShortcutController.sync(this);
        String installed = TvAppShortcutController.installedPackage(
                this,
                targetPackages()
        );
        Intent target = installed == null
                ? null
                : InstalledAppResolver.launchIntent(this, installed);
        if (target != null) {
            startActivity(target);
        } else {
            Intent aptelly = new Intent(this, MainActivity.class);
            if (targetPackages().length > 0) {
                aptelly.putExtra(MainActivity.EXTRA_FOCUS_PACKAGE, targetPackages()[0]);
            }
            startActivity(aptelly);
        }
        finish();
    }

    public static final class Plex extends TvAppShortcutActivity {
        @Override protected String[] targetPackages() {
            return new String[]{"com.plexapp.android"};
        }
    }

    public static final class Fawesome extends TvAppShortcutActivity {
        @Override protected String[] targetPackages() {
            return new String[]{"com.future.moviesByFawesomeAndroidTV"};
        }
    }

    public static final class Crunchyroll extends TvAppShortcutActivity {
        @Override protected String[] targetPackages() {
            return new String[]{"com.crunchyroll.crunchyroid"};
        }
    }

    public static final class Espn extends TvAppShortcutActivity {
        @Override protected String[] targetPackages() {
            return new String[]{"com.espn.score_center"};
        }
    }

    public static final class F1 extends TvAppShortcutActivity {
        @Override protected String[] targetPackages() {
            return new String[]{"com.formulaone.production"};
        }
    }

    public static final class BritBox extends TvAppShortcutActivity {
        @Override protected String[] targetPackages() {
            return new String[]{"com.britbox.tv"};
        }
    }

    public static final class Globoplay extends TvAppShortcutActivity {
        @Override protected String[] targetPackages() {
            return new String[]{"com.globo.globotv"};
        }
    }

    public static final class JioHotstar extends TvAppShortcutActivity {
        @Override protected String[] targetPackages() {
            return new String[]{"in.startv.hotstar"};
        }
    }

    public static final class DiscoveryPlus extends TvAppShortcutActivity {
        @Override protected String[] targetPackages() {
            return new String[]{
                    "com.discoveryplus.tv.android",
                    "com.discovery.discoveryplus.mobile",
                    "com.discovery.discoveryplus.firetv"
            };
        }
    }
}
