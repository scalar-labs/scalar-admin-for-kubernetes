package com.scalar.admin.k8s;

import java.time.ZoneId;
import java.util.concurrent.Callable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(
    name = "scalar-admin-k8s-cli",
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
      names = {"--release-name", "-r"},
      description =
          "Helm's release name that you specify when you run the `helm install <RELEASE_NAME>`"
              + " command. You can see the <RELEASE_NAME> by using the `helm list` command.",
      required = true)
  private String helmReleaseName;

  @Option(
      names = {"--pause-duration", "-d"},
      description = "The duration of the pause period by millisecond. 5000 by default.",
      defaultValue = "5000")
  private Integer pauseDuration;

  @Option(
      names = {"--time-zone", "-z"},
      description =
          "Specify a time zone ID, e.g., Asia/Tokyo, to output successful paused"
              + " period. Note the time zone ID is case sensitive. Etc/UTC by default.",
      converter = ZoneIdConverter.class,
      defaultValue = "Etc/UTC")
  private ZoneId zoneId;

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
    try {
      Pauser pauser = new Pauser(namespace, helmReleaseName);
      PausedDuration duration = pauser.pause(pauseDuration);

      System.out.printf(
          "Paused successfully. Duration: from %s to %s (%s).\n",
          duration.getStartTime().atZone(zoneId).toLocalDateTime(),
          duration.getEndTime().atZone(zoneId).toLocalDateTime(),
          zoneId.toString());
    } catch (PauserException e) {
      logger.error("Failed to pause Scalar products.", e);
      return 1;
    }

    return 0;
  }
}
