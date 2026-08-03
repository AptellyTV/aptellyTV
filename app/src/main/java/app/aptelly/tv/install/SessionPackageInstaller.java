package app.aptelly.tv.install;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;

import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.UUID;

final class SessionPackageInstaller {
    private SessionPackageInstaller() {
    }

    static void install(
            Context context,
            InstallPlan plan,
            List<File> apkFiles,
            InstallSessionRegistry.Callback callback
    ) throws Exception {
        if (plan.artifacts.size() != apkFiles.size()) {
            throw new IllegalArgumentException("Artifact/file count mismatch");
        }
        PackageInstaller packageInstaller =
                context.getPackageManager().getPackageInstaller();
        PackageInstaller.SessionParams params =
                new PackageInstaller.SessionParams(
                        PackageInstaller.SessionParams.MODE_FULL_INSTALL
                );
        if (plan.packageName != null && !plan.packageName.isEmpty()) {
            params.setAppPackageName(plan.packageName);
        }
        params.setSize(totalBytes(apkFiles));
        int sessionId = packageInstaller.createSession(params);
        String requestId = UUID.randomUUID().toString();
        InstallSessionRegistry.register(requestId, callback);

        try (PackageInstaller.Session session = packageInstaller.openSession(sessionId)) {
            byte[] buffer = new byte[64 * 1024];
            for (int index = 0; index < apkFiles.size(); index++) {
                File apk = apkFiles.get(index);
                String entryName = plan.artifacts.get(index).fileName;
                try (FileInputStream input = new FileInputStream(apk);
                     OutputStream output = session.openWrite(
                             entryName,
                             0,
                             apk.length()
                     )) {
                    int count;
                    while ((count = input.read(buffer)) != -1) {
                        output.write(buffer, 0, count);
                    }
                    session.fsync(output);
                }
            }
            Intent status = new Intent(context, InstallStatusReceiver.class);
            status.putExtra(InstallStatusReceiver.EXTRA_REQUEST_ID, requestId);
            PendingIntent pendingIntent = PendingIntent.getBroadcast(
                    context,
                    sessionId,
                    status,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE
            );
            session.commit(pendingIntent.getIntentSender());
        } catch (Exception exception) {
            InstallSessionRegistry.failure(requestId, exception.getMessage());
            try {
                packageInstaller.abandonSession(sessionId);
            } catch (Exception ignored) {
                // The installer may already have closed the failed session.
            }
            throw exception;
        }
    }

    private static long totalBytes(List<File> files) {
        long total = 0;
        for (File file : files) {
            total += file.length();
        }
        return total;
    }
}
