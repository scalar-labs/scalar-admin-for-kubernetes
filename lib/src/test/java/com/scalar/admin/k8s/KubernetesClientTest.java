package com.scalar.admin.k8s;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.Configuration;
import io.kubernetes.client.openapi.apis.AppsV1Api;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.util.ClientBuilder;
import io.kubernetes.client.util.Config;
import io.kubernetes.client.util.KubeConfig;
import java.io.FileReader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

public class KubernetesClientTest {

  private MockedConstruction<CoreV1Api> coreV1ApiMocked;
  private MockedConstruction<AppsV1Api> appsV1ApiMocked;
  private MockedConstruction<FileReader> fileReaderMocked;

  private ApiClient apiClientMocked;
  private KubeConfig kubeConfigMocked;
  private ClientBuilder clientBuilderMocked;

  private MockedStatic<Configuration> configurationMocked;
  private MockedStatic<Config> configMocked;
  private MockedStatic<KubeConfig> kubeConfigStaticMocked;
  private MockedStatic<ClientBuilder> clientBuilderStaticMocked;

  @BeforeEach
  public void setUp() {
    coreV1ApiMocked = mockConstruction(CoreV1Api.class);
    appsV1ApiMocked = mockConstruction(AppsV1Api.class);
    fileReaderMocked = mockConstruction(FileReader.class);

    apiClientMocked = mock(ApiClient.class);
    kubeConfigMocked = mock(KubeConfig.class);
    clientBuilderMocked = mock(ClientBuilder.class);

    configurationMocked = mockStatic(Configuration.class);
    configMocked = mockStatic(Config.class);
    kubeConfigStaticMocked = mockStatic(KubeConfig.class);
    clientBuilderStaticMocked = mockStatic(ClientBuilder.class);

    configMocked.when(() -> Config.defaultClient()).thenReturn(apiClientMocked);
    kubeConfigStaticMocked
        .when(() -> KubeConfig.loadKubeConfig(any()))
        .thenReturn(kubeConfigMocked);
    clientBuilderStaticMocked
        .when(() -> ClientBuilder.kubeconfig(any()))
        .thenReturn(clientBuilderMocked);
  }

  @AfterEach
  public void tearDown() {
    coreV1ApiMocked.close();
    appsV1ApiMocked.close();
    fileReaderMocked.close();

    configurationMocked.close();
    configMocked.close();
    kubeConfigStaticMocked.close();
    clientBuilderStaticMocked.close();
  }

  @Test
  public void constructor_WithNullConfigAndNullContext_ShouldNotCallLoadKubeConfigAndSetContext() {
    // Arrange

    // Act
    KubernetesClient client = new KubernetesClient(null, null, false);

    // Assert
    configurationMocked.verify(
        () -> Configuration.setDefaultApiClient(Config.defaultClient()), times(1));
    kubeConfigStaticMocked.verify(() -> KubeConfig.loadKubeConfig(any()), never());

    verify(kubeConfigMocked, never()).setContext(anyString());

    assertEquals(1, coreV1ApiMocked.constructed().size());
    assertEquals(1, appsV1ApiMocked.constructed().size());
    assertEquals(coreV1ApiMocked.constructed().get(0), client.getCoreV1Api());
    assertEquals(appsV1ApiMocked.constructed().get(0), client.getAppsV1Api());
  }

  @Test
  public void constructor_WithConfigGivenButNullContext_ShouldTryLoadConfigAndSetCurrentContext() {
    // Arrange
    String configName = "file-1";
    String contextName = "context-1";

    when(kubeConfigMocked.getCurrentContext()).thenReturn(contextName);
    when(kubeConfigMocked.setContext(contextName)).thenReturn(true);
    when(clientBuilderMocked.build()).thenReturn(apiClientMocked);

    // Act
    KubernetesClient client = new KubernetesClient(configName, null, false);

    // Assert
    configurationMocked.verify(() -> Configuration.setDefaultApiClient(apiClientMocked), times(1));
    kubeConfigStaticMocked.verify(
        () -> KubeConfig.loadKubeConfig(fileReaderMocked.constructed().get(0)), times(1));

    verify(kubeConfigMocked, times(1)).getCurrentContext();
    verify(kubeConfigMocked, times(1)).setContext(contextName);

    assertEquals(1, coreV1ApiMocked.constructed().size());
    assertEquals(1, appsV1ApiMocked.constructed().size());
    assertEquals(coreV1ApiMocked.constructed().get(0), client.getCoreV1Api());
    assertEquals(appsV1ApiMocked.constructed().get(0), client.getAppsV1Api());
  }

