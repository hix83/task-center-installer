// Copyright 2026 hix83 and contributors
// SPDX-License-Identifier: Apache-2.0
package local.taskcenter.launcher;

import android.content.*;
import android.content.pm.PackageInstaller;

/** Explicit mutable PendingIntent callback; not accessible to other apps directly. */
public final class InstallResultReceiver extends BroadcastReceiver {
  static Intent confirmation;

  static android.content.SharedPreferences prefs(Context c) {
    return c.getSharedPreferences("installation-report", Context.MODE_PRIVATE);
  }

  static String report(Context c) {
    return prefs(c).getString("report", "Попыток установки пока нет.");
  }

  static void append(Context c, String line) {
    prefs(c)
        .edit()
        .putString("report", report(c) + "\n" + new java.util.Date() + "\n" + line)
        .commit();
  }

  @Override
  public void onReceive(Context c, Intent i) {
    int id = i.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, -1);
    if (id != prefs(c).getInt("session", -2)) return;
    int status = i.getIntExtra(PackageInstaller.EXTRA_STATUS, Integer.MIN_VALUE);
    String message = i.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
    if (message != null && message.length() > 4096) message = message.substring(0, 4096);
    append(
        c,
        "Session: "
            + id
            + "\nStatus: "
            + status
            + " ("
            + InstallStatus.name(status)
            + ")\nMessage: "
            + (message == null ? "не предоставлено системой" : message)
            + "\nLegacy status: "
            + (i.hasExtra("android.content.pm.extra.LEGACY_STATUS")
                ? i.getIntExtra("android.content.pm.extra.LEGACY_STATUS", 0)
                : "не предоставлен"));
    if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
      confirmation = i.getParcelableExtra(Intent.EXTRA_INTENT);
      // The foreground activity launches confirmation, avoiding background activity restrictions.
    } else {
      confirmation = null;
      prefs(c).edit().putBoolean("active", false).commit();
    }
  }
}
