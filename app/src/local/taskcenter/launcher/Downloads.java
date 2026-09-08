// Copyright 2026 hix83 and contributors
// SPDX-License-Identifier: Apache-2.0
package local.taskcenter.launcher;

import java.io.*;
import java.net.*;
import javax.net.ssl.HttpsURLConnection;

final class Downloads {
  interface Progress {
    void update(long bytes);
  }

  volatile boolean cancelled;
  volatile HttpsURLConnection active;

  void cancel() {
    cancelled = true;
    HttpsURLConnection c = active;
    if (c != null) c.disconnect();
  }

  static boolean allowed(URL u) {
    if (!"https".equals(u.getProtocol())
        || (u.getPort() != -1 && u.getPort() != 443)
        || u.getUserInfo() != null) return false;
    String h = u.getHost();
    return h.equals("github.com")
        || h.equals("raw.githubusercontent.com")
        || h.equals("release-assets.githubusercontent.com")
        || h.equals("objects.githubusercontent.com");
  }

  void copy(String address, OutputStream out, long max, long exact, Progress progress)
      throws IOException {
    URL u = new URL(address);
    for (int redirect = 0; redirect < 6; redirect++) {
      if (cancelled) throw new IOException("Скачивание отменено");
      if (!allowed(u)) throw new IOException("Недопустимый адрес скачивания");
      HttpsURLConnection c = (HttpsURLConnection) u.openConnection();
      active = c;
      c.setInstanceFollowRedirects(false);
      c.setConnectTimeout(15000);
      c.setReadTimeout(15000);
      c.setRequestProperty("User-Agent", "TaskCenter/3.0");
      c.setRequestProperty("Accept-Encoding", "identity");
      try {
        int status = c.getResponseCode();
        if (status == 301 || status == 302 || status == 303 || status == 307 || status == 308) {
          String next = c.getHeaderField("Location");
          if (next == null) throw new IOException("Нет адреса перенаправления");
          u = new URL(u, next);
          continue;
        }
        if (status != 200) throw new IOException("Сервер вернул HTTP " + status);
        long declared = c.getContentLengthLong();
        if (declared > max || (exact >= 0 && declared >= 0 && declared != exact))
          throw new IOException("Неожиданный размер ответа");
        long total = 0, last = 0;
        try (InputStream in = c.getInputStream()) {
          byte[] buf = new byte[65536];
          int n;
          while ((n = in.read(buf)) != -1) {
            if (cancelled) throw new IOException("Скачивание отменено");
            total += n;
            if (total > max) throw new IOException("Превышен размер файла");
            out.write(buf, 0, n);
            long now = System.currentTimeMillis();
            if (progress != null && now - last > 300) {
              progress.update(total);
              last = now;
            }
          }
        }
        if (exact >= 0 && total != exact) throw new IOException("Файл скачан не полностью");
        if (cancelled) throw new IOException("Скачивание отменено");
        return;
      } finally {
        c.disconnect();
        active = null;
      }
    }
    throw new IOException("Слишком много перенаправлений");
  }
}
