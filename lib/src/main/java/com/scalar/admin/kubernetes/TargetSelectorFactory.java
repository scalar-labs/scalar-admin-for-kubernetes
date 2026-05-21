package com.scalar.admin.kubernetes;

import io.kubernetes.client.openapi.Configuration;
import io.kubernetes.client.openapi.apis.AppsV1Api;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.util.Config;
import java.io.IOException;

class TargetSelectorFactory {

  /**
   * Creates a TargetSelector for HELM_RELEASE mode. Initializes the Kubernetes client internally.
   */
  static TargetSelector fromHelmRelease(String namespace, String helmReleaseName)
      throws PauserException {
    initializeKubernetesClient();
    return new TargetSelector(new CoreV1Api(), new AppsV1Api(), namespace, helmReleaseName);
  }

  /**
   * Creates a TargetSelector for DEPLOYMENT mode. Initializes the Kubernetes client internally.
   */
  static TargetSelector fromDeployment(String namespace, String deploymentName, int adminPort)
      throws PauserException {
    initializeKubernetesClient();
    return new TargetSelector(new CoreV1Api(), new AppsV1Api(), namespace, deploymentName, adminPort);
  }

  private static void initializeKubernetesClient() throws PauserException {
    try {
      Configuration.setDefaultApiClient(Config.defaultClient());
    } catch (IOException e) {
      throw new PauserException("Failed to set default Kubernetes client.", e);
    }
  }
}
