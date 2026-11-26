# 0001. Rebuild the backend on Java 25, Spring Boot 4 and PostgreSQL

## Release

0.1.0

## Status

accepted

## Context

The services were written in 2023 and 2024 on Spring Boot 2.5.2, Java 11 and Gradle 7.4. They no longer built on a current JDK, and no service configuration was committed.

## Options

- Move every framework to its current major version, Axon Framework included. Axon 5 rewrites aggregates and sagas.
- Move Java, Spring Boot and Gradle to their current versions and keep Axon on 4.x.

## Decision

The backend moved to `backend/` with one version catalog and convention plugins, and runs on Java 25, Spring Boot 4.2, Gradle 9.9 and Axon 4.13. PostgreSQL replaced MySQL. Each service has a committed `application.yml`, and secrets come only from environment variables. The endpoint contract script showed no difference before and after the move.

## What we gave up

- Axon 5 and what it changes.
- Data from an old MySQL database does not carry over unchanged, because ids now come from Hibernate sequences.
- settlement-service no longer runs its batch job at startup, because that run raced the scheduler on PostgreSQL. The scheduler still runs it.

## When to decide again

When the project moves to Axon 5, which means rewriting the aggregates and the sagas.

## Links

- Issue [#35](https://github.com/chrysaor/JayPay/issues/35)
- PR [#44](https://github.com/chrysaor/JayPay/pull/44)
