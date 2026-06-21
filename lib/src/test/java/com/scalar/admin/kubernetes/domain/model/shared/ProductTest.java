package com.scalar.admin.kubernetes.domain.model.shared;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

public class ProductTest {

  @Nested
  @DisplayName("fromAppLabelValue()")
  class FromAppLabelValue {

    @Nested
    @DisplayName("when given app label values")
    class WhenGivenAppLabelValues {

      @Test
      @DisplayName("returns correct product for each value")
      void returnsCorrectProductForEachValue() {
        // Act & Assert
        assertThat(
                new Product[] {
                  Product.fromAppLabelValue("scalardb-cluster"),
                  Product.fromAppLabelValue("ledger"),
                  Product.fromAppLabelValue("auditor"),
                  Product.fromAppLabelValue("scalardb"),
                  Product.fromAppLabelValue("unknown"),
                  Product.fromAppLabelValue("foo"),
                  Product.fromAppLabelValue("bar"),
                })
            .containsExactly(
                Product.SCALARDB_CLUSTER,
                Product.SCALARDL_LEDGER,
                Product.SCALARDL_AUDITOR,
                Product.UNKNOWN,
                Product.UNKNOWN,
                Product.UNKNOWN,
                Product.UNKNOWN);
      }
    }
  }

  @Nested
  @DisplayName("values()")
  class Values {

    @Nested
    @DisplayName("when called")
    class WhenCalled {

      @Test
      @DisplayName("returns all product enum values")
      void returnsAllProductEnumValues() {
        // Act & Assert
        assertThat(Product.values())
            .containsExactly(
                Product.SCALARDB_CLUSTER,
                Product.SCALARDL_LEDGER,
                Product.SCALARDL_AUDITOR,
                Product.UNKNOWN);
      }
    }
  }

  @Nested
  @DisplayName("getAppLabelValue()")
  class GetAppLabelValue {

    @Nested
    @DisplayName("when called on each product")
    class WhenCalledOnEachProduct {

      @Test
      @DisplayName("returns correct app label value for each product")
      void returnsCorrectAppLabelValueForEachProduct() {
        // Act & Assert
        assertThat(
                new String[] {
                  Product.SCALARDB_CLUSTER.getAppLabelValue(),
                  Product.SCALARDL_LEDGER.getAppLabelValue(),
                  Product.SCALARDL_AUDITOR.getAppLabelValue(),
                  Product.UNKNOWN.getAppLabelValue()
                })
            .containsExactly("scalardb-cluster", "ledger", "auditor", "");
      }
    }
  }

  @Nested
  @DisplayName("getAdminPortName()")
  class GetAdminPortName {

    @Nested
    @DisplayName("when called on each product")
    class WhenCalledOnEachProduct {

      @Test
      @DisplayName("returns correct admin port name for each product")
      void returnsCorrectAdminPortNameForEachProduct() {
        // Act & Assert
        assertThat(
                new String[] {
                  Product.SCALARDB_CLUSTER.getAdminPortName(),
                  Product.SCALARDL_LEDGER.getAdminPortName(),
                  Product.SCALARDL_AUDITOR.getAdminPortName(),
                  Product.UNKNOWN.getAdminPortName()
                })
            .containsExactly("scalardb-cluster", "scalardl-admin", "scalardl-auditor-admin", "");
      }
    }
  }
}
