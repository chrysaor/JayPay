# Kubernetes packaging

This directory packages every workload that the repository builds as Helm charts: the eleven Spring Boot services and the two nginx sites. Nothing here is deployed yet. `make verify STEPS=charts` renders every workload and checks the result. The cluster, the dependencies and GitOps come in later phases of [the roadmap](https://github.com/chrysaor/JayPay/issues/71).

## Layout

```
deploy/
  charts/
    spring-service/          one chart for all eleven Spring Boot services
    frontend/                one chart for frontend-web and frontend-admin
      ci/ingress-values.yaml values that only the gate renders, to cover the Ingress template
  envs/
    local/
      spring-service.yaml    shared by every spring-service workload in local
      frontend.yaml          shared by every frontend workload in local
      spring-service/
        <workload>.yaml      one file per service, named after its Compose service
      frontend/
        <workload>.yaml      frontend-web.yaml and frontend-admin.yaml
```

The path of a workload file says everything needed to render it. In `deploy/envs/<env>/<chart>/<workload>.yaml`, the directory names the chart and the file name is the release name. The release name is also the name of the Deployment, the Service and the ServiceAccount, and it picks the image.

Helm renders a workload with two values files, in this order:

1. `deploy/envs/<env>/<chart>.yaml`, the settings that every workload of that chart shares in the environment.
2. `deploy/envs/<env>/<chart>/<workload>.yaml`, the settings of the workload.

Helm merges maps, so a workload file adds keys to `env` and `secretEnv` from the shared file and overrides the ones it repeats.

An Argo CD ApplicationSet can turn the layout into one Application per workload. Use a Git files generator on `deploy/envs/<env>/*/*.yaml`. For each file, take the chart from the directory name, the release name and the Application name from the file name, and pass the two values files above.

## Values

The `values.yaml` of each chart lists every key with its default, and `values.schema.json` rejects a key that the chart does not know.

| Key | Chart | Meaning |
| --- | --- | --- |
| `image.registry` | both | Registry and owner. The image is `<registry>/jaypay-<release>:<tag>`. The default is `ghcr.io/chrysaor`. |
| `image.tag` | both | No default. The environment sets it. It must be a release tag such as `v0.2.0` or a release candidate tag such as `0.2.0-rc.1a2b3c4`, the two forms that `release.yml` publishes. |
| `image.pullPolicy` | both | `IfNotPresent` by default. |
| `replicas` | both | 1 by default. |
| `env` | spring-service | Plain environment variables, by name. |
| `secretEnv` | spring-service | Environment variables read from the Secret, as `NAME: SECRET_KEY`. |
| `secretName` | spring-service | The Secret that `secretEnv` reads. `jaypay` by default. |
| `upstreams` | frontend | The backend that each `JAYPAY_<SERVICE>_URL` of the nginx template proxies to, as a Service name. |
| `ingress.enabled`, `ingress.className`, `ingress.host` | frontend | An Ingress to the site. Off by default. |
| `resources` | both | Requests and limits. The memory limit equals the `mem_limit` of the workload in `compose.yaml`. |

`deploy/envs/local` gives every workload the environment that `infra/docker/compose.yaml` gives it. The addresses of the dependencies are Service names that the local cluster phase still has to create: `kafka:9092`, `axon-server:8124`, `http://otel-collector:4318`, `postgres:5432`, `vault` and `http://dynamodb:8000`.

## Secrets

The charts never create a Secret. Each spring-service workload reads its secret values from the Secret named by `secretName`, and `secretEnv` maps an environment variable to a key in that Secret.

The keys are the keys of `infra/docker/.env.example`, the same names that Compose interpolates. The gate fails when a workload reads a key that `.env.example` does not have. A local cluster can create the Secret from the file that `scripts/init-env.py` writes:

```sh
kubectl create secret generic jaypay --namespace jaypay --from-env-file=infra/docker/.env
```

## What every pod gets

- A ServiceAccount of its own with `automountServiceAccountToken: false`. No workload calls the Kubernetes API.
- `enableServiceLinks: false`. Kubernetes would otherwise add variables such as `KAFKA_PORT` for every Service in the namespace. A Service named `server` would add `SERVER_PORT`, which Spring Boot reads as `server.port`.
- `runAsNonRoot` with the user of the image, 10001 for the services and 101 for nginx. Also `seccompProfile: RuntimeDefault`, `allowPrivilegeEscalation: false`, every capability dropped, and a read-only root file system.
- An `emptyDir` at `/tmp`, where Tomcat and the JVM write. The nginx pods also get one at `/etc/nginx/conf.d`, where the image's entrypoint writes `default.conf` from its template at start.
- A memory request equal to the memory limit, and a CPU request without a CPU limit. The JVM sizes its heap from the memory limit, because `backend/Dockerfile` starts it with `-XX:MaxRAMPercentage=50`.

The Spring Boot services expose `/actuator/health/liveness` and `/actuator/health/readiness` without extra configuration. `TaskConsumerApplicationTest` pins both paths. The startup probe allows 300 seconds before Kubernetes restarts the container. That is longer than the 180-second `start-period` of the `HEALTHCHECK` in `backend/Dockerfile`, because on a small cluster all eleven JVMs start at once and share the CPUs. The liveness and readiness probes take over after the startup probe passes. The nginx pods probe `/`, like the Compose healthcheck.

## Admin console and auth-service in a cluster

The nginx template of each site resolves its upstreams at request time with `resolver ${NGINX_LOCAL_RESOLVERS}`. The image fills that variable from the `nameserver` lines of `/etc/resolv.conf`, which in a pod is the cluster DNS Service. The nginx resolver ignores the `search` domains of `/etc/resolv.conf`, so a short name such as `auth-service` does not resolve in a cluster. The image's default URLs, such as `http://auth-service:8080`, work only in Compose, where Docker's DNS answers short names.

The frontend chart therefore turns every entry of `upstreams` into a full name in the release namespace. For `JAYPAY_AUTH_URL: auth-service` in namespace `jaypay`, nginx gets `http://auth-service.jaypay.svc.cluster.local:8080`. The `auth_request` to `/authorize` and the proxy to `/api/auth/staff/` use the same URL. The chart assumes the default cluster domain `cluster.local`, which minikube and EKS use.

## The gate

`make verify STEPS=charts` runs these steps. It runs Helm and kubeconform from images pinned by digest in `scripts/verify.sh`.

1. `scripts/render-charts.sh` lints every chart with `helm lint --strict` for each file in `deploy/charts/<chart>/ci/` and for each workload in `deploy/envs`, and renders each one with `helm template` into `build/charts/<env>/<workload>.yaml`.
2. kubeconform validates everything in `build/charts` against the Kubernetes schemas, in strict mode, so a field that Kubernetes does not know fails.
3. `scripts/check-deploy.py` compares the rendered `local` workloads with `infra/docker/compose.yaml`.

The comparison checks the facts that break a workload when they drift. It leaves the values of plain settings to the environment, because a cluster reaches its dependencies at other addresses than Compose does.

- The Compose services that are a JayPay workload and the workloads in `deploy/envs/local` are the same set.
- Every workload gets the environment variable names that Compose passes to it, and no other names. A site also gets one `JAYPAY_<SERVICE>_URL` for every upstream in its nginx template.
- A variable that Compose interpolates from `.env`, such as `${POSTGRES_PASSWORD}`, comes from the Secret key with the same name. A variable that Compose sets in plain text is not a secret.
- Every Secret key is in `infra/docker/.env.example`.
- Every upstream of a site names a Service that `deploy/envs/local` renders.
- The memory limit equals `mem_limit` in Compose.

`make verify-selftest` plants a field Kubernetes does not know, a `latest` tag, a missing workload, a missing variable and a wrong upstream, and checks that the step fails on each one.

## Next phases

1. Local cluster. `make k8s-up` starts minikube with PostgreSQL, Kafka, Axon Server, Vault, DynamoDB Local, ClickHouse and the workloads, and `make k8s-smoke` runs `scripts/smoke.sh` through an ingress. `release.yml` publishes `linux/amd64` images only, so a cluster on an arm64 machine needs locally built images or multi-architecture release images.
2. GitOps. Argo CD syncs the local cluster from `deploy/envs/local`, one Application per workload file.
3. Observability and policy. The OpenTelemetry Collector, Prometheus and Grafana run in the cluster, and NetworkPolicy lets only the admin console reach the backends it proxies.
4. Cloud. Terraform modules for the network, an EKS cluster, workload identity for DynamoDB and the secret store, validated in the gate and applied only from a private copy of this repository.
