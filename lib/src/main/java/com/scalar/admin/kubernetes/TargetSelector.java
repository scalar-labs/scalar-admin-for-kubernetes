package com.scalar.admin.kubernetes;

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
import javax.annotation.concurrent.ThreadSafe;

@ThreadSafe
class TargetSelector {

  static final String LABEL_INSTANCE = "app.kubernetes.io/instance";
  static final String LABEL_APP = "app.kubernetes.io/app";
  static final String ADMIN_SERVICE_NAME_SUFFIX = "-headless";

  private final PodDiscoveryMode mode;
  private final CoreV1Api coreApi;
  private final AppsV1Api appsApi;
  private final String namespace;
  @javax.annotation.Nullable private final String helmReleaseName;
  @javax.annotation.Nullable private final String deploymentName;
  private final int adminPort;

  /** Constructor for HELM_RELEASE mode. */
  TargetSelector(CoreV1Api coreApi, AppsV1Api appsApi, String namespace, String helmReleaseName) {
    this.mode = PodDiscoveryMode.HELM_RELEASE;
    this.coreApi = coreApi;
    this.appsApi = appsApi;
    this.namespace = namespace;
    this.helmReleaseName = helmReleaseName;
    this.deploymentName = null;
    this.adminPort = 0;
  }

  /** Constructor for DEPLOYMENT mode. */
  TargetSelector(
      CoreV1Api coreApi,
      AppsV1Api appsApi,
      String namespace,
      String deploymentName,
      int adminPort) {
    this.mode = PodDiscoveryMode.DEPLOYMENT;
    this.coreApi = coreApi;
    this.appsApi = appsApi;
    this.namespace = namespace;
    this.helmReleaseName = null;
    this.deploymentName = deploymentName;
    this.adminPort = adminPort;
  }

  TargetSnapshot select() throws PauserException {
    switch (mode) {
      case HELM_RELEASE:
        return selectByHelmRelease();
      case DEPLOYMENT:
        return selectByDeploymentName();
      default:
        throw new AssertionError("Unknown PodDiscoveryMode: " + mode);
    }
  }

