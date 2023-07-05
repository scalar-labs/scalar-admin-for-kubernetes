package com.scalar.admin.k8s;

import javax.annotation.Nullable;

public class Pauser {

  private final String namespace;
  private final String helmReleaseName;
  private final String productType;
  private final Integer adminPort;
  private final KubernetesClient k8sClient;
  private final InternalPauser internalPauser;

  /**
   * @param namespace The namespace where the pods are deployed. `default` is used if this parameter
   *     is null.
   * @param helmReleaseName The Helm release name used to deploy the pods. This parameter can not be
   *     null.
   * @param kubeConfigFilePath The path to the kubeconfig file that can be used to connect to the
   *     cluster. If null, then the `KUBECONFIG` environment variable and `~/.kube/config` file are
   *     used in order.
   * @param kubeConfigContextName The context name in the kubeconfig file. `current-context` is used
   *     if null.
   * @param productType The Scalar product type of the pods. If null, then Pauser will try to figure
   *     out by itself.
   * @param adminPort The admin port of the pods. If null, the default admin port of the product
   *     type is used.
   * @param inCluster Pass true if the Pauser is running in the cluster. Otherwise, false.
   */
  public Pauser(
      @Nullable String namespace,
      String helmReleaseName,
      @Nullable String kubeConfigFilePath,
      @Nullable String kubeConfigContextName,
      @Nullable String productType,
      @Nullable Integer adminPort,
      boolean inCluster) {

    if (helmReleaseName == null) {
      throw new IllegalArgumentException("helmReleaseName is required");
    }

    this.helmReleaseName = helmReleaseName;
    this.namespace = namespace != null ? namespace : "default";
    this.productType = productType;
    this.adminPort = adminPort;

    if (inCluster) {
      kubeConfigFilePath = null;
      kubeConfigContextName = null;
    }

    k8sClient = new KubernetesClient(kubeConfigFilePath, kubeConfigContextName);

    internalPauser =
        (inCluster) ? new InClusterPauser() : new OutClusterPauser(k8sClient, this.namespace);
  }

  /**
   * @param pauseDuration The duration to pause in seconds.
   * @return 0 if the pause operation is successful. Otherwise, 1.
   */
  public int pause(Integer pauseDuration) {
    if (pauseDuration == null) {
      throw new IllegalArgumentException("pauseDuration is required");
    }

    TargetPods targetPods =
        TargetPods.findTargetPods(k8sClient, namespace, helmReleaseName, productType, adminPort);

    if (targetPods.getPods().size() == 0) {
      System.out.println("No Scalar pods found. Nothing to pause.");
      return 1;
    }

    PausedDuration pausedDuration =
        internalPauser.pause(targetPods.getPods(), targetPods.getAdminPort(), pauseDuration);

    if (targetPods.isUpdated()) {
      System.out.println(
          "Pause operation failed because the target pods is updated between the duration. Please"
              + " retry this command.");
      return 1;
    }

    System.out.println(
        "Paused sucessfully. Duration: from "
            + pausedDuration.getStartAt().toString()
            + " to "
            + pausedDuration.getEndAt().toString());

    return 0;
  }
}
