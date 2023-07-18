package com.scalar.admin.k8s;

import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.AppsV1Api;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1Deployment;
import io.kubernetes.client.openapi.models.V1DeploymentList;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodList;
import io.kubernetes.client.openapi.models.V1Service;
import io.kubernetes.client.openapi.models.V1ServiceList;
import io.kubernetes.client.openapi.models.V1ServicePort;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

class TargetSelector {

  static final String LABEL_INSTANCE = "app.kubernetes.io/instance";
  static final String LABEL_APP = "app.kubernetes.io/app";
  static final String ADMIN_SERVICE_NAME_SUFFIX = "-headless";

  private final CoreV1Api coreApi;
  private final AppsV1Api appsApi;
  private final String namespace;
  private final String helmReleaseName;

  TargetSelector(CoreV1Api coreApi, AppsV1Api appsApi, String namespace, String helmReleaseName) {
    this.coreApi = coreApi;
    this.appsApi = appsApi;
    this.namespace = namespace;
    this.helmReleaseName = helmReleaseName;
  }

  TargetSnapshot select() {
    try {
      List<V1Pod> podsCreatedByHelmRelease =
          findPodsCreatedByHelmRelease(namespace, helmReleaseName);

      PodsWithSameProduct podsWithSameProduct =
          selectPodsRunScalarProduct(podsCreatedByHelmRelease);

      V1Deployment deployment =
          findDeploymentCreatedByHelmReleaseForProduct(
              namespace, helmReleaseName, podsWithSameProduct.product);

      V1Service service =
          findServiceCreatedByHelmReleaseForProduct(
              namespace, helmReleaseName, podsWithSameProduct.product);

      Integer adminPort =
          findAdminPortInService(service, podsWithSameProduct.product.getAdminPortName());

      return new TargetSnapshot(podsWithSameProduct.pods, deployment, adminPort);
    } catch (Exception e) {
      throw new RuntimeException("Can not find any target pods. " + e.getMessage());
    }
  }

  private List<V1Pod> findPodsCreatedByHelmRelease(String namespace, String releaseName) {
    V1PodList podList;
    try {
      podList =
          coreApi.listNamespacedPod(
              namespace,
              null,
              null,
              null,
              null,
              LABEL_INSTANCE + "=" + releaseName,
              null,
              null,
              null,
              null,
              null);
    } catch (ApiException e) {
      String m =
          String.format(
              "Kubernetes API error when requesting listNamespacedPod. %s", e.getResponseBody());
      throw new RuntimeException(m);
    }

    List<V1Pod> pods = podList.getItems();

    if (pods.size() == 0) {
      String m = String.format("Helm release %s didn't create any pod.", releaseName);
      throw new RuntimeException(m);
    }

    return pods;
  }

  private V1Deployment findDeploymentCreatedByHelmReleaseForProduct(
      String namespace, String releaseName, Product product) {
    String labelSelector =
        String.format(
            "%s,%s",
            LABEL_INSTANCE + "=" + releaseName, LABEL_APP + "=" + product.getAppLabelValue());

    V1DeploymentList deploymentList;
    try {
      deploymentList =
          appsApi.listNamespacedDeployment(
              namespace, null, null, null, null, labelSelector, null, null, null, null, null);
    } catch (ApiException e) {
      String m =
          String.format(
              "Kubernetes API error when requesting listNamespacedDeployment. %s",
              e.getResponseBody());
      throw new RuntimeException(m);
    }

    List<V1Deployment> deployments = deploymentList.getItems();

    if (deployments.size() == 0) {
      String m = String.format("Helm release %s didn't create any deployment.", releaseName);
      throw new RuntimeException(m);
    }

    if (deployments.size() > 1) {
      String m =
          String.format(
              "Helm release %s created more than one deployment. Please make sure you deploy Scalar"
                  + " products with Scalar Helm Charts.",
              releaseName);
      throw new RuntimeException(m);
    }

    return deployments.get(0);
  }

