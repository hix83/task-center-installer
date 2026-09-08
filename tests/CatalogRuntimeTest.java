// SPDX-License-Identifier: Apache-2.0
package local.taskcenter.launcher;

import android.app.Instrumentation;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import java.io.*;
import java.net.URL;
import org.json.JSONObject;

/** Explicitly invoked device tests. Not shipped in the release APK. */
public class CatalogRuntimeTest extends Instrumentation {
  Bundle args;

  public void onCreate(Bundle b) {
    super.onCreate(b);
    args = b;
    start();
  }

  void check(boolean ok, String message) {
    if (!ok) throw new AssertionError(message);
  }

  public void onStart() {
    Bundle result = new Bundle();
    File download = null;
    try {
      byte[] signed = Catalog.read(getTargetContext().getAssets().open("catalog.signed.json"));
      Catalog catalog = Catalog.parse(signed);
      check(catalog.revision >= 1, "valid signature");
      JSONObject tampered = new JSONObject(new String(signed, "UTF-8"));
      byte[] body = android.util.Base64.decode(tampered.getString("payload"), 0);
      body[0] ^= 1;
      tampered.put(
          "payload", android.util.Base64.encodeToString(body, android.util.Base64.NO_WRAP));
      boolean rejected = false;
      try {
        Catalog.parse(tampered.toString().getBytes("UTF-8"));
      } catch (Exception e) {
        rejected = true;
      }
      check(rejected, "tampered catalog must fail");
      check(!Downloads.allowed(new URL("http://github.com/test")), "reject HTTP");
      check(
          !Downloads.allowed(new URL("https://github.com.evil.example/test")),
          "reject lookalike host");
      check(!Downloads.allowed(new URL("https://user@github.com/test")), "reject credentials");
      CatalogPolicy.Entry e =
          CatalogPolicy.select(
              catalog.entries,
              new CatalogPolicy.Device("klimt", "2506BPN68G", 37, "OS3.0.336.0.XOSRUXM", 40001275));
      check(e != null, "select known stock configuration");
      if (args != null && "true".equals(args.getString("network"))) {
        Bundle progress = new Bundle();
        progress.putString("stream", "Downloading verified Xiaomi APK from release asset…\n");
        sendStatus(1, progress);
        download = File.createTempFile("catalog-test-", ".apk", getTargetContext().getCacheDir());
        try (OutputStream out = new FileOutputStream(download)) {
          new Downloads().copy(e.url, out, e.size, e.size, null);
        }
        check(MainActivity.hash(download).equals(e.sha256), "download SHA-256");
        PackageInfo p =
            getTargetContext()
                .getPackageManager()
                .getPackageArchiveInfo(download.getPath(), PackageManager.GET_SIGNING_CERTIFICATES);
        check(
            p != null
                && e.packageName.equals(p.packageName)
                && p.getLongVersionCode() == e.versionCode,
            "archive package and version");
        check(MainActivity.signed(p), "pinned Xiaomi signing certificate");
      }
      if (args != null && "true".equals(args.getString("catalog"))) {
        Catalog refreshed = Catalog.refresh(getTargetContext(), new Downloads(), catalog.revision);
        check(refreshed.revision >= catalog.revision, "remote catalog revision");
        boolean oldRejected = false;
        try {
          Catalog.refresh(getTargetContext(), new Downloads(), refreshed.revision + 1);
        } catch (Exception expected) {
          oldRejected = true;
        }
        check(oldRejected, "reject older remote revision");
        Bundle progress = new Bundle();
        progress.putString("stream", "PASS: remote catalog update and older revision rejection\n");
        sendStatus(1, progress);
      }
      result.putString(
          "stream",
          "PASS: signed catalog, tamper rejection, HTTPS/host validation, matching"
              + ((args != null && "true".equals(args.getString("network")))
                  ? ", real APK download/hash/package/version/signature"
                  : "")
              + "\n");
      finish(-1, result);
    } catch (Throwable t) {
      result.putString("stream", "FAIL: " + t.toString() + "\n");
      finish(1, result);
    } finally {
      if (download != null) download.delete();
    }
  }
}
