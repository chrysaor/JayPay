# 0023. The Kafka rebalance latency total is left out of the metrics

## Release

0.5.0 (unreleased)

## Status

accepted

## Context

During the checks of 0.4.0 on the local Kubernetes cluster, every scrape of logging-consumer answered 500 with `counters cannot have a negative value` for a Kafka consumer counter at -745 that carried only the client id. Prometheus showed the service as down until a restart ([#154](https://github.com/chrysaor/JayPay/issues/154)).

Three parts meet here. Micrometer's Kafka binder registers every Kafka metric backed by `CumulativeSum` or `CumulativeCount` as a counter. The Prometheus client refuses a counter below zero, and the Micrometer Prometheus registry lets that refusal fail the whole scrape, which Micrometer tracks in [micrometer#7302](https://github.com/micrometer-metrics/micrometer/issues/7302) with no fix released. In kafka-clients 4.4.0 every such counter adds counts, byte sizes or differences of the monotonic nanosecond clock, except `rebalance-latency-total` of `consumer-coordinator-metrics`, which adds the difference of two readings of the wall clock for each rebalance, in the classic and in the new consumer protocol. A wall clock that steps back during a rebalance makes it negative. The meter was not read from the running process, so that it was this one is inferred from the source, the millisecond size of the value and the client id as the only label.

## Options

The issue names two:

- Leave out the meter that can go negative, in the configuration every service shares. Chosen.
- Guard the Kafka consumer meters in the shared configuration, for example a Prometheus registry that writes a negative function counter as `NaN`, the change Micrometer considers in #7302. Rejected in this PR: it replaces the registry that Spring Boot configures, and it would hide a negative value from any meter instead of removing the one meter whose value is not a counter.

A version update is not an option: the services already run the newest Micrometer, 1.18.0, and the newest Prometheus client, 1.10.0.

## Decision

`jaypay-common.yml`, which every service imports, sets `management.metrics.enable` to `false` for `kafka.consumer.coordinator.rebalance.latency.total`. The meter is never registered, so `kafka_consumer_coordinator_rebalance_latency_total` is not scraped. `kafka_consumer_coordinator_rebalance_latency_avg`, `_max` and `kafka_consumer_coordinator_rebalance_total` stay. The management port contract that every service runs binds a consumer whose total is -745 and expects the scrape to answer 200 with the other Kafka meters in it.

## What we gave up

- The total time spent in rebalances is gone. No dashboard or alert used it.
- Another meter that turns negative would still fail the whole scrape. None of the Kafka client counters can by their source today, and the contract test does not cover meters from other binders.

## When to decide again

When Micrometer resolves #7302, or when a kafka-clients update adds a counter that sums wall-clock differences.

## Links

- Issue [#154](https://github.com/chrysaor/JayPay/issues/154)
- PR [#179](https://github.com/chrysaor/JayPay/pull/179)
- Retrospective [One negative Kafka counter took logging-consumer out of Prometheus](../retrospectives/2026-11-27-negative-kafka-counter-failed-every-scrape.md)
- [micrometer#7302](https://github.com/micrometer-metrics/micrometer/issues/7302)
