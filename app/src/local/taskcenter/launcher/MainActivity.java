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
  Catalog catalog;
  Downloads network;
  String catalogNotice = "";
  LinearLayout box;
  TextView status;
  boolean busy = false, shown = false;

  int dp(int x) {
    return (int) (x * getResources().getDisplayMetrics().density);
  }

  public void onCreate(Bundle b) {
    super.onCreate(b);
    try {
      catalog = Catalog.local(this);
    } catch (Exception e) {
      catalogNotice = "Каталог недоступен: " + e.getMessage();
    }
    getWindow()
        .getDecorView()
        .setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
    if (!getIntent().getBooleanExtra("setup", false)
        && !"local.taskcenter.launcher.MANAGE".equals(getIntent().getAction())
        && (getPreferences(MODE_PRIVATE).getBoolean("setupSeen3", false)
            || "local.taskcenter.launcher.OPEN".equals(getIntent().getAction()))
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
      return a.enabled && a.exported && a.applicationInfo.enabled && signed(installed());
    } catch (Exception e) {
      return false;
    }
  }

  PackageInfo installed() throws Exception {
    return getPackageManager().getPackageInfo(PKG, PackageManager.GET_SIGNING_CERTIFICATES);
  }

  static boolean signed(PackageInfo p) throws Exception {
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
      button("Добавить ярлык на рабочий стол", () -> pinShortcut());
      button("Обновить каталог", () -> refreshCatalog());
      if (!catalogNotice.isEmpty()) text(catalogNotice, 14, Color.DKGRAY);
      button("Скопировать сведения об устройстве", () -> copyDiagnostics());
      rollbackSection();
      text(
          "Если значка нет на рабочем столе, перенесите Task Center из списка приложений.",
          14,
          Color.DKGRAY);
      return;
    }
    renderInstaller();
  }

  CatalogPolicy.Device device() throws Exception {
    return new CatalogPolicy.Device(
        Build.DEVICE,
        Build.MODEL,
        Build.VERSION.SDK_INT,
        Build.VERSION.INCREMENTAL,
        installed().getLongVersionCode());
  }

  String diagnostics() {
    String version = "не установлена";
    try {
      PackageInfo p = installed();
      version = p.versionName + " (" + p.getLongVersionCode() + ")";
    } catch (Exception ignored) {
    }
    return "Task Center 3.0\nМодель: "
        + Build.MANUFACTURER
        + " "
        + Build.MODEL
        + "\nУстройство: "
        + Build.DEVICE
        + "\nAndroid: "
        + Build.VERSION.RELEASE
        + " / API "
        + Build.VERSION.SDK_INT
        + "\nСборка: "
        + Build.VERSION.INCREMENTAL
        + "\nБезопасность: "
        + version
        + "\nКаталог: "
        + (catalog == null ? "нет" : catalog.revision);
  }

  void copyDiagnostics() {
    android.content.ClipboardManager cb =
        (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
    cb.setPrimaryClip(ClipData.newPlainText("Task Center — совместимость", diagnostics()));
    Toast.makeText(this, "Сведения скопированы — можно отправить разработчику", Toast.LENGTH_LONG)
        .show();
  }

  void renderInstaller() {
    text("Подбор компонента", 22, Color.rgb(22, 35, 55));
    text(diagnostics(), 14, Color.DKGRAY);
    if (!catalogNotice.isEmpty()) text(catalogNotice, 15, Color.DKGRAY);
    button("Обновить каталог", () -> refreshCatalog());
    CatalogPolicy.Entry selected = null;
    String reason = null;
    try {
      if (!signed(installed()))
        reason = "Подпись «Безопасности» не совпадает с проверенной подписью Xiaomi.";
      else if (catalog == null)
        reason =
            "Нужен проверенный каталог. Подключитесь к интернету и нажмите «Обновить каталог».";
      else {
        selected = CatalogPolicy.select(catalog.entries, device());
        if (selected == null)
          reason =
              "Для этой модели, сборки HyperOS и версии «Безопасности» пока нет подтверждённого"
                  + " обновления. Мы не будем устанавливать случайный APK. Обновите каталог или"
                  + " отправьте сведения разработчику.";
      }
    } catch (Exception e) {
      reason = "Не удалось проверить «Безопасность»: " + e.getMessage();
    }
    if (reason != null) text(reason, 17, Color.DKGRAY);
    else {
      final CatalogPolicy.Entry entry = selected;
      text("Доступна версия " + entry.versionName, 20, Color.rgb(28, 120, 80));
      try {
        text(CatalogPolicy.evidence(entry, device()), 15, Color.DKGRAY);
      } catch (Exception ignored) {
      }
      text(
          "Скачивание: "
              + (entry.size / 1024 / 1024)
              + " МБ. Обновится всё приложение «Безопасность». Интерфейс может стать английским,"
              + " настройки и другие функции могут измениться. Android попросит подтвердить"
              + " обновление.",
          16,
          Color.DKGRAY);
      button(
          getPackageManager().canRequestPackageInstalls()
              ? "Скачать и установить"
              : "Разрешить установку",
          () -> install(entry));
    }
    status =
        text(
            "Установка выполняется только после вашего нажатия. После системного подтверждения"
                + " вернитесь сюда.",
            15,
            Color.DKGRAY);
    button("Скопировать сведения об устройстве", () -> copyDiagnostics());
    rollbackSection();
    text(
        "Независимый установщик. Каталог и APK загружаются с GitHub. Серийный номер, аккаунты и"
            + " список приложений не отправляются. Компонент Xiaomi не входит в лицензию Apache 2.0"
            + " нашего кода.",
        13,
        Color.GRAY);
  }

  interface Work {
    void run() throws Exception;
  }

  void background(String message, Work work) {
    if (busy) return;
    busy = true;
    network = new Downloads();
    showBusy(message);
    new Thread(
            () -> {
              try {
                work.run();
              } catch (Exception e) {
                ui(
                    () -> {
                      busy = false;
                      show();
                      error(e.getMessage() == null ? "Операция не выполнена" : e.getMessage());
                    });
              }
            })
        .start();
  }

  void ui(Runnable r) {
    runOnUiThread(
        () -> {
          if (!isFinishing() && !isDestroyed()) r.run();
        });
  }

  void showBusy(String message) {
    box.removeAllViews();
    text("Task Center", 28, Color.rgb(22, 35, 55));
    status = text(message, 18, Color.DKGRAY);
    button(
        "Отменить",
        () -> {
          if (network != null) network.cancel();
          status.setText("Отмена…");
        });
  }

  protected void onDestroy() {
    if (network != null && busy) network.cancel();
    super.onDestroy();
  }

  void refreshCatalog() {
    background(
        "Получение и проверка каталога…",
        () -> {
          Catalog fresh = Catalog.refresh(this, network, catalog == null ? 1 : catalog.revision);
          ui(
              () -> {
                catalog = fresh;
                catalogNotice = "Каталог обновлён и подпись проверена.";
                busy = false;
                show();
              });
        });
  }

  static String hash(File f) throws Exception {
    MessageDigest md = MessageDigest.getInstance("SHA-256");
    try (InputStream in = new FileInputStream(f)) {
      byte[] b = new byte[65536];
      int n;
      while ((n = in.read(b)) != -1) md.update(b, 0, n);
    }
    StringBuilder hex = new StringBuilder();
    for (byte b : md.digest()) hex.append(String.format("%02x", b & 255));
    return hex.toString();
  }

  void install(CatalogPolicy.Entry entry) {
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
    background(
        "Скачивание «Безопасности»…",
        () -> {
          File part = File.createTempFile("security-", ".part", getCacheDir());
          try {
            try (OutputStream out = new FileOutputStream(part)) {
              network.copy(
                  entry.url,
                  out,
                  entry.size,
                  entry.size,
                  bytes ->
                      ui(() -> status.setText("Скачивание: " + (100 * bytes / entry.size) + "%")));
            }
            ui(() -> status.setText("Проверка APK…"));
            if (!entry.sha256.equals(hash(part)))
              throw new IOException("Контрольная сумма APK не совпала. Файл не будет установлен.");
            PackageInfo p =
                getPackageManager()
                    .getPackageArchiveInfo(part.getPath(), PackageManager.GET_SIGNING_CERTIFICATES);
            if (p == null
                || !PKG.equals(p.packageName)
                || p.getLongVersionCode() != entry.versionCode
                || !signed(p))
              throw new IOException("Подпись, пакет или версия скачанного APK не прошли проверку.");
            if (!signed(installed())
                || CatalogPolicy.select(java.util.Collections.singletonList(entry), device())
                    == null)
              throw new IOException("Версия на телефоне изменилась. Повторите подбор компонента.");
            if (network.cancelled) throw new IOException("Скачивание отменено");
            File f = new File(getCacheDir(), "security.apk");
            if (!part.renameTo(f))
              throw new IOException("Не удалось подготовить APK для установки");
            ui(
                () -> {
                  busy = false;
                  show();
                  Uri u = Uri.parse("content://local.taskcenter.launcher.apk/security.apk");
                  Intent i =
                      new Intent(Intent.ACTION_VIEW)
                          .setDataAndType(u, "application/vnd.android.package-archive")
                          .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                  i.setClipData(ClipData.newRawUri("APK", u));
                  try {
                    startActivity(i);
                  } catch (Exception e) {
                    error("Не удалось открыть системный установщик: " + e.getMessage());
                  }
                });
          } finally {
            part.delete();
          }
        });
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

  void pinShortcut() {
    ShortcutManager sm = getSystemService(ShortcutManager.class);
    if (sm == null || !sm.isRequestPinShortcutSupported()) {
      error(
          "Рабочий стол не поддерживает запрос ярлыка. Перенесите Task Center из списка"
              + " приложений.");
      return;
    }
    for (ShortcutInfo item : sm.getPinnedShortcuts())
      if ("task-center-direct".equals(item.getId())) {
        Toast.makeText(this, "Ярлык уже закреплён", Toast.LENGTH_LONG).show();
        return;
      }
    Intent i = new Intent(this, MainActivity.class).setAction("local.taskcenter.launcher.OPEN");
    ShortcutInfo shortcut =
        new ShortcutInfo.Builder(this, "task-center-direct")
            .setShortLabel("Task Center")
            .setIcon(
                android.graphics.drawable.Icon.createWithResource(this, getApplicationInfo().icon))
            .setIntent(i)
            .build();
    try {
      if (!sm.requestPinShortcut(shortcut, null)) error("Рабочий стол отклонил запрос ярлыка.");
    } catch (Exception e) {
      error("Не удалось запросить ярлык: " + e.getMessage());
    }
  }

  void openTasks() {
    try {
      getPreferences(MODE_PRIVATE).edit().putBoolean("setupSeen3", true).apply();
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
