package local.taskcenter.launcher;

public final class InstallStatusTest {
  static void check(boolean value) {
    if (!value) throw new AssertionError();
  }

  public static void main(String[] args) {
    check("SUCCESS".equals(InstallStatus.name(0)));
    check("PENDING_USER_ACTION".equals(InstallStatus.name(-1)));
    check("UNKNOWN".equals(InstallStatus.name(999)));
    check(!"SUCCESS".equals(InstallStatus.name(Integer.MIN_VALUE)));
    check(
        InstallStatus.diagnosticAllowed(
            "23127PN0CG", 36, "12.6.1-260608.1.1", 40001261, "12.8.3-260831.0.1", 40001283));
    check(
        !InstallStatus.diagnosticAllowed(
            "other", 36, "12.6.1-260608.1.1", 40001261, "12.8.3-260831.0.1", 40001283));
    check(
        !InstallStatus.diagnosticAllowed(
            "23127PN0CG", 35, "12.6.1-260608.1.1", 40001261, "12.8.3-260831.0.1", 40001283));
    check(
        !InstallStatus.diagnosticAllowed(
            "23127PN0CG", 36, "12.6.1-260608.1.1", 40001284, "12.8.3-260831.0.1", 40001283));
    check(
        !InstallStatus.diagnosticAllowed(
            "23127PN0CG", 36, "other", 40001261, "12.8.3-260831.0.1", 40001283));
    check(
        !InstallStatus.diagnosticAllowed(
            "23127PN0CG", 36, "12.6.1-260608.1.1", 40001261, "other", 40001283));
    System.out.println("PASS: installation status and diagnostic eligibility (10 checks)");
  }
}
