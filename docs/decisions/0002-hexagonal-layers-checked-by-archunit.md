# 0002. Hexagonal layers in every service, checked by ArchUnit

## Release

0.1.0

## Status

accepted

## Context

The services followed a hexagonal layout only by convention. settlement-service had no layers, and nothing stopped the domain from depending on frameworks or adapters from calling each other. The dependency direction was broken in many places.

## Options

- Keep the layout as a convention and fix violations when a review finds them.
- Write the dependency direction as ArchUnit rules that fail the build, and fix every violation.

## Decision

One `HexagonalArchitectureTest` with ten ArchUnit rules runs in every service through the `jaypay.hexagonal` convention plugin: `domain` depends on nothing, `application` on `domain` and its ports, adapters only inward. The rules were committed first and failed in seven of eight services. The fixes removed pass-through layers and single-implementation abstractions that carried no boundary instead of adding new ones.

## What we gave up

- Named exceptions stay in the rules. The Axon message packages keep their place because the event store keeps their class names, and the two small Kafka consumers have no layers.
- Sagas that were in progress in an existing local volume did not resume after the move.

## When to decide again

When the event store no longer holds messages under their old class names, the exception for the Axon message packages can go.

## Links

- Issue [#41](https://github.com/chrysaor/JayPay/issues/41)
- PR [#57](https://github.com/chrysaor/JayPay/pull/57)
