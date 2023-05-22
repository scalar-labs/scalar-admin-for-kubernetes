package com.scalar.admin.k8s;

import com.google.common.util.concurrent.Uninterruptibles;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1Container;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodSpec;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

class OutClusterPauser implements InternalPauser {

  static final String SCALAR_ADMIN_IMAGE = "@SCALAR_ADMIN_IMAGE@";
  static final Integer MAX_POD_SUCCEEDED_WAIT_TIME = 30;

  private final CoreV1Api api;
  private final String namespace;

  OutClusterPauser(KubernetesClient k8sClient, String namespace) {
    api = k8sClient.getCoreV1Api();
    this.namespace = namespace;
  }

  public void pause(List<V1Pod> pods, Integer adminPort, Integer pauseDuration) {
    V1Pod pauserPod = createPauserPod(pods, adminPort);
    try {
      api.createNamespacedPod(namespace, pauserPod, null, null, null, null);
      waitUntilPodSucceeded(pauserPod);
    } catch (ApiException e) {
      throw new RuntimeException("Pause failed: " + e.getResponseBody());
    } catch (RuntimeException e) {
      throw new RuntimeException("Pause failed: " + e.getMessage());
    } finally {
      tryToDeletePod(namespace, pauserPod);
    }

    Uninterruptibles.sleepUninterruptibly(pauseDuration, TimeUnit.SECONDS);

    V1Pod unpauserPod = createUnpauserPod(pods, adminPort);
    try {
      api.createNamespacedPod(namespace, unpauserPod, null, null, null, null);
      waitUntilPodSucceeded(unpauserPod);
    } catch (ApiException e) {
      throw new RuntimeException("Unpause failed: " + e.getResponseBody());
    } catch (RuntimeException e) {
      throw new RuntimeException("Unpause failed: " + e.getMessage());
    } finally {
      tryToDeletePod(namespace, unpauserPod);
    }
  }

  private String toIPsAndPortsString(List<V1Pod> pods, Integer adminPort) {
    return String.join(
        ",",
        pods.stream()
            .map(p -> p.getStatus().getPodIP() + ":" + adminPort.toString())
            .collect(Collectors.toList()));
  }

  private void tryToDeletePod(String namespace, V1Pod pod) {
    try {
      api.deleteNamespacedPod(
          pod.getMetadata().getName(), namespace, null, null, null, null, null, null);
    } catch (ApiException e) {
      String podName = pod.getMetadata().getName();
      String warningMessage =
          String.format(
              "Warning: Failed to delete Pod %s in Namespace: %s. Please try `kubectl delete pod %s"
                  + " -n %s`.",
              podName, namespace, podName, namespace);
      System.out.println(warningMessage);
    }
  }

  private V1Pod createPauserPod(List<V1Pod> pods, Integer adminPort) {
    V1Pod pod = createScalarAdminPod();

    pod.getMetadata().setName("scalar-admin-pause-" + System.currentTimeMillis());

    pod.getSpec()
        .getContainers()
        .get(0)
        .setCommand(
            Arrays.asList(
                "bin/scalar-admin", "-a", toIPsAndPortsString(pods, adminPort), "-c", "pause"));

    return pod;
  }

  private V1Pod createUnpauserPod(List<V1Pod> pods, Integer adminPort) {
    V1Pod pod = createScalarAdminPod();

    pod.getMetadata().setName("scalar-admin-unpause-" + System.currentTimeMillis());

    pod.getSpec()
        .getContainers()
        .get(0)
        .setCommand(
            Arrays.asList(
                "bin/scalar-admin", "-a", toIPsAndPortsString(pods, adminPort), "-c", "unpause"));

    return pod;
  }

  private V1Pod createScalarAdminPod() {
    V1Pod pod = new V1Pod();
    V1ObjectMeta metadata = new V1ObjectMeta();
    V1Container container = new V1Container();
    V1PodSpec spec = new V1PodSpec();

    container.setImage(SCALAR_ADMIN_IMAGE);
    container.setName("scalar-admin-container");

    spec.setContainers(Arrays.asList(container));
    spec.setRestartPolicy("Never");

    pod.setSpec(spec);
    pod.setMetadata(metadata);

    return pod;
  }

  private void waitUntilPodSucceeded(V1Pod pod) {
    Integer count = 0;
    V1Pod got;

    while (true) {
      try {
        got = api.readNamespacedPod(pod.getMetadata().getName(), namespace, null);
      } catch (ApiException e) {
        throw new RuntimeException("Read Pod failed: " + e.getResponseBody());
      }

      if (got.getStatus().getPhase().equals("Succeeded")) {
        break;
      }

      if (count++ > MAX_POD_SUCCEEDED_WAIT_TIME) {
        throw new RuntimeException(
            "Pod is not succeeded in " + MAX_POD_SUCCEEDED_WAIT_TIME + " seconds");
      }

      Uninterruptibles.sleepUninterruptibly(1, TimeUnit.SECONDS);
    }
  }
}
