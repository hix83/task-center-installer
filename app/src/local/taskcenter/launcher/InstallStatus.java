// Copyright 2026 hix83 and contributors
// SPDX-License-Identifier: Apache-2.0
package local.taskcenter.launcher;

public final class InstallStatus {
  public static String name(int status) {
    switch (status) {
      case -1:
        return "PENDING_USER_ACTION";
      case 0:
        return "SUCCESS";
      case 1:
        return "FAILURE";
      case 2:
        return "FAILURE_BLOCKED";
      case 3:
        return "FAILURE_ABORTED";
      case 4:
        return "FAILURE_INVALID";
      case 5:
        return "FAILURE_CONFLICT";
      case 6:
        return "FAILURE_STORAGE";
      case 7:
        return "FAILURE_INCOMPATIBLE";
      case 8:
        return "FAILURE_TIMEOUT";
      default:
        return "UNKNOWN";
    }
  }

  public static boolean diagnosticAllowed(
      String model,
      int sdk,
      String sourceName,
      long sourceCode,
      String targetName,
      long targetCode) {
    return "23127PN0CG".equals(model)
        && sdk == 36
        && "12.6.1-260608.1.1".equals(sourceName)
        && sourceCode > 0
        && sourceCode <= targetCode
        && "12.8.3-260831.0.1".equals(targetName)
        && targetCode == 40001283L;
  }
}
