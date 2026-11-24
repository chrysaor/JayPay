# Security

## Reporting a vulnerability

Report vulnerabilities privately through GitHub's "Report a vulnerability" button on the Security tab. Do not open a public issue.

## Automated checks

| Check | Workflow | Runs on |
| --- | --- | --- |
| CodeQL (`java-kotlin`, `javascript-typescript`) | `security.yml` | PR, push to `master`, weekly |
| Dependency review | `security.yml` | PR |
| Trivy filesystem scan (vulnerabilities and misconfigurations, SARIF to code scanning) | `security.yml` | PR, push to `master`, weekly |
| gitleaks secret scan | `security.yml` | PR (PR commits only), push to `master` (pushed commits only), weekly (full history) |
| Dependabot version updates (gradle, npm, github-actions, docker, docker-compose, terraform) | `dependabot.yml` | Weekly, grouped |
| Backend build and tests, frontend lint, tests and build | `ci.yml` | PR and push to `master`, only when `backend/` or `frontend/` changes |

The weekly run is Monday 03:00 KST.

Container image scanning goes in `security.yml` as a Trivy `image` job once the image build is defined.

## Known findings

`.github/.gitleaksignore` lists findings that stay in git history after the secret was removed and rotated. Each entry is a single gitleaks fingerprint, so the rule keeps catching every other match. Add an entry only after rotating the secret.

## Running the checks locally

From the repository root:

```sh
docker run --rm -v "$PWD:/repo" -w /repo aquasec/trivy:0.76.2 fs --scanners vuln,misconfig .
docker run --rm -v "$PWD:/repo" ghcr.io/gitleaks/gitleaks:v8.30.1 git /repo --redact --verbose --gitleaks-ignore-path /repo/.github/.gitleaksignore
```

The gitleaks command scans full history, like the weekly run. Add `--log-opts=origin/master..HEAD` to scan only your branch, like a PR run.
