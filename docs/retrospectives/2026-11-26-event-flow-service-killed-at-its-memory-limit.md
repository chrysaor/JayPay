# event-flow-service was killed at its memory limit on the cluster

## What happened

On the local Kubernetes cluster event-flow-service was killed for using too much memory about every two minutes. While it restarted, the Event flow page missed steps, and `make k8s-smoke` failed at the remittance flow. It was found by the Kubernetes smoke that runs before a release, after the service had been on `develop` for two PRs.

## Cause

The service was added with a memory limit of 384m, taken from logging-consumer, which has no database and no web controllers. event-flow-service has both and needs more: it reached 382Mi within half a minute of starting, and its peak under the smoke is 418Mi. settlement-service, a service of the same shape, uses 368Mi under the default limit of 512m. Nobody measured the memory of the new service before choosing the limit.

## Why the checks missed it

Both PRs were checked with `make smoke` on the Compose stack only, and it passed there with the same limit. A Compose memory limit lets a container swap when it goes over, and a Kubernetes limit kills it. The `charts` step of the gate compares the limits of the two stacks, so they agreed, and both were wrong. CI runs neither smoke.

## Fix

[#157](https://github.com/chrysaor/JayPay/pull/157) gives event-flow-service the default limit of 512m in `compose.yaml` and in `deploy/envs/local`.

## What changed so it is caught next time

- `CLAUDE.md` says to run `make k8s-up` and `make k8s-smoke` before merging a PR that adds a workload or changes a memory limit.
- [#143](https://github.com/chrysaor/JayPay/issues/143) puts the smoke into CI.
- [#109](https://github.com/chrysaor/JayPay/issues/109) tracks what a service spends outside its heap, which decides how small a limit can be.