  private V1Service findServiceCreatedByHelmReleaseForProduct(
      String namespace, String releaseName, Product product) {
    String labelSelector =
        String.format(
            "%s,%s",
            LABEL_INSTANCE + "=" + releaseName, LABEL_APP + "=" + product.getAppLabelValue());

    V1ServiceList serviceList;
    try {
      serviceList =
          coreApi.listNamespacedService(
              namespace, null, null, null, null, labelSelector, null, null, null, null, null);
    } catch (ApiException e) {
      String m =
          String.format(
              "Kubernetes API error when requesting listNamespacedService. %s",
              e.getResponseBody());
      throw new RuntimeException(m);
    }

    List<V1Service> services = serviceList.getItems();

    if (services.size() == 0) {
      String m = String.format("Helm release %s didn't create any service.", releaseName);
      throw new RuntimeException(m);
    }

    List<V1Service> servicesHaveScalarAdmin =
        services.stream()
            .filter(s -> s.getMetadata().getName().endsWith(ADMIN_SERVICE_NAME_SUFFIX))
            .collect(Collectors.toList());

    if (servicesHaveScalarAdmin.size() == 0) {
      String m =
          String.format(
              "Helm release %s didn't create any service that runs Scalar Admin interface.",
              releaseName);
      throw new RuntimeException(m);
    }

    if (servicesHaveScalarAdmin.size() != 1) {
      String m =
          String.format(
              "Helm release %s create more than one service that run Scalar Admin interface.",
              releaseName);
      throw new RuntimeException(m);
    }

    return servicesHaveScalarAdmin.get(0);
  }

  private PodsWithSameProduct selectPodsRunScalarProduct(List<V1Pod> pods) {

    List<V1Pod> selected = new ArrayList<V1Pod>();
    Product productThesePodsRun = Product.UNKNOWN;

    for (V1Pod pod : pods) {
      Map<String, String> labels = pod.getMetadata().getLabels();

      if (!labels.containsKey(LABEL_APP)) {
        String m =
            String.format(
                "A pod %s does not have the label: %s. Please deploy Scalar products with Scalar"
                    + " Helm Charts.",
                pod.getMetadata().getName(), LABEL_APP);
        throw new RuntimeException(m);
      }

      String appLabelValue = labels.get(LABEL_APP);
      Product productThisPodRuns = Product.fromAppLabelValue(appLabelValue);

      if (productThisPodRuns == Product.UNKNOWN) {
        continue;
      }

      if (productThesePodsRun == Product.UNKNOWN) {
        productThesePodsRun = productThisPodRuns;
      }

      if (productThisPodRuns != productThesePodsRun) {

        String m =
            String.format(
                "The pods created by the Helm release run different Scalar products: %s and %s."
                    + " This should not happen. Please make sure you deploy Scalar products with"
                    + " Scalar Helm Charts.",
                productThesePodsRun, productThisPodRuns);

        throw new RuntimeException(m);
      }

      selected.add(pod);
    }

    if (productThesePodsRun == Product.UNKNOWN || selected.size() == 0) {
      throw new RuntimeException(
          "The pods created by the Helm release don't run any Scalar product.");
    }

    return new PodsWithSameProduct(productThesePodsRun, selected);
  }

  private Integer findAdminPortInService(V1Service service, String portName) {
    V1ServicePort servicePort =
        service.getSpec().getPorts().stream()
            .filter(p -> p.getName().equals(portName))
            .findFirst()
            .orElseThrow(
                () -> {
                  String m =
                      String.format(
                          "Can not find the port %s in the service %s.",
                          portName, service.getMetadata().getName());
                  return new RuntimeException(m);
                });

    return servicePort.getTargetPort().getIntValue();
  }

  private class PodsWithSameProduct {
    private final Product product;
    private final List<V1Pod> pods;

    PodsWithSameProduct(Product product, List<V1Pod> pods) {
      this.product = product;
      this.pods = pods;
    }
  }
}
