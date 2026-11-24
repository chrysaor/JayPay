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
| Trivy image scan of every service image and the frontend image, fails on HIGH or CRITICAL | `release.yml` | Push to `release/**`, push of a `v*` tag |
| Dependabot version updates (gradle, npm, github-actions, docker, docker-compose, terraform) | `dependabot.yml` | Weekly, grouped, PRs into `develop` |
| `make verify` steps: contract diff, terraform, actionlint, backend, frontend | `ci.yml` | PR and push to a long-lived branch; backend and frontend only when they change |

Long-lived branches are `master`, `develop` and `release/**`.

The weekly run is Monday 03:00 KST.

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
