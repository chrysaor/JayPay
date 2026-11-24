# CLAUDE.md

Rules for agents working in this repository. Every command runs from the repository root through `make`. Run `make` with no target to list them.

## Layout

| Area | Path | Commands |
| --- | --- | --- |
| Backend, Gradle 9, Java 25, Spring Boot 4 | `backend/` | `make build`, `make test`, `make images`, `make lock`, `make contract` |
| Frontend, React, Vite, npm | `frontend/` | `make build`, `make test`, `make images` |
| Local stack, Docker Compose | `infra/docker/` | `make up`, `make down` |
| AWS, Terraform | `infra/terraform/` | `make verify STEPS=terraform`, see `infra/terraform/README.md` |

Docker must be running. Backend tests start PostgreSQL through Testcontainers, and terraform, actionlint, trivy and gitleaks run from their official images.

## The gate

`make verify` runs the endpoint contract diff, terraform fmt and validate, actionlint, the frontend lint, tests and build, and the backend build with tests. It exits non-zero when any step fails and prints `FAILED: <steps>`. `make verify STEPS="contract backend"` runs a subset. CI calls the same target.

Do not report work as done until `make verify` passes. Changed an HTTP endpoint on purpose? Run `make contract` and commit `docs/api-contract.md`.

## Workflow

The main branch (currently named `master`) holds released code. `develop` is the integration branch.

1. Start from an issue. Ask for one if the task has none.
2. Branch from `develop` as `feature/<issue>-<short-name>`, for example `feature/40-workflow-release`.
3. Write the failing test first, then the code that makes it pass.
4. Run `make verify`.
5. Open the PR against `develop`. Fill in `.github/pull_request_template.md` and link the issue with `Closes #<issue>`.
6. Merge after CI passes and a reviewer approves. Never push to `master` or `develop` directly.

Releases and hotfixes need a person's confirmation at every step. Do not run any of these on your own.

- Release: `make release-branch VERSION=X.Y.Z` cuts `release/X.Y.Z` from `develop`. Each push to it builds, scans and publishes `X.Y.Z-rc.<sha>` images. The release PR into main needs one approval. Tagging the merge commit `vX.Y.Z` publishes the final images and the GitHub Release. Merge the release branch back into `develop`.
- Hotfix: branch `hotfix/<issue>-<short-name>` from main and open PRs into main and `develop`.

## Code rules

- No comments in code or config. Names and tests carry the meaning.
- SOLID, applied with judgment. An interface with one implementation that is not a boundary, and a layer that only passes calls through, are anti-patterns. Delete them.
- Hexagonal dependency direction inside each service: `domain` ← `application` ← `adapter`. `domain` imports neither of the others. `application` talks to the outside only through its `port.in` and `port.out` interfaces.
- Secrets come from environment variables only. Never commit them, never give them defaults. Local values live in `infra/docker/.env`, which git ignores.

## Dependencies

- Backend versions live in one place, `backend/gradle/libs.versions.toml`. Build scripts and convention plugins in `backend/build-logic/` read the catalog and never hard-code a version.
- After changing the catalog or `frontend/package.json`, run `make lock` and commit every changed `gradle.lockfile` and `package-lock.json`.

## Language

Code, commit messages, PR descriptions and docs in this repository are written in English.
