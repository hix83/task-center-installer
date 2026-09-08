// Copyright 2026 hix83 and contributors
// SPDX-License-Identifier: Apache-2.0
package local.taskcenter.launcher;

import java.util.*;

/** Exact, evidence-based matching. No model-family or Android-version guesses. */
public final class CatalogPolicy {
  public static final String PACKAGE = "com.miui.securitycenter";
  public static final String CERT =
      "c9009d01ebf9f5d0302bc71b2fe9aa9a47a432bba17308a3111b75d7b2149025";

  public static final class Device {
    public final String device, model, incremental;
    public final int sdk;
    public final long installedVersion;

    public Device(String d, String m, int s, String i, long v) {
      device = d;
      model = m;
      sdk = s;
      incremental = i;
      installedVersion = v;
    }
  }

  public static final class Rule {
    public String device, model, incremental, evidence;
    public int sdk;
    public long[] fromVersions;

    boolean matches(Device d) {
      if (!device.equals(d.device)
          || !model.equals(d.model)
          || sdk != d.sdk
          || !incremental.equals(d.incremental)) return false;
      for (long v : fromVersions) if (v == d.installedVersion) return true;
      return false;
    }
  }

  public static final class Entry {
    public String id, versionName, packageName, url, sha256;
    public long versionCode, size;
    public List<Rule> rules = new ArrayList<>();
  }

  public static Entry select(List<Entry> entries, Device d) {
    Entry best = null;
    for (Entry e : entries) {
      if (!PACKAGE.equals(e.packageName) || e.versionCode < d.installedVersion) continue;
      for (Rule r : e.rules)
        if (r.matches(d) && (best == null || e.versionCode > best.versionCode)) {
          best = e;
          break;
        }
    }
    return best;
  }

  public static String evidence(Entry e, Device d) {
    for (Rule r : e.rules) if (r.matches(d)) return r.evidence;
    return "";
  }
}
