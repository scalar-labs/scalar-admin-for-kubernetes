package com.scalar.admin.k8s;

import java.util.concurrent.Callable;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "scalar-admin-k8s-cli", description = "Scalar Admin pause tool for Kubernetes")
class Cli implements Callable<Integer> {

  @Option(
      names = {"--namespace", "-n"},
      description =
          "Namespace that Scalar products you want to pause are deployed. Use `default` by default",
      defaultValue = "default")
  private String namespace;

  @Option(
      names = {"--type", "-t"},
      description =
          "Scalar product type that you will pause, must be scalardb, scalardb-cluster,"
              + " scalardl-ledger, or scalardl-auditor.")
  private String productType;

  @Option(
      names = {"--release-name", "-r"},
      description =
          "Helm's release name that you specify when you run the `helm install <releaseName>`"
              + " command. You can see the <releaseName> by using the `helm list` command",
      required = true)
  private String helmReleaseName;

  @Option(
      names = {"--pause-duration", "-d"},
      description = "The duration of the pause period by second. 10 by default",
      defaultValue = "10")
  private Integer pauseDuration;

  @Option(
      names = {"--kubeconfig"},
      description =
          "Path to the kubeconfig file. Use default config if not specified."
              + "If --in-cluster is set, this value is ignored")
  private String kubeConfigFilePath;

  @Option(
      names = {"--kube-context"},
      description =
          "Name of the kubeconfig context to use. Use the `current-context` if not specified."
              + "If --in-cluster is set, this value is ignored")
  private String kubeConfigContext;

  @Option(
      names = {"--admin-port", "-p"},
      description =
          "The port that handles Scalar admin protocol. Use the default port (according to the"
              + " product type) if not specified.")
  private Integer adminPort;

  @Option(
      names = {"--in-cluster"},
      description =
          "Use in-cluster mode (The Scalar Admin Kubernetes tool is running in the same cluster"
              + " with Scalar products).",
      defaultValue = "false")
  boolean inCluster;

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
    Pauser pauser =
        new Pauser(
            namespace,
            helmReleaseName,
            kubeConfigFilePath,
            kubeConfigContext,
            productType,
            adminPort,
            inCluster);

    return pauser.pause(pauseDuration);
  }
}
