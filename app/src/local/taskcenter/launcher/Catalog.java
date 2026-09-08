// Copyright 2026 hix83 and contributors
// SPDX-License-Identifier: Apache-2.0
package local.taskcenter.launcher;

import android.content.Context;
import android.util.AtomicFile;
import android.util.Base64;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.X509EncodedKeySpec;
import java.util.*;
import org.json.*;

final class Catalog {
  static final String URL =
      "https://raw.githubusercontent.com/hix83/task-center-installer/main/catalog/catalog.signed.json";
  static final int MAX = 131072;
  final long revision;
  final List<CatalogPolicy.Entry> entries;

  Catalog(long r, List<CatalogPolicy.Entry> e) {
    revision = r;
    entries = e;
  }

  static Catalog parse(byte[] envelope) throws Exception {
    if (envelope.length > MAX) throw new IOException("Каталог слишком большой");
    JSONObject outer = new JSONObject(new String(envelope, StandardCharsets.UTF_8));
    byte[] payload = Base64.decode(outer.getString("payload"), Base64.NO_WRAP);
    byte[] sig = Base64.decode(outer.getString("signature"), Base64.NO_WRAP);
    Signature verify = Signature.getInstance("SHA256withRSA");
    verify.initVerify(
        KeyFactory.getInstance("RSA")
            .generatePublic(
                new X509EncodedKeySpec(Base64.decode(CatalogKey.PUBLIC_KEY, Base64.NO_WRAP))));
    verify.update(payload);
    if (!verify.verify(sig)) throw new IOException("Неверная подпись каталога");
    JSONObject root = new JSONObject(new String(payload, StandardCharsets.UTF_8));
    if (root.getInt("schema") != 1 || root.getLong("revision") < 1)
      throw new IOException("Неподдерживаемый каталог");
    List<CatalogPolicy.Entry> entries = new ArrayList<>();
    JSONArray releases = root.getJSONArray("releases");
    Set<String> ids = new HashSet<>();
    for (int i = 0; i < releases.length(); i++) {
      JSONObject o = releases.getJSONObject(i);
      CatalogPolicy.Entry e = new CatalogPolicy.Entry();
      e.id = o.getString("id");
      e.versionName = o.getString("versionName");
      e.versionCode = o.getLong("versionCode");
      e.packageName = o.getString("packageName");
      e.url = o.getString("url");
      e.sha256 = o.getString("sha256");
      e.size = o.getLong("size");
      if (!ids.add(e.id)
          || !CatalogPolicy.PACKAGE.equals(e.packageName)
          || e.versionCode <= 0
          || e.size <= 0
          || e.size > 200L * 1024 * 1024
          || !e.sha256.matches("[a-f0-9]{64}")
          || !e.url.startsWith("https://github.com/hix83/task-center-installer/releases/download/"))
        throw new IOException("Некорректная запись каталога");
      JSONArray rules = o.getJSONArray("rules");
      for (int j = 0; j < rules.length(); j++) {
        JSONObject x = rules.getJSONObject(j);
        CatalogPolicy.Rule r = new CatalogPolicy.Rule();
        r.device = x.getString("device");
        r.model = x.getString("model");
        r.sdk = x.getInt("sdk");
        r.incremental = x.getString("incremental");
        r.evidence = x.getString("evidence");
        if (r.device.isEmpty() || r.model.isEmpty() || r.incremental.isEmpty() || r.sdk < 28)
          throw new IOException("Неполное правило совместимости");
        JSONArray from = x.getJSONArray("fromVersionCodes");
        r.fromVersions = new long[from.length()];
        for (int k = 0; k < from.length(); k++) {
          r.fromVersions[k] = from.getLong(k);
          if (r.fromVersions[k] <= 0 || r.fromVersions[k] > e.versionCode)
            throw new IOException("Некорректная исходная версия");
        }
        e.rules.add(r);
      }
      entries.add(e);
    }
    return new Catalog(root.getLong("revision"), entries);
  }

  static byte[] read(InputStream in) throws IOException {
    try (InputStream stream = in;
        ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      byte[] b = new byte[8192];
      int n;
      while ((n = stream.read(b)) != -1) {
        if (out.size() + n > MAX) throw new IOException("Каталог слишком большой");
        out.write(b, 0, n);
      }
      return out.toByteArray();
    }
  }

  static Catalog local(Context c) throws Exception {
    Catalog bundled = parse(read(c.getAssets().open("catalog.signed.json")));
    try {
      Catalog cached =
          parse(read(new AtomicFile(new File(c.getFilesDir(), "catalog.json")).openRead()));
      return cached.revision >= bundled.revision ? cached : bundled;
    } catch (Exception ignored) {
      return bundled;
    }
  }

  static Catalog refresh(Context c, Downloads network, long minimum) throws Exception {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    network.copy(URL, bytes, MAX, -1, null);
    byte[] data = bytes.toByteArray();
    Catalog result = parse(data);
    if (result.revision < minimum) throw new IOException("Сервер вернул устаревший каталог");
    AtomicFile file = new AtomicFile(new File(c.getFilesDir(), "catalog.json"));
    FileOutputStream out = null;
    try {
      out = file.startWrite();
      out.write(data);
      file.finishWrite(out);
    } catch (Exception e) {
      if (out != null) file.failWrite(out);
      throw e;
    }
    return result;
  }
}
