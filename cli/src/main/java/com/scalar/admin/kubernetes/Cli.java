package com.scalar.admin.kubernetes;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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
          "The mode to discover the target pods."
              + " Valid values: ${COMPLETION-CANDIDATES}. `helm-release` by default.",
      defaultValue = "helm-release")
  private PodDiscoveryMode podDiscoveryMode;

  @Option(
      names = {"--release-name", "-r"},
      description =
          "The helm release name that you specify when you run the `helm install"
              + " <RELEASE_NAME>` command. You can see the <RELEASE_NAME> by using the `helm list`"
              + " command. Required when --pod-discovery-mode is helm-release.")
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
          "A path to a root certificate file for verifying the server's certificate when wire"
              + " encryption is enabled.")
  private String caRootCertPath;

  @Option(
      names = {"--ca-root-cert-pem"},
      description =
          "A PEM format string of a root certificate for verifying the server's certificate when"
              + " wire encryption is enabled. This option is prioritized when --ca-root-cert-path"
              + " is specified.")
  private String caRootCertPem;

  @Option(
      names = {"--override-authority"},
      description =
          "The value to be used as the expected authority in the server's certificate when wire"
              + " encryption is enabled.")
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
    // Validate CLI options for the selected pod discovery mode.
    try {
      podDiscoveryMode.validate(helmReleaseName, deploymentName, adminPort);
    } catch (IllegalArgumentException e) {
      logger.error(e.getMessage());
      return 1;
    }

    Result result = null;

    try {
      // Create TargetSelector via factory (K8s client init is handled internally).
      TargetSelector targetSelector;
      switch (podDiscoveryMode) {
        case HELM_RELEASE:
          targetSelector = TargetSelectorFactory.fromHelmRelease(namespace, helmReleaseName);
          break;
        case DEPLOYMENT:
          targetSelector =
              TargetSelectorFactory.fromDeployment(namespace, deploymentName, adminPort);
          break;
        default:
          throw new AssertionError("Unknown PodDiscoveryMode: " + podDiscoveryMode);
      }

      // Create Pauser with injected TargetSelector.
      Pauser pauser =
          tlsEnabled
              ? new TlsPauser(targetSelector, getCaRootCert(), overrideAuthority)
              : new Pauser(targetSelector);

      PausedDuration duration = pauser.pause(pauseDuration, maxPauseWaitTime);

      switch (podDiscoveryMode) {
        case HELM_RELEASE:
          result = new Result(namespace, helmReleaseName, duration, zoneId);
          break;
        case DEPLOYMENT:
          result = new Result(namespace, deploymentName, adminPort, duration, zoneId);
          break;
        default:
          throw new AssertionError("Unknown PodDiscoveryMode: " + podDiscoveryMode);
      }
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
