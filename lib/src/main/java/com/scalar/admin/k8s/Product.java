package com.scalar.admin.k8s;

import java.util.ArrayList;
import java.util.List;

class Product {

  private final String type;
  private final String labelAppValue;

  private Product(String type, String labelAppValue) {
    this.type = type;
    this.labelAppValue = labelAppValue;
  }

  static Product fromLabelAppValue(String labelAppValue) {
    switch (labelAppValue) {
      case "scalardb":
        return new Product("scalardb", "scalardb");
      case "scalardb-cluster":
        return new Product("scalardb-cluster", "scalardb-cluster");
      case "ledger":
        return new Product("scalardl-ledger", "ledger");
      case "auditor":
        return new Product("scalardl-auditor", "auditor");
      default:
        throw new IllegalArgumentException("Invalid label app value: " + labelAppValue);
    }
  }

  static Product fromType(String type) {
    switch (type) {
      case "scalardb":
        return new Product("scalardb", "scalardb");
      case "scalardb-cluster":
        return new Product("scalardb-cluster", "scalardb-cluster");
      case "scalardl-ledger":
        return new Product("scalardl-ledger", "ledger");
      case "scalardl-auditor":
        return new Product("scalardl-auditor", "auditor");
      default:
        throw new IllegalArgumentException("Invalid product type: " + type);
    }
  }

  Integer getDefaultAdminPort() {
    switch (type) {
      case "scalardb":
        return 60051;
      case "scalardb-cluster":
        return 60051;
      case "scalardl-ledger":
        return 50053;
      case "scalardl-auditor":
        return 40053;
      default:
        throw new IllegalArgumentException("Invalid product type: " + type);
    }
  }

  String getHelmChartName() {
    switch (type) {
      case "scalardb":
        return "scalardb";
      case "scalardb-cluster":
        return "scalardb-cluster";
      case "scalardl-ledger":
        return "scalardl";
      case "scalardl-auditor":
        return "scalardl-audit";
      default:
        throw new IllegalArgumentException("Invalid product type: " + type);
    }
  }

  String composeDeploymentName(String helmReleaseName) {
    String chartName = getHelmChartName();

    String fullname =
        (helmReleaseName.contains(chartName)) ? helmReleaseName : helmReleaseName + "-" + chartName;

    if (fullname.length() > 63) {
      fullname = fullname.substring(0, 63);
    }

    while (fullname.endsWith("-")) {
      fullname = fullname.substring(0, fullname.length() - 1);
    } // don't worry about empty name because helmReleaseName has to be started with alphanumeric

    switch (type) {
      case "scalardb":
        return fullname;
      case "scalardb-cluster":
        return fullname + "-node";
      case "scalardl-ledger":
        return fullname + "-ledger";
      case "scalardl-auditor":
        return fullname + "-auditor";
      default:
        throw new IllegalArgumentException("Invalid product type: " + type);
    }
  }

  String getType() {
    return type;
  }

  String getLabelAppValue() {
    return labelAppValue;
  }

  static List<String> allLabelAppValues() {
    return new ArrayList<String>() {
      {
        add("scalardb");
        add("scalardb-cluster");
        add("ledger");
        add("auditor");
      }
    };
  }
}
