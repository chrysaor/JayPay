# 0034. PostgreSQL gets a memory limit of 512 MiB

## Release

0.6.0

## Status

accepted

## Context

After [0033](0033-three-postgres-connections-per-service.md) the postgres container used about half of its 256 MiB limit on Compose, but one run on Kubernetes ended at 194 MiB, 76%, where `make smoke` fails at 80%.

Measured on the local cluster in the cgroup of the container:

- Three smoke runs in a row stayed between 154 and 160 MiB, so the stack was not the difference.
- PostgreSQL maps about 150 MiB of shared memory for `shared_buffers` (128 MB) and its other shared structures, and it counts against the limit only as the data is read. Idle, 62 MiB of it was in use.
- Reading a 176 MB table took the shared memory to 147 MiB and the working set from 155 to 242 MiB, 94% of the limit. The 194 MiB of the earlier run was a point on that way.
- With Keycloak at its ten connections the worst case is about 255 MiB, the limit itself.

## Options

- Lower `shared_buffers` to 48 MB and keep the limit. Tried on the cluster: 65 MiB idle. It fits the memory by cutting what the database may cache, and has to be changed again when the data grows.
- Raise the limit to 512 MiB and keep the PostgreSQL defaults.

## Decision

The limit is 512 MiB in `infra/docker/compose.yaml` and in `deploy/envs/local`, and the PostgreSQL settings stay at their defaults. The owner proposed it: the stack takes about 6.3 GiB where Docker has 11.7, and PostgreSQL is the one workload every service depends on.

| Kubernetes, 512 MiB | Working set | Of the limit |
| --- | --- | --- |
| Idle | 87 MiB | 17% |
| Right after the smoke | 102 MiB | 20% |
| Shared buffers full | 221 MiB | 43% |

The check in `make smoke` stays at 80%, now 410 MiB.

## What we gave up

- The pods of the local cluster now request 11,190 MiB of the 11,934 MiB the node can give, 93%. About 740 MiB remain for another workload.

## When to decide again

When the smoke check fails, or when the node has no room for a workload that is added.

## Links

- Issue [#217](https://github.com/chrysaor/JayPay/issues/217)
