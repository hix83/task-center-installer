// Copyright 2026 hix83 and contributors
// SPDX-License-Identifier: Apache-2.0

package local.taskcenter.launcher;

import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.graphics.Color;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.security.*;

public class MainActivity extends Activity {
  static final String PKG = "com.miui.securitycenter",
      TARGET = "com.miui.autotask.activity.TaskManagerActivity",
      CERT = "c9009d01ebf9f5d0302bc71b2fe9aa9a47a432bba17308a3111b75d7b2149025";
  static final long VERSION = 40001283;
  LinearLayout box;
  TextView status;
  boolean busy = false, shown = false;

  int dp(int x) {
    return (int) (x * getResources().getDisplayMetrics().density);
  }

  public void onCreate(Bundle b) {
    super.onCreate(b);
    getWindow()
        .getDecorView()
        .setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
    if (!getIntent().getBooleanExtra("setup", false)
        && !"local.taskcenter.launcher.MANAGE".equals(getIntent().getAction())
        && ready()) {
      openTasks();
      return;
    }
    show();
  }

  protected void onResume() {
    super.onResume();
    if (shown && !busy) show();
  }

  boolean ready() {
    try {
      ActivityInfo a = getPackageManager().getActivityInfo(new ComponentName(PKG, TARGET), 0);
      return a.enabled && a.exported && a.applicationInfo.enabled;
    } catch (Exception e) {
      return false;
    }
  }

  PackageInfo installed() throws Exception {
    return getPackageManager().getPackageInfo(PKG, PackageManager.GET_SIGNING_CERTIFICATES);
  }

  boolean signed(PackageInfo p) throws Exception {
    if (p.signingInfo == null) return false;
    for (android.content.pm.Signature s : p.signingInfo.getApkContentsSigners()) {
      byte[] hash = MessageDigest.getInstance("SHA-256").digest(s.toByteArray());
      StringBuilder h = new StringBuilder();
      for (byte v : hash) h.append(String.format("%02x", v & 255));
      if (CERT.equals(h.toString())) return true;
    }
    return false;
  }

  TextView text(String s, int size, int color) {
    TextView t = new TextView(this);
    t.setText(s);
    t.setTextSize(size);
    t.setTextColor(color);
    t.setPadding(0, dp(10), 0, dp(10));
    box.addView(t);
    return t;
  }

