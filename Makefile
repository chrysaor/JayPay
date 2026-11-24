SERVICES := $(notdir $(wildcard backend/services/*))
IMAGES ?= $(SERVICES) frontend
REGISTRY ?= ghcr.io/chrysaor
TAG ?=
VERSION ?=
STEPS ?=

TRIVY_IMAGE := aquasec/trivy:0.76.2@sha256:8f6bb947dbc528902e11f2f5be52f7b519f6a39d5d148f41166f2040499768fb
GITLEAKS_IMAGE := ghcr.io/gitleaks/gitleaks:v8.30.1@sha256:c00b6bd0aeb3071cbcb79009cb16a60dd9e0a7c60e2be9ab65d25e6bc8abbb7f
TRIVY_CACHE := -v jaypay-trivy-cache:/root/.cache/trivy
FRONTEND_DEPS := frontend/node_modules/.package-lock.json

.DEFAULT_GOAL := help
.PHONY: help build test verify verify-selftest images scan scan-images push up down lock contract release-branch

help:
	@echo "make build                     compile the backend and build the frontend"
	@echo "make test                      run backend and frontend tests"
	@echo "make verify [STEPS=...]        the gate: $$(sed -n 's/^ALL_STEPS=(\(.*\))/\1/p' scripts/verify.sh)"
	@echo "make verify-selftest           prove the gate fails on planted errors"
	@echo "make images                    build jaypay/<service> and jaypay/frontend"
	@echo "make scan                      trivy fs scan and gitleaks history scan"
	@echo "make scan-images [IMAGES=...]  trivy image scan, fails on HIGH or CRITICAL"
	@echo "make push TAG=<tag>            push images to $(REGISTRY)/jaypay-<name>:<tag>"
	@echo "make up | down                 start or stop the local stack in infra/docker"
	@echo "make lock                      refresh gradle.lockfile and package-lock.json"
	@echo "make contract                  regenerate docs/api-contract.md"
	@echo "make release-branch VERSION=X.Y.Z  create release/X.Y.Z from origin/develop"

$(FRONTEND_DEPS): frontend/package-lock.json
	cd frontend && npm ci --no-audit --no-fund

build: $(FRONTEND_DEPS)
	cd backend && ./gradlew assemble
	cd frontend && npm run build

test: $(FRONTEND_DEPS)
	cd backend && ./gradlew test
	cd frontend && npm test

verify:
	scripts/verify.sh $(STEPS)

verify-selftest:
	scripts/verify-selftest.sh

images:
	cd backend && ./gradlew dockerBuild
	docker build --tag jaypay/frontend frontend

scan:
	docker run --rm $(TRIVY_CACHE) -v "$(CURDIR):/repo" -w /repo $(TRIVY_IMAGE) fs --scanners vuln,misconfig .
	docker run --rm -v "$(CURDIR):/repo" $(GITLEAKS_IMAGE) git /repo --redact --verbose --gitleaks-ignore-path /repo/.github/.gitleaksignore

scan-images:
	@status=0; for name in $(IMAGES); do \
	  echo "==> jaypay/$$name"; \
	  docker run --rm $(TRIVY_CACHE) -v /var/run/docker.sock:/var/run/docker.sock $(TRIVY_IMAGE) \
	    image --severity HIGH,CRITICAL --exit-code 1 --no-progress jaypay/$$name || status=1; \
	done; exit $$status

push:
	@test -n "$(TAG)" || { echo "usage: make push TAG=<tag>"; exit 2; }
	@for name in $(IMAGES); do \
	  docker tag jaypay/$$name $(REGISTRY)/jaypay-$$name:$(TAG) && \
	  docker push $(REGISTRY)/jaypay-$$name:$(TAG) || exit 1; \
	done

infra/docker/.env:
	@echo "infra/docker/.env is missing. Run: cp infra/docker/.env.example infra/docker/.env, then fill in every value."
	@exit 1

up: infra/docker/.env
	cd infra/docker && docker compose up -d

down: infra/docker/.env
	cd infra/docker && docker compose down

lock:
	cd backend && ./gradlew resolveAndLockAll --write-locks
	cd frontend && npm install --package-lock-only --no-audit --no-fund

contract:
	python3 scripts/extract-endpoints.py backend > docs/api-contract.md

release-branch:
	@echo "$(VERSION)" | grep -Eq '^[0-9]+\.[0-9]+\.[0-9]+$$' || { echo "usage: make release-branch VERSION=X.Y.Z"; exit 2; }
	git fetch origin develop
	git switch --no-track --create release/$(VERSION) origin/develop
	@echo "Created release/$(VERSION). Push it with: git push -u origin release/$(VERSION)"
