# Components

Everything this repository runs or depends on, what it is for, where it runs and which file pins its version. Versions are not repeated here, the file in the last column is the only place to change one.

A new component is added to this page in the PR that introduces it.

## Application

| Component | Role | Version pinned in |
| --- | --- | --- |
| Java | Language of the eleven services. The images run a `jlink` runtime cut from the Temurin JDK with only the modules the services load | `backend/gradle/libs.versions.toml`, `backend/Dockerfile` |
| Distroless base, glibc without OpenSSL | Base of the service images: glibc, CA certificates, time zones and `/etc/passwd`, no shell or package manager | `backend/Dockerfile` |
| Debian | Source of the `C.UTF-8` locale the service images copy, the same glibc as the distroless base | `backend/Dockerfile` |
| Gradle | Backend build | `backend/gradle/wrapper/gradle-wrapper.properties` |
| Spring Boot | Web, data access, Kafka, Actuator | `backend/gradle/libs.versions.toml` |
| Spring Security | Resource server in auth-service that checks Keycloak tokens, the roles and the admin gate | Spring Boot BOM |
| Spring Vault | Reads the encryption key of membership-service | `backend/gradle/libs.versions.toml` |
| Axon Framework | Event sourcing and the money top-up saga | `backend/gradle/libs.versions.toml` |
| AWS SDK for Java | DynamoDB access of money-query-service | `backend/gradle/libs.versions.toml` |
| JJWT | Customer tokens | `backend/gradle/libs.versions.toml` |
| springdoc | OpenAPI documents of the services, under the Spring profile `local` only | `backend/gradle/libs.versions.toml` |
| Micrometer and the OpenTelemetry Logback appender | Metrics, traces and logs out of every service | Spring Boot BOM, `backend/gradle/libs.versions.toml` |
| ArchUnit, Testcontainers | Hexagonal layer rules and tests against real PostgreSQL and Kafka | `backend/gradle/libs.versions.toml`, Spring Boot BOM |
| React, TypeScript, Vite, Vitest | The customer site and the admin console | `frontend/package.json` and the `package.json` of each app |
| TanStack Query | Server state in both sites | `frontend/apps/*/package.json` |
| Node | Frontend build | `frontend/Dockerfile` |
| nginx | Serves both sites and proxies `/api`, runs the admin gate through `auth_request` | `frontend/Dockerfile` |

## Stateful dependencies and messaging

They run in the compose stack and, as StatefulSets, in the local cluster. The cluster reads the image from `deploy/envs/local/dependency/<name>.yaml`, and `make verify` fails when it differs from compose.

| Component | Role | Version pinned in |
| --- | --- | --- |
| PostgreSQL | One database per service with data, and one for Keycloak | `infra/docker/compose.yaml` |
| Keycloak | Staff accounts and sign-in of the admin console: the realm `staff` with the Authorization Code flow and PKCE, brute force protection, the password policy and optional authenticator apps. Keeps its data in the `keycloak` database of PostgreSQL | `infra/docker/compose.yaml` |
| Apache Kafka | Logging events and the flow events of saga top-ups between services | `infra/docker/compose.yaml` |
| Axon Server | Event store and command and event routing | `infra/docker/compose.yaml` |
| HashiCorp Vault | Holds the encryption key of membership-service, which reads it with a token that may read nothing else, dev mode | `infra/docker/compose.yaml` |
| DynamoDB Local | Regional money totals, stands in for the AWS table | `infra/docker/compose.yaml` |
| ClickHouse | Stores logs and traces | `infra/docker/compose.yaml` |
| Kafbat UI | Kafka console, compose only | `infra/docker/compose.yaml` |

## Observability

The local cluster mounts the same configuration files as compose, and `make verify` fails when a cluster image differs from compose.

| Component | Role | Runs in | Version pinned in |
| --- | --- | --- | --- |
| OpenTelemetry Collector | Receives logs and traces over OTLP and writes them to ClickHouse | compose, local cluster | `infra/docker/otel-collector/Dockerfile`, `deploy/envs/local/dependency/otel-collector.yaml` |
| Prometheus | Scrapes `/actuator/prometheus` on the management port 9080 | compose, local cluster | `infra/docker/compose.yaml`, `deploy/envs/local/dependency/prometheus.yaml` |
| Grafana | Dashboards over ClickHouse, read as the read-only user `grafana`, and Prometheus. Anonymous visitors are viewers | compose, local cluster | `infra/docker/compose.yaml`, `deploy/envs/local/dependency/grafana.yaml` |

## Kubernetes and GitOps

| Component | Role | Runs in | Version pinned in |
| --- | --- | --- | --- |
| minikube | Local cluster, profile `jaypay` | developer machine | installed by the developer, Kubernetes version in `scripts/k8s.sh` |
| ingress-nginx | Ingress of the two sites | local cluster, minikube addon | minikube |
| kindnet | Pod network that enforces NetworkPolicy, started with `--cni kindnet` | local cluster | minikube |
| Helm | Renders the charts in `deploy/charts` | gate, Argo CD | `scripts/verify.sh` |
| Argo CD, core install | Syncs the cluster from `deploy/envs/<env>` | local cluster | `scripts/verify.sh`, with the checksum of the install manifest |
| kubectl | Applies the bootstrap manifests and builds the Argo CD overlay | developer machine, gate | installed by the developer, `scripts/verify.sh` |
| kubeconform | Validates rendered manifests against the Kubernetes and Argo CD schemas | gate | `scripts/verify.sh` |
| BusyBox | Probe pods of `make k8s-policy-check`, and the `wget` of the health checks in the service and Collector images | local cluster, compose | `infra/docker/otel-collector/Dockerfile`, `backend/Dockerfile` |

Approved for the cloud phases and not in the repository yet: External Secrets with AWS Secrets Manager, CloudNativePG, Strimzi, Amazon EKS.

## Infrastructure as code

| Component | Role | Version pinned in |
| --- | --- | --- |
| Terraform | The AWS side, today the DynamoDB table | `scripts/verify.sh`, `infra/terraform/envs/dev` |
| Terraform AWS provider | AWS resources | `infra/terraform/envs/dev`, `.terraform.lock.hcl` |
| Docker Compose | The local stack | installed by the developer |

## Gate, security and delivery

| Component | Role | Version pinned in |
| --- | --- | --- |
| actionlint | Checks the workflows | `scripts/verify.sh` |
| Trivy | Filesystem, config and image scans | `Makefile`, `.github/workflows` |
| gitleaks | Secret scan over the whole history | `Makefile`, `.github/workflows` |
| CodeQL | Static analysis of Java and TypeScript | `.github/workflows` |
| Dependabot | Dependency update PRs | `.github/dependabot.yml` |
| GitHub Actions | CI, release candidates and releases | `.github/workflows` |
| GitHub Container Registry | Published images `ghcr.io/chrysaor/jaypay-<name>` | `.github/workflows/release.yml` |
