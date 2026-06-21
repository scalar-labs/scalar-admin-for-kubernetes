package com.scalar.admin.kubernetes.domain.model.pause;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class TlsConfigTest {

  @Nested
  @DisplayName("Constructor")
  class Constructor {

    @Nested
    @DisplayName("when given valid parameters")
    class WhenGivenValidParameters {

      @Test
      @DisplayName("creates TlsConfig successfully")
      void createsTlsConfigSuccessfully() {
        // Arrange & Act
        TlsConfig config = new TlsConfig("cert-content", "authority");

        // Assert
        assertThat(config.caRootCert()).isEqualTo("cert-content");
        assertThat(config.overrideAuthority()).isEqualTo("authority");
      }
    }

    @Nested
    @DisplayName("when caRootCert is invalid")
    class WhenCaRootCertIsInvalid {

      @ParameterizedTest
      @NullAndEmptySource
      @ValueSource(strings = {"  ", "   "})
      @DisplayName("throws IllegalArgumentException")
      void throwsIllegalArgumentException(String invalidCaRootCert) {
        // Arrange & Act & Assert
        assertThatThrownBy(() -> new TlsConfig(invalidCaRootCert, "authority"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("caRootCert is required for TLS configuration");
      }
    }

    @Nested
    @DisplayName("when overrideAuthority is invalid")
    class WhenOverrideAuthorityIsInvalid {

      @ParameterizedTest
      @NullAndEmptySource
      @ValueSource(strings = {"  ", "   "})
      @DisplayName("throws IllegalArgumentException")
      void throwsIllegalArgumentException(String invalidOverrideAuthority) {
        // Arrange & Act & Assert
        assertThatThrownBy(() -> new TlsConfig("cert-content", invalidOverrideAuthority))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("overrideAuthority is required for TLS configuration");
      }
    }
  }
}