  @Test
  public void constructor_WithConfigAndContextGiven_ShouldTryLoadConfigAndSetGivenContext() {
    // Arrange
    String configName = "file-2";
    String contextName = "context-2";

    when(kubeConfigMocked.setContext(contextName)).thenReturn(true);
    when(clientBuilderMocked.build()).thenReturn(apiClientMocked);

    // Act
    KubernetesClient client = new KubernetesClient(configName, contextName, false);

    // Assert
    configurationMocked.verify(() -> Configuration.setDefaultApiClient(apiClientMocked), times(1));
    kubeConfigStaticMocked.verify(
        () -> KubeConfig.loadKubeConfig(fileReaderMocked.constructed().get(0)), times(1));

    verify(kubeConfigMocked, never()).getCurrentContext();
    verify(kubeConfigMocked, times(1)).setContext(contextName);

    assertEquals(1, coreV1ApiMocked.constructed().size());
    assertEquals(1, appsV1ApiMocked.constructed().size());
    assertEquals(coreV1ApiMocked.constructed().get(0), client.getCoreV1Api());
    assertEquals(appsV1ApiMocked.constructed().get(0), client.getAppsV1Api());
  }

  @Test
  public void
      constructor_WithNullConfigButContextGiven_ShouldTryLoadConfigFromEnvironmentAndSetContext() {
    // Arrange
    String contextName = "context-3";

    when(kubeConfigMocked.setContext(contextName)).thenReturn(true);
    when(clientBuilderMocked.build()).thenReturn(apiClientMocked);

    // Act
    KubernetesClient client = new KubernetesClient(null, contextName, false);

    // Assert
    configurationMocked.verify(() -> Configuration.setDefaultApiClient(apiClientMocked), times(1));
    kubeConfigStaticMocked.verify(
        () -> KubeConfig.loadKubeConfig(fileReaderMocked.constructed().get(0)), times(1));

    verify(kubeConfigMocked, never()).getCurrentContext();
    verify(kubeConfigMocked, times(1)).setContext(contextName);

    assertEquals(1, coreV1ApiMocked.constructed().size());
    assertEquals(1, appsV1ApiMocked.constructed().size());
    assertEquals(coreV1ApiMocked.constructed().get(0), client.getCoreV1Api());
    assertEquals(appsV1ApiMocked.constructed().get(0), client.getAppsV1Api());
  }

  @Test
  public void
      constructor_WithConfigAndContextGivenAndInClusterIsTrue_ShouldNotCallLoadKubeConfigAndSetContext() {
    // Arrange
    String configName = "file-4";
    String contextName = "context-4";

    // Act
    KubernetesClient client = new KubernetesClient(configName, contextName, true);

    // Assert
    configurationMocked.verify(
        () -> Configuration.setDefaultApiClient(Config.defaultClient()), times(1));
    kubeConfigStaticMocked.verify(() -> KubeConfig.loadKubeConfig(any()), never());

    verify(kubeConfigMocked, never()).setContext(anyString());

    assertEquals(1, coreV1ApiMocked.constructed().size());
    assertEquals(1, appsV1ApiMocked.constructed().size());
    assertEquals(coreV1ApiMocked.constructed().get(0), client.getCoreV1Api());
    assertEquals(appsV1ApiMocked.constructed().get(0), client.getAppsV1Api());
  }
}
