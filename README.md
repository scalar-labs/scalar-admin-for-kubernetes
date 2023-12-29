# Scalar Admin for Kubernetes

Scalar Admin for Kubernetes is a tool that creates a paused state for ScalarDB or ScalarDL in a Kubernetes environment. You can use such a paused state to take backups easily and consistently across multiple diverse databases.

## Usage of the CLI tool

```console
Usage: scalar-admin-for-kubernetes-cli [-h] [-d=<pauseDuration>]
                                       [-n=<namespace>] -r=<helmReleaseName>
                                       [-w=<maxPauseWaitTime>] [-z=<zoneId>]
Scalar Admin pause tool for the Kubernetes environment
  -d, --pause-duration=<pauseDuration>
                             The duration of the pause period by millisecond.
                               5000 (5 seconds) by default.
  -h, --help                 Display the help message.
  -n, --namespace=<namespace>
                             Namespace that Scalar products you want to pause
                               are deployed. `default` by default.
  -r, --release-name=<helmReleaseName>
                             This option is required. Helm's release name that
                               you specify when you run the `helm install
                               <RELEASE_NAME>` command. You can see the
                               <RELEASE_NAME> by using the `helm list` command.
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

Coming soon.
