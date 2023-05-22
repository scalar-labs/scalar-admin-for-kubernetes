package com.scalar.admin.k8s;

import io.kubernetes.client.openapi.Configuration;
import io.kubernetes.client.openapi.apis.AppsV1Api;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.util.ClientBuilder;
import io.kubernetes.client.util.Config;
import io.kubernetes.client.util.KubeConfig;
import java.io.FileReader;
import java.nio.file.Paths;
import javax.annotation.Nullable;

class KubernetesClient {

  private final CoreV1Api coreV1Api;
  private final AppsV1Api appsV1Api;

  KubernetesClient(@Nullable String kubeConfigFilePath, @Nullable String kubeConfigContextName) {
    try {
      if (kubeConfigFilePath != null) {
        KubeConfig config = KubeConfig.loadKubeConfig(new FileReader(kubeConfigFilePath));

        if (kubeConfigContextName == null) {
          kubeConfigContextName = config.getCurrentContext();
        }

        if (!config.setContext(kubeConfigContextName)) {
          throw new RuntimeException(
              "Failed to set the context of the Kubernetes config: " + kubeConfigContextName);
        }

        Configuration.setDefaultApiClient(ClientBuilder.kubeconfig(config).build());
      } else if (kubeConfigContextName
          != null) { // user doesn't set `--kubeconfig` but set the `--kube-context`
        if (System.getenv("KUBECONFIG") != null) {
          kubeConfigFilePath = System.getenv("KUBECONFIG");
        } else {
          kubeConfigFilePath =
              Paths.get(System.getProperty("user.home"), ".kube", "config").toString();
        }

        KubeConfig config = KubeConfig.loadKubeConfig(new FileReader(kubeConfigFilePath));

        if (!config.setContext(kubeConfigContextName)) {
          throw new RuntimeException(
              "Failed to set the context of the Kubernetes config: " + kubeConfigContextName);
        }
        Configuration.setDefaultApiClient(ClientBuilder.kubeconfig(config).build());
      } else {
        Configuration.setDefaultApiClient(Config.defaultClient());
      }
    } catch (Exception e) {
      throw new RuntimeException("Failed to set default Kubernetes client: ", e);
    }

    coreV1Api = new CoreV1Api();
    appsV1Api = new AppsV1Api();
  }

  protected CoreV1Api getCoreV1Api() {
    return coreV1Api;
  }

  protected AppsV1Api getAppsV1Api() {
    return appsV1Api;
  }
}
