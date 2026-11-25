# Security

## Reporting a vulnerability

Report vulnerabilities privately through GitHub's "Report a vulnerability" button on the Security tab. Do not open a public issue.

## Automated checks

| Check | Workflow | Runs on |
| --- | --- | --- |
| CodeQL (`java-kotlin`, `javascript-typescript`) | `security.yml` | PR, push to a long-lived branch, weekly |
| Dependency review | `security.yml` | PR |
| Trivy filesystem scan (vulnerabilities and misconfigurations, SARIF to code scanning) | `security.yml` | PR, push to a long-lived branch, weekly |
| gitleaks secret scan | `security.yml` | PR (PR commits only), push to a long-lived branch (pushed commits only), weekly (full history) |
| Trivy image scan of every service image and both frontend images, fails on HIGH or CRITICAL | `release.yml` | Push to `release/**`, push of a `v*` tag |
| Dependabot version updates (gradle, npm, github-actions, docker, docker-compose, terraform) | `dependabot.yml` | Weekly, grouped, PRs into `develop` |
| `make verify` steps: contract diff, English-only and env checks, terraform, actionlint, Helm charts, backend, frontend | `ci.yml` | PR and push to a long-lived branch; backend and frontend only when they change |

Long-lived branches are `master`, `develop` and `release/**`.

The weekly run is Monday 03:00 KST.

## Admin console access

The admin console requires a staff account. auth-service stores each account with a salted bcrypt hash through Spring Security's `DelegatingPasswordEncoder`, and nginx asks auth-service about every `/api` request before it forwards it. The roles viewer, operator and admin, the decision table and the password rules are described in [infra/docker/README.md](infra/docker/README.md#staff-accounts) and [docs/architecture.md](docs/architecture.md#admin-console-request-through-auth-service).

The first three accounts come from `STAFF_*` keys in `infra/docker/.env`, which `scripts/init-env.py` generates and git ignores. The repository holds no default username or password.

What the gate does not cover:

- The service ports `127.0.0.1:8081` to `8089` reach the services without a staff token.
- The customer site on 8090 forwards member edits and Mark settled without a login.
- Staff sign-in has no rate limit or lockout, and the MFA flag does not change sign-in yet.
- A staff token stays valid for 8 hours. Signing out only drops it in the browser, but a deactivation stops it on the next request.

## Known findings

`.github/.gitleaksignore` lists findings that stay in git history after the secret was removed and rotated. Each entry is a single gitleaks fingerprint, so the rule keeps catching every other match. Add an entry only after rotating the secret.

## Running the checks locally

From the repository root, `make scan` runs both of these:

```sh
docker run --rm -v "$PWD:/repo" -w /repo aquasec/trivy:0.76.2 fs --scanners vuln,misconfig .
docker run --rm -v "$PWD:/repo" ghcr.io/gitleaks/gitleaks:v8.30.1 git /repo --redact --verbose --gitleaks-ignore-path /repo/.github/.gitleaksignore
```

The gitleaks command scans full history, like the weekly run. Add `--log-opts=origin/develop..HEAD` to scan only your branch, like a PR run.

`make images scan-images` builds every image and runs the same image scan as `release.yml`.
