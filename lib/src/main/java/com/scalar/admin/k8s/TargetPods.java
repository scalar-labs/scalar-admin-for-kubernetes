package com.scalar.admin.k8s;

import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1Deployment;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodList;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

class TargetPods {

  static final String LABEL_INSTANCE = "app.kubernetes.io/instance";
  static final String LABEL_APP = "app.kubernetes.io/app";

  private List<V1Pod> pods;
  private V1Deployment deployment;
  private final KubernetesClient k8sClient;
  private final String namespace;
  private final String helmReleaseName;
  private final Product product;
  private final Integer adminPort;

  private TargetPods(
      KubernetesClient k8sClient,
      String namespace,
      String helmReleaseName,
      Product product,
      Integer adminPort,
      List<V1Pod> pods,
      V1Deployment deployment) {
    this.k8sClient = k8sClient;
    this.namespace = namespace;
    this.helmReleaseName = helmReleaseName;
    this.product = product;
    this.adminPort = adminPort;
    this.pods = pods;
    this.deployment = deployment;
  }

  List<V1Pod> getPods() {
    return pods;
  }

  Integer getAdminPort() {
    return adminPort;
  }

  Product getProduct() {
    return product;
  }

  static TargetPods findTargetPods(
      KubernetesClient k8sClient,
      String namespace,
      String helmReleaseName,
      @Nullable String productType,
      @Nullable Integer adminPort) {

    if (namespace == null) {
      throw new IllegalArgumentException("namespace is required");
    }

    if (helmReleaseName == null) {
      throw new IllegalArgumentException("helmReleaseName is required");
    }

    Product product = null;
    if (productType != null) {
      product = Product.fromType(productType);
    }

    V1PodList podList =
        listNamespacedPod(k8sClient, namespace, LABEL_INSTANCE + "=" + helmReleaseName);

    List<V1Pod> selected = new ArrayList<V1Pod>();
    for (V1Pod pod : podList.getItems()) {
      Map<String, String> labels = pod.getMetadata().getLabels();

      if (!labels.containsKey(LABEL_APP)) {
        continue;
      }

      String appLabelValue = labels.get(LABEL_APP);

      if (product != null
          && !product.getLabelAppValue().equals(appLabelValue)
          && Product.allLabelAppValues().contains(appLabelValue)
          && selected.size() > 0) {
        throw new RuntimeException(
            "Multiple Scalar products are found in the same release. This should not happen. Please"
                + " make sure you deploy Scalar products with Scalar Helm Charts");
      }

      if (product != null && product.getLabelAppValue().equals(appLabelValue)) {
        selected.add(pod);
      } else if (Product.allLabelAppValues().contains(appLabelValue)) {
        product = Product.fromLabelAppValue(appLabelValue);
        selected.add(pod);
      }
    }

    if (adminPort == null && product != null) {
      adminPort = product.getDefaultAdminPort();
    }

    V1Deployment deployment = null;

    if (product != null) {
      deployment = readNamespacedDeployment(k8sClient, namespace, product.getType());
    }

    return new TargetPods(
        k8sClient, namespace, helmReleaseName, product, adminPort, selected, deployment);
  }

  private static V1PodList listNamespacedPod(
      KubernetesClient k8sClient, String namespace, String labelSelector) {
    try {
      return k8sClient
          .getCoreV1Api()
          .listNamespacedPod(
              namespace, null, null, null, null, labelSelector, null, null, null, null, null);
    } catch (ApiException e) {
      throw new RuntimeException("Failed to listNamespacedPod: " + e.getResponseBody());
    }
  }

  private static V1Deployment readNamespacedDeployment(
      KubernetesClient k8sClient, String namespace, String name) {
    try {
      return k8sClient.getAppsV1Api().readNamespacedDeployment(name, namespace, null);
    } catch (ApiException e) {
      throw new RuntimeException("Failed to readNamespacedDeployment: " + e.getResponseBody());
    }
  }

  boolean isUpdated() {
    if (pods.size() == 0) {
      return false;
    }

    if (product == null) {
      throw new RuntimeException("The product of the pods is not identified");
    }

    V1PodList podList =
        listNamespacedPod(k8sClient, namespace, LABEL_INSTANCE + "=" + helmReleaseName);

    List<V1Pod> after =
        podList.getItems().stream()
            .filter(
                p -> {
                  Map<String, String> labels = p.getMetadata().getLabels();
                  if (!labels.containsKey(LABEL_APP)) {
                    return false;
                  }

                  String appLabelValue = labels.get(LABEL_APP);
                  return (product.getLabelAppValue().equals(appLabelValue));
                })
            .collect(Collectors.toList());

    List<V1Pod> before = pods;
    if (after.size() != before.size()) {
      return true;
    }

    for (V1Pod b : before) {
      String podName = b.getMetadata().getName();

      Optional<V1Pod> matched =
          after.stream()
              .parallel()
              .filter(a -> a.getMetadata().getName().equals(podName))
              .findAny();

      if (!matched.isPresent()) {
        return true;
      }

      if (!matched
          .get()
          .getMetadata()
          .getResourceVersion()
          .equals(b.getMetadata().getResourceVersion())) {
        return true;
      }

      int restartCountBeforePause =
          b.getStatus().getContainerStatuses().stream().mapToInt(c -> c.getRestartCount()).sum();

      int restartCountAfterPause =
          matched.get().getStatus().getContainerStatuses().stream()
              .mapToInt(c -> c.getRestartCount())
              .sum();

      if (restartCountAfterPause != restartCountBeforePause) {
        return true;
      }
    }

    V1Deployment afterD = readNamespacedDeployment(k8sClient, namespace, product.getType());
    V1Deployment beforeD = deployment;
    if (!beforeD
        .getMetadata()
        .getResourceVersion()
        .equals(afterD.getMetadata().getResourceVersion())) {
      return true;
    }

    return false;
  }
}
