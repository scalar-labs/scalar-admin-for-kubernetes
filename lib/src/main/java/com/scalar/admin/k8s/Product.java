package com.scalar.admin.k8s;

enum Product {
  SCALARDB_SERVER {
    @Override
    String getAppLabelValue() {
      return "scalardb";
    }

    @Override
    String getAdminPortName() {
      return "scalardb";
    }
  },

  SCALARDB_CLUSTER {
    @Override
    String getAppLabelValue() {
      return "scalardb-cluster";
    }

    @Override
    String getAdminPortName() {
      return "scalardb-cluster";
    }
  },

  SCALARDL_LEDGER {
    @Override
    String getAppLabelValue() {
      return "ledger";
    }

    @Override
    String getAdminPortName() {
      return "scalardl-admin";
    }
  },

  SCALARDL_AUDITOR {
    @Override
    String getAppLabelValue() {
      return "auditor";
    }

    @Override
    String getAdminPortName() {
      return "scalardl-auditor-admin";
    }
  },

  UNKNOWN;

  String getAppLabelValue() {
    return "";
  }

  String getAdminPortName() {
    return "";
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
