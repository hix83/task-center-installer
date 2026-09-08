// SPDX-License-Identifier: Apache-2.0
package local.taskcenter.launcher;

import java.util.*;

public final class CatalogPolicyTest {
  static void check(boolean b, String m) {
    if (!b) throw new AssertionError(m);
  }

  static CatalogPolicy.Entry entry(long target) {
    CatalogPolicy.Entry e = new CatalogPolicy.Entry();
    e.packageName = CatalogPolicy.PACKAGE;
    e.versionCode = target;
    CatalogPolicy.Rule r = new CatalogPolicy.Rule();
    r.device = "klimt";
    r.model = "2506BPN68G";
    r.sdk = 37;
    r.incremental = "OS3.0.336.0.XOSRUXM";
    r.fromVersions = new long[] {40001275};
    e.rules.add(r);
    return e;
  }

  static CatalogPolicy.Device d(String device, int sdk, String build, long version) {
    return new CatalogPolicy.Device(device, "2506BPN68G", sdk, build, version);
  }

  public static void main(String[] args) {
    CatalogPolicy.Entry good = entry(40001283);
    List<CatalogPolicy.Entry> list = Arrays.asList(good);
    check(
        CatalogPolicy.select(list, d("klimt", 37, "OS3.0.336.0.XOSRUXM", 40001275)) == good,
        "verified configuration");
    check(
        CatalogPolicy.select(list, d("houji", 36, "OS3.0.336.0.XOSRUXM", 40001275)) == null,
        "unverified Xiaomi 14");
    check(
        CatalogPolicy.select(list, d("klimt", 36, "OS3.0.336.0.XOSRUXM", 40001275)) == null,
        "different Android");
    check(
        CatalogPolicy.select(list, d("klimt", 37, "OS3.0.337.0.XOSRUXM", 40001275)) == null,
        "different firmware");
    check(
        CatalogPolicy.select(list, d("klimt", 37, "OS3.0.336.0.XOSRUXM", 40001274)) == null,
        "unverified source version");
    check(
        CatalogPolicy.select(list, d("klimt", 37, "OS3.0.336.0.XOSRUXM", 40001300)) == null,
        "no downgrade");
    check(
        CatalogPolicy.select(
                Arrays.asList(entry(40001270)), d("klimt", 37, "OS3.0.336.0.XOSRUXM", 40001275))
            == null,
        "deny downgrade even if rule matches");
    CatalogPolicy.Entry newest = entry(40001290);
    check(
        CatalogPolicy.select(
                Arrays.asList(good, newest), d("klimt", 37, "OS3.0.336.0.XOSRUXM", 40001275))
            == newest,
        "prefer latest verified");
    good.packageName = "other";
    check(
        CatalogPolicy.select(list, d("klimt", 37, "OS3.0.336.0.XOSRUXM", 40001275)) == null,
        "different package");
    System.out.println("PASS: 9 compatibility cases");
  }
}
