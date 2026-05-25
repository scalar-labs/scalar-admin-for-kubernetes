package com.scalar.admin.kubernetes.domain.model.shared;

public enum Product {
  SCALARDB_SERVER("scalardb", "scalardb"),
  SCALARDB_CLUSTER("scalardb-cluster", "scalardb-cluster"),
  SCALARDL_LEDGER("ledger", "scalardl-admin"),
  SCALARDL_AUDITOR("auditor", "scalardl-auditor-admin"),
  UNKNOWN("", "");

  private final String appLabelValue;
  private final String adminPortName;

  private Product(String appLabelValue, String adminPortName) {
    this.appLabelValue = appLabelValue;
    this.adminPortName = adminPortName;
  }

  public String getAppLabelValue() {
    return appLabelValue;
  }

  public String getAdminPortName() {
    return adminPortName;
  }

  public static Product fromAppLabelValue(String appLabelValue) {
    return switch (appLabelValue) {
      case "scalardb" -> SCALARDB_SERVER;
      case "scalardb-cluster" -> SCALARDB_CLUSTER;
      case "ledger" -> SCALARDL_LEDGER;
      case "auditor" -> SCALARDL_AUDITOR;
      default -> UNKNOWN;
    };
  }
}
