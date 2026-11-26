# 0007. Kubernetes and GitOps as the target, with only the foundation in this repository

## Release

0.3.0

## Status

accepted

## Context

JayPay ran only as a Docker Compose stack. The target is a Kubernetes cluster driven by GitOps. This repository is public, and account ids, domain names, secrets and Terraform state must stay out of it.

## Options

The roadmap left four choices open: the GitOps controller, the cluster flavour for the cloud, the secret delivery and the operators for the stateful dependencies. For the local cluster:

- Operators such as CloudNativePG and Strimzi for PostgreSQL and Kafka, or plain StatefulSets.
- A Git server in the cluster, pushing a branch to GitHub, or a snapshot of the working tree in a local bare repository as the GitOps source.

## Decision

This repository holds the foundation only. A private copy holds the real environment and deploys, tests and tears it down. The work runs in phases that each end in a check that can be rerun. 0.3.0 shipped phases 1 to 4: Helm charts checked by the gate, `make k8s-up` on a local minikube cluster, Argo CD syncing from `deploy/envs/local` and proven by `make k8s-gitops-check`, and the observability stack with a default-deny NetworkPolicy proven by `make k8s-policy-check`. The dependencies run as plain StatefulSets, and Argo CD reads a working-tree snapshot from a local bare repository. Every new component needs the owner's approval before it is added.

## What we gave up

- Failover for the dependencies. Two operators would add memory on a 10 GiB node for one broker and one database that need no failover.
- `deploy/envs/local` and `compose.yaml` describe the same stack twice, so the gate compares them.
- Argo CD syncs all Applications at once, so a service that starts too early restarts, at most once in the recorded runs.
- The NetworkPolicy restricts ingress only.

## When to decide again

At phase 5, the cloud foundation, when the open choices of the roadmap have to be made.

## Links

- Issue [#71](https://github.com/chrysaor/JayPay/issues/71), [#77](https://github.com/chrysaor/JayPay/issues/77), [#79](https://github.com/chrysaor/JayPay/issues/79), [#81](https://github.com/chrysaor/JayPay/issues/81), [#83](https://github.com/chrysaor/JayPay/issues/83)
- PR [#78](https://github.com/chrysaor/JayPay/pull/78), [#80](https://github.com/chrysaor/JayPay/pull/80), [#82](https://github.com/chrysaor/JayPay/pull/82), [#87](https://github.com/chrysaor/JayPay/pull/87)
