# 0005. Scanners, tools and base images pinned by digest

## Release

0.1.0, extended to the site images in 0.3.0

## Status

accepted

## Context

There was no CI and no security checking, and a Vault token was committed. Later, in 0.3.0, the site images ran nginx 1.31.7 because the unprivileged nginx image had not been rebuilt for weeks, while one of the advisories that 1.31.10 fixes concerned a directive both sites use.

## Options

- Run gitleaks and Trivy as marketplace actions in CI.
- Run them, and the other gate tools, from images pinned by digest, so CI and a local run use the same binary.
- For the sites in 0.3.0: stay on the unprivileged image, or move to the official `nginx` image pinned by digest.

## Decision

CodeQL, dependency review, Trivy and gitleaks run on PRs, on `master` and weekly. Trivy, gitleaks, terraform and actionlint run from images pinned by digest, Helm, kubeconform and kubectl joined them in 0.3.0, and third-party actions are pinned by commit SHA. Every image is scanned before it is published and the publish fails on a HIGH or CRITICAL finding. In 0.3.0 both sites moved to the official `nginx` image pinned by digest, so Dependabot proposes each new release with its digest, and `apk upgrade` left the build because it changed a pinned image on every build.

## What we gave up

- Dependabot does not see the tool image versions in `Makefile` and `scripts/verify.sh`, so they are bumped by hand.
- Without `apk upgrade`, Alpine fixes reach the sites only through a Dependabot digest bump.
- The site images own their `nginx.conf`, so an upstream change to the default main config does not reach them.
- The images the stacks only run, such as Keycloak and PostgreSQL, are pinned but not scanned. That is open in [#121](https://github.com/chrysaor/JayPay/issues/121).

## When to decide again

When [#121](https://github.com/chrysaor/JayPay/issues/121) or [#102](https://github.com/chrysaor/JayPay/issues/102) is decided: scanning third-party images, or signing the images this repository publishes.

## Links

- Issue [#36](https://github.com/chrysaor/JayPay/issues/36), [#103](https://github.com/chrysaor/JayPay/issues/103)
- PR [#45](https://github.com/chrysaor/JayPay/pull/45), [#48](https://github.com/chrysaor/JayPay/pull/48), [#105](https://github.com/chrysaor/JayPay/pull/105)
