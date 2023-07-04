package com.scalar.admin.k8s;

import io.kubernetes.client.openapi.models.V1Pod;
import java.util.List;

interface InternalPauser {
  PausedDuration pause(List<V1Pod> pods, Integer adminPort, Integer pauseDuration);
}
