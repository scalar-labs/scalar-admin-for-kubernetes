# Scalar Admin for Kubernetes

Scalar Admin for Kubernetes is a tool that creates a paused state for ScalarDB or ScalarDL in a Kubernetes environment. You can use such a paused state to take backups easily and consistently across multiple diverse databases.

## Usage of the CLI tool

```console
Usage: scalar-admin-for-kubernetes-cli [-h] [--tls]
                                       [--ca-root-cert-path=<caRootCertPath>]
                                       [--ca-root-cert-pem=<caRootCertPem>]
                                       [-d=<pauseDuration>] [-n=<namespace>]
                                       [--override-authority=<overrideAuthority>
                                       ] -r=<helmReleaseName>
                                       [-w=<maxPauseWaitTime>] [-z=<zoneId>]
Scalar Admin pause tool for the Kubernetes environment
      --ca-root-cert-path=<caRootCertPath>
                             A path to a root certificate file for verifying
                               the server's certificate when wire encryption is
                               enabled.
      --ca-root-cert-pem=<caRootCertPem>
                             A PEM format string of a root certificate for
                               verifying the server's certificate when wire
                               encryption is enabled. This option is
                               prioritized when --ca-root-cert-path is
                               specified.
  -d, --pause-duration=<pauseDuration>
                             The duration of the pause period by millisecond.
                               5000 (5 seconds) by default.
  -h, --help                 Display the help message.
  -n, --namespace=<namespace>
                             Namespace that Scalar products you want to pause
                               are deployed. `default` by default.
      --override-authority=<overrideAuthority>
                             The value to be used as the expected authority in
                               the server's certificate when wire encryption is
                               enabled.
  -r, --release-name=<helmReleaseName>
                             Required. The helm release name that you specify
                               when you run the `helm install <RELEASE_NAME>`
                               command. You can see the <RELEASE_NAME> by using
                               the `helm list` command.
      --tls                  Whether wire encryption (TLS) between scalar-admin
                               and the target is enabled.
  -w, --max-pause-wait-time=<maxPauseWaitTime>
                             The max wait time (in milliseconds) until Scalar
                               products drain outstanding requests before they
                               pause. If omitting this option, the max wait
                               time will be the default value defined in the
                               products. Most Scalar products have the default
                               value of 30 seconds.
  -z, --time-zone=<zoneId>   Specify a time zone ID, e.g., Asia/Tokyo, to
                               output successful paused period. Note the time
                               zone ID is case sensitive. Etc/UTC by default.
```

## Run the CLI tool in a Kubernetes environment

The `scalar-admin-for-kubernetes` CLI tool executes Kubernetes APIs in its internal processes. To run those Kubernetes APIs, you must run the `scalar-admin-for-kubernetes` CLI tool as a pod on the Kubernetes environment by following the steps below:

1. Create three Kubernetes resources (`Role`, `RoleBinding`, and `ServiceAccount`), replacing the contents in the angle brackets as described:

   * Role

     ```yaml
     apiVersion: rbac.authorization.k8s.io/v1
     kind: Role
     metadata:
       name: scalar-admin-for-kubernetes-role
       namespace: <YOUR_NAMESPACE>
     rules:
       - apiGroups: ["", "apps"]
         resources: ["pods", "deployments", "services"]
         verbs: ["get", "list"]
     ```

   * RoleBinding

     ```yaml
     apiVersion: rbac.authorization.k8s.io/v1
     kind: RoleBinding
     metadata:
       name: scalar-admin-for-kubernetes-rolebinding
       namespace: <YOUR_NAMESPACE>
     subjects:
       - kind: ServiceAccount
         name: scalar-admin-for-kubernetes-sa
     roleRef:
       kind: Role
       name: scalar-admin-for-kubernetes-role
       apiGroup: rbac.authorization.k8s.io
     ```

   * ServiceAccount

     ```yaml
     apiVersion: v1
     kind: ServiceAccount
     metadata:
       name: scalar-admin-for-kubernetes-sa
       namespace: <YOUR_NAMESPACE>
     ```

1. Mount the `ServiceAccount` resource on the `scalar-admin-for-kubernetes` pod, replacing the contents in the angle brackets as described:

   * Pod

     ```yaml
     apiVersion: v1
     kind: Pod
     metadata:
       name: scalar-admin-for-kubernetes
       namespace: <YOUR_NAMESPACE>
     spec:
       serviceAccountName: scalar-admin-for-kubernetes-sa
       containers:
       - name: scalar-admin-for-kubernetes
         image: ghcr.io/scalar-labs/scalar-admin-for-kubernetes:1.0.0
         command:
           - java
           - -jar
           - /app.jar
           - -r
           - <HELM_RELEASE_NAME>
           - -n
           - <SCALAR_PRODUCT_NAMESPACE>
           - -d
           - <PAUSE_DURATION>
           - -z
           - <TIMEZONE>
     ```

## Run the CLI tool in a Kubernetes environment by using a Helm Chart

For details on how to use a Helm Chart to deploy Scalar Admin for Kubernetes in a Kubernetes environment, see the following documentation based on the product you're using:

### For ScalarDB

1. [Configure a custom values file for Scalar Admin for Kubernetes](https://scalardb.scalar-labs.com/docs/latest/helm-charts/configure-custom-values-scalar-admin-for-kubernetes/)
1. [How to deploy Scalar Admin for Kubernetes](https://scalardb.scalar-labs.com/docs/latest/helm-charts/how-to-deploy-scalar-admin-for-kubernetes/)

### For ScalarDL

1. [Configure a custom values file for Scalar Admin for Kubernetes](https://scalardl.scalar-labs.com/docs/latest/helm-charts/configure-custom-values-scalar-admin-for-kubernetes/)
1. [How to deploy Scalar Admin for Kubernetes](https://scalardl.scalar-labs.com/docs/latest/helm-charts/how-to-deploy-scalar-admin-for-kubernetes/)

### License

Scalar Admin for Kubernetes is licensed under the Apache 2.0 License (found in the LICENSE file in the root directory).
