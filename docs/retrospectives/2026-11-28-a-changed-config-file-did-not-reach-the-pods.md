# A changed configuration file did not reach the pods of the local cluster

## What happened

[#240](https://github.com/chrysaor/JayPay/issues/240) removed auth-service and its line from `prometheus.yml`. After `make k8s-up` on the running cluster, `make k8s-smoke` failed: Prometheus still scraped auth-service and reported it down. The ConfigMap `prometheus-files` in the cluster had the new file, and the file inside the Prometheus pod was the old one. Compose was not affected, and a cluster started from nothing was not either.

## Cause

The dependency chart mounts each configuration file from a ConfigMap with `subPath`. Kubernetes never updates such a mount in a running pod, and nothing in the pod template changed with the file, so Argo CD synced the ConfigMap and left the pod alone. This held for every dependency that reads files: PostgreSQL, Keycloak, Vault, ClickHouse, DynamoDB, Grafana, the Collector and Prometheus.

## Why the checks missed it

- `make verify STEPS=charts` compares what is rendered with Compose. The render was right. The fault is in what a running cluster does with a second render.
- `make k8s-smoke` usually ran after `make k8s-down` and `make k8s-up`, or after changes that touched no mounted file, so a pod never had to pick up a changed one. The earlier changes to `prometheus.yml` added a target with a new workload, and a missing target fails the same check, but those runs started from a new cluster.

## Fix

[#247](https://github.com/chrysaor/JayPay/pull/247) puts the hash of a dependency's files on its pod template as the annotation `checksum/files`. A changed file changes the template, and the StatefulSet replaces the pod.

## What changed so it is caught next time

- `scripts/check-deploy.py` fails when a rendered Deployment or StatefulSet mounts a ConfigMap and its pod template has no `checksum/files`.
- `make verify-selftest` removes the annotation from the chart and checks that the gate fails.
- Nothing yet runs the smoke test on a cluster that was upgraded in place by CI. The local runs do, because `make k8s-up` is run again on a running cluster for every change.
