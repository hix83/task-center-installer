// Copyright 2026 hix83 and contributors
// SPDX-License-Identifier: Apache-2.0

package local.taskcenter.launcher;

import android.content.*;
import android.database.*;
import android.net.Uri;
import android.os.*;
import android.provider.OpenableColumns;
import java.io.*;

public class ApkProvider extends ContentProvider {
  public boolean onCreate() {
    return true;
  }

  private File file(Uri u) throws FileNotFoundException {
    if (!"/security.apk".equals(u.getPath())) throw new FileNotFoundException();
    return new File(getContext().getCacheDir(), "security.apk");
  }

  public String getType(Uri u) {
    return "application/vnd.android.package-archive";
  }

  public ParcelFileDescriptor openFile(Uri u, String mode) throws FileNotFoundException {
    if (!"r".equals(mode)) throw new FileNotFoundException();
    return ParcelFileDescriptor.open(file(u), ParcelFileDescriptor.MODE_READ_ONLY);
  }

  public Cursor query(Uri u, String[] p, String s, String[] a, String o) {
    try {
      File f = file(u);
      String[] cols =
          p == null ? new String[] {OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE} : p;
      MatrixCursor c = new MatrixCursor(cols);
      Object[] row = new Object[cols.length];
      for (int i = 0; i < cols.length; i++)
        row[i] =
            OpenableColumns.DISPLAY_NAME.equals(cols[i])
                ? "Xiaomi-Security.apk"
                : OpenableColumns.SIZE.equals(cols[i]) ? f.length() : null;
      c.addRow(row);
      return c;
    } catch (Exception e) {
      return null;
    }
  }

  public Uri insert(Uri u, ContentValues v) {
    throw new UnsupportedOperationException();
  }

  public int delete(Uri u, String s, String[] a) {
    throw new UnsupportedOperationException();
  }

  public int update(Uri u, ContentValues v, String s, String[] a) {
    throw new UnsupportedOperationException();
  }
}
