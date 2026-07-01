package com.scalar.admin.kubernetes.domain.model.pause;

import javax.annotation.Nullable;

/**
 * Enum representing the mode for discovering target pods to pause.
 *
 * <p>This enum defines how the system identifies which pods should be paused. Different modes
 * provide different strategies for pod discovery in a Kubernetes cluster.
 */
public enum PodDiscoveryMode {
  /**
   * Discover pods by Helm release name.
   *
   * <p>In this mode, the system identifies pods by looking for resources labeled with the
   * specified Helm release name. This is the default and original behavior.
   */
  HELM_RELEASE("helm-release"),

  /**
   * Discover pods by deployment name.
   *
   * <p>In this mode, the system identifies pods by the deployment name. The admin port must be
   * explicitly specified as it cannot be auto-detected.
   */
  DEPLOYMENT("deployment");

  private final String value;

  PodDiscoveryMode(String value) {
    this.value = value;
  }

  /**
   * Returns the string value of this mode.
   *
   * @return the string value (e.g., "helm-release", "deployment")
   */
  public String getValue() {
    return value;
  }

  /**
   * Converts a string value to PodDiscoveryMode enum.
   *
   * <p>This method uses case-insensitive matching for better user experience, consistent with
   * picocli's setCaseInsensitiveEnumValuesAllowed(true) setting used in the CLI layer.
   *
   * @param value the string value (case-insensitive)
   * @return the corresponding PodDiscoveryMode
   * @throws IllegalArgumentException if the value is invalid
   */
  public static PodDiscoveryMode fromValue(String value) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("podDiscoveryMode value cannot be null or blank");
    }

    // Case-insensitive comparison for consistency with CLI's setCaseInsensitiveEnumValuesAllowed
    for (PodDiscoveryMode mode : values()) {
      if (mode.value.equalsIgnoreCase(value)) {
        return mode;
      }
    }

    throw new IllegalArgumentException(
        "Invalid podDiscoveryMode: " + value + ". Valid values are: helm-release, deployment");
  }

  /**
   * Validates the required parameters for this discovery mode.
   *
   * @param helmReleaseName the Helm release name (required for HELM_RELEASE mode)
   * @param deploymentName the deployment name (required for DEPLOYMENT mode)
   * @param adminPort the admin port (required for DEPLOYMENT mode)
   * @throws IllegalArgumentException if required parameters are missing or invalid for this mode
   */
  public void validate(
      @Nullable String helmReleaseName,
      @Nullable String deploymentName,
      @Nullable Integer adminPort) {
    var _exhaustiveCheck = switch (this) {
      case HELM_RELEASE -> {
        if (helmReleaseName == null || helmReleaseName.isBlank()) {
          throw new IllegalArgumentException(
              "helmReleaseName is required when podDiscoveryMode is HELM_RELEASE");
        }
        if (deploymentName != null || adminPort != null) {
          throw new IllegalArgumentException(
              "deploymentName and adminPort cannot be used when podDiscoveryMode is HELM_RELEASE");
        }
        yield (Void) null;
      }
      case DEPLOYMENT -> {
        if (deploymentName == null || deploymentName.isBlank()) {
          throw new IllegalArgumentException(
              "deploymentName is required when podDiscoveryMode is DEPLOYMENT");
        }
        if (adminPort == null) {
          throw new IllegalArgumentException(
              "adminPort is required when podDiscoveryMode is DEPLOYMENT");
        }
        if (adminPort < 1 || adminPort > 65535) {
          throw new IllegalArgumentException(
              "adminPort must be between 1 and 65535 when podDiscoveryMode is DEPLOYMENT, but was: "
                  + adminPort);
        }
        if (helmReleaseName != null) {
          throw new IllegalArgumentException(
              "helmReleaseName cannot be used when podDiscoveryMode is DEPLOYMENT");
        }
        yield (Void) null;
      }
    };
  }
}
