# 0038. Restarts on a cold start of the cluster are accepted

## Release

0.6.0

## Status

accepted

## Context

On a fresh `make k8s-up` every workload starts at once. A workload that reaches its dependency before the dependency is ready exits, and Kubernetes restarts it ([#205](https://github.com/chrysaor/JayPay/issues/205)):

- otel-collector exits while ClickHouse refuses connections. It restarted twice on every cold start that was watched.
- A Spring service exits when PostgreSQL does not accept connections yet. membership-service restarted once on one cold start of three, and the path was reproduced with PostgreSQL scaled to zero.
- Vault is not part of it. With Vault scaled to zero a new membership-service pod started and stayed up.

The stack ends up healthy and `make k8s-smoke` passes. The cost is start time and restart counts that look like faults.

## Options

- A: accept the restarts and write them down.
- B: make each workload wait for its dependency, with an init container or a retry while the context starts.

## Decision

A. The owner chose it. A pod that exits and is started again until its dependency answers is how Kubernetes brings a set of workloads up, and B puts the waiting into every chart.

`deploy/README.md` says under "Order of the dependencies" that a restart count above zero right after a cold start is not a fault.

## What we gave up

- A cold start takes longer than it would with ordered starts, and the restart counts of otel-collector and of some services start above zero.
- A reader of `kubectl get pods` has to know this to tell it from a crash loop.

## When to decide again

When a workload does not become healthy after its restarts, or when a restart count is used for an alert.

## Links

- Issue [#205](https://github.com/chrysaor/JayPay/issues/205)
