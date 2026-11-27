# 0016. Java stays on 25 and the sites build on Node 26, the LTS lines

## Release

0.5.0

## Status

accepted

## Context

Dependabot proposed two major image updates in one PR ([#65](https://github.com/chrysaor/JayPay/pull/65)): the JDK from 25 to 26 and Node from 24 to 26. The Node.js release schedule lists Node 24 in maintenance since 2026-10-20 and Node 26 as the active LTS since 2026-10-28, supported until 2029-04-30. CI installs Node through `lts/*`, so it already ran on Node 26 while the site images built on Node 24. JDK 26 is not an LTS release, and Java 25 is the version chosen in [0001](0001-java-25-and-spring-boot-4.md).

## Options

- Merge the Dependabot PR and move both.
- Close it and stay on both versions.
- Move Node to 26 and keep Java on 25. The owner chose this.

## Decision

`frontend/Dockerfile` builds on Node 26, pinned by digest, so the images and CI use the same line. Java stays on 25, and Dependabot ignores major updates of the `eclipse-temurin` image. `package.json` still accepts Node 24 or newer for a build outside Docker.

## What we gave up

- A machine with Node 24 runs the frontend tests on another line than CI and the images.
- A new JDK major no longer shows up as a Dependabot PR. Moving Java is a deliberate change of the Gradle toolchain, the image and CI together.

## When to decide again

When the next Java LTS is released, or when Node 26 goes into maintenance on 2027-10-20.

## Links

- Issue [#168](https://github.com/chrysaor/JayPay/issues/168)
- PR [#169](https://github.com/chrysaor/JayPay/pull/169)