  private TargetSnapshot selectByHelmRelease() throws PauserException {
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

      Integer port =
          findAdminPortInService(service, podsWithSameProduct.product.getAdminPortName());

      return new TargetSnapshot(podsWithSameProduct.pods, deployment, port);
    } catch (Exception e) {
      throw new PauserException("Can not find any target pods.", e);
    }
  }

  private TargetSnapshot selectByDeploymentName() throws PauserException {
    try {
      V1Deployment deployment = findDeploymentByName(namespace, deploymentName);
      List<V1Pod> pods = findPodsByDeploymentSelector(namespace, deployment);
      return new TargetSnapshot(pods, deployment, adminPort);
    } catch (PauserException e) {
      throw e;
    } catch (Exception e) {
      throw new PauserException("Can not find any target pods.", e);
    }
  }

  private V1Deployment findDeploymentByName(String namespace, String name) throws PauserException {
    try {
      return appsApi.readNamespacedDeployment(name, namespace, null);
    } catch (ApiException e) {
      String m =
          String.format(
              "Failed to get deployment %s in namespace %s."
                  + " Kubernetes API error with code %d and body %s.",
              name, namespace, e.getCode(), e.getResponseBody());
      throw new PauserException(m, e);
    }
  }

  private List<V1Pod> findPodsByDeploymentSelector(String namespace, V1Deployment deployment)
      throws PauserException {
    Map<String, String> matchLabels = deployment.getSpec().getSelector().getMatchLabels();

    String labelSelector =
        matchLabels.entrySet().stream()
            .map(e -> e.getKey() + "=" + e.getValue())
            .collect(Collectors.joining(","));

    V1PodList podList;
    try {
      podList =
          coreApi.listNamespacedPod(
              namespace, null, null, null, null, labelSelector, null, null, null, null, null);
    } catch (ApiException e) {
      String m =
          String.format(
              "Kubernetes listNamespacedPod API error with code %d and body %s.",
              e.getCode(), e.getResponseBody());
      throw new PauserException(m, e);
    }

    List<V1Pod> pods = podList.getItems();
    if (pods.isEmpty()) {
      String m =
          String.format(
              "Deployment %s does not have any running pods.",
              deployment.getMetadata().getName());
      throw new PauserException(m);
    }

    return pods;
  }

  private List<V1Pod> findPodsCreatedByHelmRelease(String namespace, String releaseName)
      throws PauserException {
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
              "Kubernetes listNamespacedPod API error with code %d and body %s.",
              e.getCode(), e.getResponseBody());
      throw new PauserException(m);
    }

    List<V1Pod> pods = podList.getItems();

    if (pods.size() == 0) {
      String m = String.format("Helm release %s didn't create any pod.", releaseName);
      throw new PauserException(m);
    }

    return pods;
  }

  private V1Deployment findDeploymentCreatedByHelmReleaseForProduct(
      String namespace, String releaseName, Product product) throws PauserException {
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
              "Kubernetes listNamespacedDeployment API error with code %d and body %s.",
              e.getCode(), e.getResponseBody());
      throw new PauserException(m, e);
    }

    List<V1Deployment> deployments = deploymentList.getItems();

    if (deployments.size() == 0) {
      String m = String.format("Helm release %s didn't create any deployment.", releaseName);
      throw new PauserException(m);
    }

    if (deployments.size() > 1) {
      String m =
          String.format(
              "Helm release %s created more than one deployment. Please make sure you deploy Scalar"
                  + " products with Scalar Helm Charts.",
              releaseName);
      throw new PauserException(m);
    }

    return deployments.get(0);
  }

  private V1Service findServiceCreatedByHelmReleaseForProduct(
      String namespace, String releaseName, Product product) throws PauserException {
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
              "Kubernetes listNamespacedService API error with code %d and body %s.",
              e.getCode(), e.getResponseBody());
      throw new PauserException(m, e);
    }

    List<V1Service> services = serviceList.getItems();

    if (services.size() == 0) {
      String m = String.format("Helm release %s didn't create any service.", releaseName);
      throw new PauserException(m);
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
      throw new PauserException(m);
    }

    if (servicesHaveScalarAdmin.size() != 1) {
      String m =
          String.format(
              "Helm release %s create more than one service that run Scalar Admin interface.",
              releaseName);
      throw new PauserException(m);
    }

    return servicesHaveScalarAdmin.get(0);
  }

  /**
   * This method filters the givens pods and returns a list of pods of the same Scalar product
   * (i.e., having the same app.kubernetes.io/app value). What value of app.kubernetes.io/app is
   * used depends on the first pod having the value of Scalar products. The other pods, for example,
   * an Envoy pod, will be excluded. An exception is thrown if there are pods of different products.
   */
  private PodsWithSameProduct selectPodsRunScalarProduct(List<V1Pod> pods) throws PauserException {

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
        throw new PauserException(m);
      }

      String appLabelValue = labels.get(LABEL_APP);
      Product productThisPodRuns = Product.fromAppLabelValue(appLabelValue);

      // If the pod doesn't run any Scalar product, e.g, an Envoy pod, we exclude it.
      if (productThisPodRuns == Product.UNKNOWN) {
        continue;
      }

      // If this is the first pod, we use its product as the product of all pods.
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

        throw new PauserException(m);
      }

      selected.add(pod);
    }

    if (productThesePodsRun == Product.UNKNOWN || selected.size() == 0) {
      throw new PauserException(
          "The pods created by the Helm release don't run any Scalar product.");
    }

    return new PodsWithSameProduct(productThesePodsRun, selected);
  }

  private Integer findAdminPortInService(V1Service service, String portName)
      throws PauserException {
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
                  return new PauserException(m);
                });

    if (!servicePort.getTargetPort().isInteger()) {
      throw new PauserException(
          String.format(
              "The service %s seems using the port definition %s in the TargetPort. This should not"
                  + " happen. Please deploy Scalar products by Scalar Helm Charts.",
              service.getMetadata().getName(), servicePort.getTargetPort().getStrValue()));
    }

    return servicePort.getTargetPort().getIntValue();
  }

  private static class PodsWithSameProduct {
    private final Product product;
    private final List<V1Pod> pods;

    PodsWithSameProduct(Product product, List<V1Pod> pods) {
      this.product = product;
      this.pods = pods;
    }
  }
}
