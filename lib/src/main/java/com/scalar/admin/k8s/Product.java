package com.scalar.admin.k8s;

enum Product {
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

  String getAppLabelValue() {
    return appLabelValue;
  }

  String getAdminPortName() {
    return adminPortName;
  }

  static Product fromAppLabelValue(String appLabelValue) {
    for (Product product : Product.values()) {
      if (product.getAppLabelValue().equals(appLabelValue)) {
        return product;
      }
    }

    return UNKNOWN;
  }
}