  void button(String label, Runnable r) {
    Button b = new Button(this);
    b.setText(label);
    b.setAllCaps(false);
    LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, dp(58));
    p.topMargin = dp(12);
    box.addView(b, p);
    b.setOnClickListener(v -> r.run());
  }

  void show() {
    shown = true;
    ScrollView scroll = new ScrollView(this);
    scroll.setFillViewport(true);
    scroll.setBackgroundColor(Color.rgb(245, 248, 252));
    box = new LinearLayout(this);
    box.setOrientation(1);
    box.setPadding(dp(24), dp(32), dp(24), dp(32));
    scroll.addView(box);
    setContentView(scroll);
    scroll.setOnApplyWindowInsetsListener(
        (v, i) -> {
          v.setPadding(
              i.getSystemWindowInsetLeft(),
              i.getSystemWindowInsetTop(),
              i.getSystemWindowInsetRight(),
              i.getSystemWindowInsetBottom());
          return i;
        });
    scroll.requestApplyInsets();
    text("TASK CENTER", 14, Color.rgb(28, 105, 210));
    text("Автоматизация\nна вашем Xiaomi", 30, Color.rgb(22, 35, 55));
    text(
        Build.MANUFACTURER + " " + Build.MODEL + " · Android " + Build.VERSION.RELEASE,
        14,
        Color.DKGRAY);
    if (ready()) {
      text("Всё готово", 24, Color.rgb(28, 120, 80));
      text(
          "Automated tasks доступен. Значок Task Center теперь открывает его напрямую.",
          17,
          Color.DKGRAY);
      button("Открыть Task Center", () -> openTasks());
      rollbackSection();
      text(
          "Если значка нет на рабочем столе, перенесите Task Center из списка приложений.",
          14,
          Color.DKGRAY);
      return;
    }
    String reason = null;
    try {
      PackageInfo p = installed();
      text("Безопасность: " + p.versionName, 15, Color.DKGRAY);
      if (!signed(p))
        reason = "Подпись установленной «Безопасности» не совпадает с проверенной подписью Xiaomi.";
      else if (p.getLongVersionCode() > VERSION)
        reason =
            "На телефоне установлена более новая «Безопасность». Встроенный APK не подходит для"
                + " обновления. Понижение версии не выполняется.";
    } catch (Exception e) {
      reason =
          "Не удалось проверить системное приложение «Безопасность». Этот установщик предназначен"
              + " для Xiaomi с HyperOS.";
    }
    if (Build.VERSION.SDK_INT < 28) reason = "Нужен Android 9 или новее.";
    if (reason != null) {
      text("Установка недоступна", 23, Color.rgb(160, 65, 35));
      text(reason, 17, Color.DKGRAY);
      rollbackSection();
      return;
    }
    text("1. Обновите «Безопасность»", 22, Color.rgb(22, 35, 55));
    text(
        "Внутри — оригинальная китайская версия 12.8.3. Интернет не нужен. Android попросит"
            + " разрешить установку из Task Center и подтвердить обновление.",
        17,
        Color.DKGRAY);
    text(
        "Обновится всё приложение «Безопасность». Его интерфейс может стать английским; на других"
            + " моделях возможны сбои. Открытие Task Center проверено на Xiaomi 15T Pro и 17T Pro."
            + " Выполнение сценариев ещё не проверено.",
        15,
        Color.DKGRAY);
    button(
        getPackageManager().canRequestPackageInstalls()
            ? "Установить компонент"
            : "Разрешить установку",
        () -> install());
    status = text("2. После обновления вернитесь сюда и откройте Task Center.", 16, Color.DKGRAY);
    rollbackSection();
    text(
        "Источник APK: MemeOS Updates. Подпись сверена со штатной «Безопасностью» Xiaomi. Это"
            + " независимый установщик, не приложение Xiaomi.",
        13,
        Color.GRAY);
  }

  void install() {
    if (!getPackageManager().canRequestPackageInstalls()) {
      try {
        startActivity(
            new Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:" + getPackageName())));
      } catch (Exception e) {
        error("Не удалось открыть разрешение установки.");
      }
      return;
    }
    if (busy) return;
    busy = true;
    status.setText("Подготовка и проверка APK…");
    new Thread(
            () -> {
              try {
                File f = new File(getCacheDir(), "security.apk");
                try (InputStream in = getAssets().open("security.apk");
                    OutputStream out = new FileOutputStream(f)) {
                  byte[] buf = new byte[65536];
                  int n;
                  while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
                }
                PackageInfo p =
                    getPackageManager()
                        .getPackageArchiveInfo(
                            f.getPath(), PackageManager.GET_SIGNING_CERTIFICATES);
                if (p == null
                    || !PKG.equals(p.packageName)
                    || p.getLongVersionCode() != VERSION
                    || !signed(p)) throw new IOException("Не пройдена проверка APK");
                PackageInfo cur = installed();
                if (!signed(cur) || cur.getLongVersionCode() > VERSION)
                  throw new IOException("Установленная версия изменилась или несовместима");
                runOnUiThread(
                    () -> {
                      busy = false;
                      Uri u = Uri.parse("content://local.taskcenter.launcher.apk/security.apk");
                      Intent i =
                          new Intent(Intent.ACTION_VIEW)
                              .setDataAndType(u, "application/vnd.android.package-archive")
                              .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                      i.setClipData(ClipData.newRawUri("APK", u));
                      try {
                        startActivity(i);
                      } catch (Exception e) {
                        error("Не удалось открыть системный установщик.");
                      }
                    });
              } catch (Exception e) {
                runOnUiThread(
                    () -> {
                      busy = false;
                      error("Установка не начата: " + e.getMessage());
                    });
              }
            })
        .start();
  }

  void rollbackSection() {
    boolean updated = false;
    try {
      updated = (installed().applicationInfo.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0;
    } catch (Exception ignored) {
    }
    text("Возврат к штатной версии", 22, Color.rgb(22, 35, 55));
    if (!updated) {
      text(
          "Система не сообщает об установленном обновлении «Безопасности». Удаление обновлений"
              + " может быть недоступно.",
          15,
          Color.DKGRAY);
    }
    text(
        "Откат выполняется кнопкой «Удалить обновления» / Uninstall updates в системных настройках."
            + " Вернётся версия из прошивки, а Automated tasks может исчезнуть. Настройки"
            + " «Безопасности» и созданные задачи могут быть сброшены.",
        15,
        Color.DKGRAY);
    button(
        "Открыть настройки для отката",
        () ->
            new AlertDialog.Builder(this)
                .setTitle("Откат «Безопасности»")
                .setMessage(
                    "На следующем экране нажмите «Удалить обновления» / Uninstall updates — внизу"
                        + " или в меню ⋮ — и прочитайте системное подтверждение.\n\n"
                        + "Это вернёт версию из прошивки, а не обязательно ту, что была перед"
                        + " установкой. Не нажимайте «Очистить данные» или «Удалить приложение»."
                        + " Если удаления обновлений нет, остановитесь: автоматического обхода"
                        + " здесь нет.\n\n"
                        + "Сам Task Center останется установлен. После отката он покажет мастер;"
                        + " повторная установка компонента выполняется только по вашему нажатию.")
                .setNegativeButton("Отмена", null)
                .setPositiveButton(
                    "Открыть настройки",
                    (d, w) -> {
                      try {
                        startActivity(
                            new Intent(
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.parse("package:" + PKG)));
                      } catch (Exception e) {
                        error("Не удалось открыть настройки «Безопасности».");
                      }
                    })
                .show());
    text(
        "Чтобы вернуться сюда: удерживайте значок Task Center → «Настройка и откат». Удаление"
            + " самого Task Center не откатывает «Безопасность».",
        14,
        Color.DKGRAY);
  }

  void error(String s) {
    new AlertDialog.Builder(this)
        .setTitle("Task Center")
        .setMessage(s)
        .setPositiveButton("Понятно", null)
        .show();
  }

  void openTasks() {
    try {
      startActivity(
          new Intent()
              .setClassName(PKG, TARGET)
              .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP));
      finish();
    } catch (Exception e) {
      show();
      error("Не удалось открыть Automated tasks.");
    }
  }
}
