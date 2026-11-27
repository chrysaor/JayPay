# 0033. Each service keeps three PostgreSQL connections, and the memory limit of PostgreSQL stays

## Release

0.6.0 (unreleased)

## Status

accepted

## Context

The postgres container ran at 250 of its 256 MiB limit on the CI runner. On Compose a container over its limit swaps, on Kubernetes it is killed, and every service depends on this database.

Measured on the Compose stack in the cgroup of the container, with `pg_stat_activity` and the `smaps_rollup` of each PostgreSQL process:

- No service set a pool size, so HikariCP kept its default fixed pool of ten. Nine services held 90 idle connections and Keycloak 3, 93 of `max_connections` 100.
- Each idle connection is a backend process with about 1.5 MiB of private memory and 0.4 MiB of page tables and kernel memory. The 93 backends held 150 of the 153 MiB of anonymous memory.
- Shared memory was 56 MiB of the 153 MiB that PostgreSQL maps for `shared_buffers` (128 MB) and its other shared structures. It grows as more of the data is read, so the worst case was about 350 MiB.
- During the smoke, no pool had more than 2 connections in use at once and no request waited for one, in Prometheus and in repeated samples of `pg_stat_activity`. After the change the longest wait for a connection in the smoke was 19 ms.

## Options

- Raise the limit in `infra/docker/compose.yaml` and `deploy/envs/local`. It keeps 93 idle processes that do nothing.
- Lower `shared_buffers`. Shared memory was not what filled the limit.
- Keep fewer connections per service. This is what took the memory.
- Put a connection pooler in front of PostgreSQL. It is a new workload, and three connections per service already fit.

## Decision

`jaypay-common.yml` sets `spring.datasource.hikari.maximum-pool-size` to 3 for every service, with the minimum idle left at the same value, so the pool stays fixed. Three is the highest use measured plus one. The limit and the PostgreSQL settings stay.

| Compose | Before | After |
| --- | --- | --- |
| Connections held | 93 | 30 |
| Memory, idle | 244 MiB, 95% | 125 MiB, 49% |
| Memory, right after the smoke | 252 MiB, 99% | 132 MiB, 51% |
| Anonymous memory, idle | 153 MiB | 61 MiB |

Memory is the working set, the cgroup's `memory.current` less its inactive file cache, which is what Kubernetes counts against the limit. The smoke added 0 to 7 MiB in each run. With every pool and Keycloak full, 37 connections, and the shared buffers full, the container would use about 240 MiB.

`make smoke` reads the same value from the postgres container on Compose and on Kubernetes and fails above 80% of the limit. That leaves about 70 MiB for the shared buffers to grow before the check fails, and it fails 50 MiB before the limit. Back at ten connections per service the container would be at 95% or more and fail it.

## What we gave up

- A service with more than three requests that need the database at the same moment waits for a connection. The wait is visible as `hikaricp_connections_pending`.
- If all shared buffers fill and every pool is full, only about 16 MiB remain under the limit.

## When to decide again

When `hikaricp_connections_pending` stays above zero for a service, when a service or a database is added, or when the data grows past `shared_buffers` and the smoke check fails.

## Links

- Issue [#215](https://github.com/chrysaor/JayPay/issues/215)
