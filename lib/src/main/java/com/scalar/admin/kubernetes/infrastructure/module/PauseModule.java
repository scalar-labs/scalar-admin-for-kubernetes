package com.scalar.admin.kubernetes.infrastructure.module;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.scalar.admin.kubernetes.domain.client.KubernetesClient;
import com.scalar.admin.kubernetes.domain.client.ScalarAdminClientFactory;
import com.scalar.admin.kubernetes.domain.exception.PauserException;
import com.scalar.admin.kubernetes.infrastructure.client.KubernetesClientImpl;
import com.scalar.admin.kubernetes.infrastructure.client.ScalarAdminClientFactoryImpl;
import io.kubernetes.client.openapi.Configuration;
import io.kubernetes.client.openapi.apis.AppsV1Api;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.util.Config;
import java.io.IOException;

/**
 * Guice module that binds domain interfaces to their infrastructure implementations.
 *
 * <p>This module serves as the composition root for dependency injection, wiring together the
 * Kubernetes client, Scalar Admin client factory, and their dependencies. It is intended to be
 * instantiated at the application entry point (e.g., CLI).
 */
public class PauseModule extends AbstractModule {

  @Override
  protected void configure() {
    bind(ScalarAdminClientFactory.class).to(ScalarAdminClientFactoryImpl.class).in(Singleton.class);
  }

  @Provides
  @Singleton
  KubernetesClient provideKubernetesClient() throws PauserException {
    try {
      Configuration.setDefaultApiClient(Config.defaultClient());
    } catch (IOException e) {
      throw new PauserException("Failed to set default Kubernetes client.", e);
    }
    return new KubernetesClientImpl(new CoreV1Api(), new AppsV1Api());
  }
}
