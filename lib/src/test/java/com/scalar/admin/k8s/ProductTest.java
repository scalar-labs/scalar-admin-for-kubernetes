package com.scalar.admin.k8s;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import org.junit.jupiter.api.Test;

public class ProductTest {

  @Test
  public void fromAppLabelValue_shouldReturnCorrectValue() {
    // Act && Assert
    assertArrayEquals(
        new Product[] {
          Product.SCALARDB_SERVER,
          Product.SCALARDB_CLUSTER,
          Product.SCALARDL_LEDGER,
          Product.SCALARDL_AUDITOR,
          Product.UNKNOWN,
          Product.UNKNOWN,
          Product.UNKNOWN,
        },
        new Product[] {
          Product.fromAppLabelValue("scalardb"),
          Product.fromAppLabelValue("scalardb-cluster"),
          Product.fromAppLabelValue("ledger"),
          Product.fromAppLabelValue("auditor"),
          Product.fromAppLabelValue("unknown"),
          Product.fromAppLabelValue("foo"),
          Product.fromAppLabelValue("bar"),
        });
  }

  @Test
  public void values_shouldReturnAllProducts() {
    // Act && Assert
    assertArrayEquals(
        new Product[] {
          Product.SCALARDB_SERVER,
          Product.SCALARDB_CLUSTER,
          Product.SCALARDL_LEDGER,
          Product.SCALARDL_AUDITOR,
          Product.UNKNOWN
        },
        Product.values());
  }

  @Test
  public void getAppLabelValue_shouldReturnCorrectValue() {
    // Act && Assert
    assertArrayEquals(
        new String[] {"scalardb", "scalardb-cluster", "ledger", "auditor", ""},
        new String[] {
          Product.SCALARDB_SERVER.getAppLabelValue(),
          Product.SCALARDB_CLUSTER.getAppLabelValue(),
          Product.SCALARDL_LEDGER.getAppLabelValue(),
          Product.SCALARDL_AUDITOR.getAppLabelValue(),
          Product.UNKNOWN.getAppLabelValue()
        });
  }

  @Test
  public void getAdminPortName_shouldReturnCorrectValue() {
    // Act && Assert
    assertArrayEquals(
        new String[] {
          "scalardb", "scalardb-cluster", "scalardl-admin", "scalardl-auditor-admin", ""
        },
        new String[] {
          Product.SCALARDB_SERVER.getAdminPortName(),
          Product.SCALARDB_CLUSTER.getAdminPortName(),
          Product.SCALARDL_LEDGER.getAdminPortName(),
          Product.SCALARDL_AUDITOR.getAdminPortName(),
          Product.UNKNOWN.getAdminPortName()
        });
  }
}
