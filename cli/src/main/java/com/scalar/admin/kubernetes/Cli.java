package com.scalar.admin.kubernetes;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.scalar.admin.kubernetes.application.dto.PauseDurationDto;
import com.scalar.admin.kubernetes.infrastructure.module.PauseModule;
import com.scalar.admin.kubernetes.presentation.PauseController;
import com.scalar.admin.kubernetes.presentation.dto.PauseRequest;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.ZoneId;
import java.util.concurrent.Callable;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(
    name = "scalar-admin-for-kubernetes-cli",
    description = "Scalar Admin pause tool for the Kubernetes environment")
class Cli implements Callable<Integer> {

  private final Logger logger = LoggerFactory.getLogger(Cli.class);

  @Option(
      names = {"--namespace", "-n"},
      description =
          "Namespace that Scalar products you want to pause are deployed. `default` by default.",
      defaultValue = "default")
  private String namespace;

  @Option(
      names = {"--pod-discovery-mode"},
      description =
          "The mode to discover the target pods. Valid values: helm-release, deployment."
              + " helm-release by default.",
      defaultValue = "helm-release")
  private String podDiscoveryMode;

  @Option(
      names = {"--release-name", "-r"},
      description =
          "The helm release name that you specify when you run the `helm install <RELEASE_NAME>`"
              + " command. You can see the <RELEASE_NAME> by using the `helm list` command."
              + " Required when --pod-discovery-mode is helm-release.")
  @Nullable
  private String helmReleaseName;

  @Option(
      names = {"--deployment-name"},
      description =
          "The name of the Kubernetes Deployment for the Scalar product."
              + " Required when --pod-discovery-mode is deployment.")
  @Nullable
  private String deploymentName;

  @Option(
      names = {"--admin-port"},
      description =
          "The port number of the admin interface of the Scalar product."
              + " Required when --pod-discovery-mode is deployment.")
  @Nullable
  private Integer adminPort;

  @Option(
      names = {"--pause-duration", "-d"},
      description = "The duration of the pause period by millisecond. 5000 (5 seconds) by default.",
      defaultValue = "5000")
  private Integer pauseDuration;

  @Option(
      names = {"--max-pause-wait-time", "-w"},
      description =
          "The max wait time (in milliseconds) until Scalar products drain outstanding requests"
              + " before they pause. If omitting this option, the max wait time will be the default"
              + " value defined in the products. Most Scalar products have the default value of 30"
              + " seconds.")
  @Nullable
  private Long maxPauseWaitTime;

  @Option(
      names = {"--time-zone", "-z"},
      description =
          "Specify a time zone ID, e.g., Asia/Tokyo, to output successful paused"
              + " period. Note the time zone ID is case sensitive. Etc/UTC by default.",
      converter = ZoneIdConverter.class,
      defaultValue = "Etc/UTC")
  private ZoneId zoneId;

  @Option(
      names = {"--tls"},
      description = "Whether wire encryption (TLS) between scalar-admin and the target is enabled.")
  private boolean tlsEnabled;

  @Option(
      names = {"--ca-root-cert-path"},
      description =
          "A path to a root certificate file for verifying the server's certificate."
              + " Either this or --ca-root-cert-pem is required when --tls is enabled.")
  private String caRootCertPath;

  @Option(
      names = {"--ca-root-cert-pem"},
      description =
          "A PEM format string of a root certificate for verifying the server's certificate."
              + " Either this or --ca-root-cert-path is required when --tls is enabled."
              + " This option takes precedence over --ca-root-cert-path.")
  private String caRootCertPem;

  @Option(
      names = {"--override-authority"},
      description =
          "The value to be used as the expected authority in the server's certificate."
              + " Required when --tls is enabled.")
  private String overrideAuthority;

  @Option(
      names = {"-h", "--help"},
      usageHelp = true,
      description = "Display the help message.")
  boolean helpRequested;

  public static void main(String[] args) {
    int exitCode =
        new CommandLine(new Cli()).setCaseInsensitiveEnumValuesAllowed(true).execute(args);
    System.exit(exitCode);
  }

  @Override
  public Integer call() {
    Result result = null;

    try {
      // Create controller
      Injector injector = Guice.createInjector(new PauseModule());
      PauseController controller = injector.getInstance(PauseController.class);

      // Build PauseRequest
      PauseRequest request =
          new PauseRequest(
              namespace,
              podDiscoveryMode,
              helmReleaseName,
              deploymentName,
              adminPort,
              pauseDuration,
              maxPauseWaitTime,
              tlsEnabled,
              getCaRootCert(),
              overrideAuthority);

      // Execute pause operation
      PauseDurationDto durationDto = controller.pause(request);

      // Build result
      result = new Result(namespace, helmReleaseName, durationDto, zoneId);
      ObjectMapper mapper = new ObjectMapper();
      System.out.println(mapper.writeValueAsString(result));
    } catch (JsonProcessingException e) {
      logger.error(
          "Succeeded to pause Scalar products but failed to output the result in JSON.", e);
      logger.info(
          "Paused duration: from {} to {}, {}",
          result.pauseStartDateTime,
          result.pauseEndDateTime,
          result.timezone);
      return 1;
    } catch (Exception e) {
      logger.error("Failed to pause Scalar products.", e);
      return 1;
    }

    return 0;
  }

  private String getCaRootCert() {
    String caRootCert = null;

    if (caRootCertPem != null) {
      caRootCert = caRootCertPem.replace("\\n", System.lineSeparator());
    } else if (caRootCertPath != null) {
      try {
        caRootCert =
            new String(
                Files.readAllBytes(new File(caRootCertPath).toPath()), StandardCharsets.UTF_8);
      } catch (IOException e) {
        throw new UncheckedIOException("Couldn't read the file: " + caRootCertPath, e);
      }
    }

    return caRootCert;
  }
}
