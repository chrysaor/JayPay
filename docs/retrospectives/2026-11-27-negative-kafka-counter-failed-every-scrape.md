# One negative Kafka counter took logging-consumer out of Prometheus

## What happened

On a new local Kubernetes cluster logging-consumer started before its topic existed. After that every scrape of its metrics endpoint answered 500 with `counters cannot have a negative value` for a Kafka consumer counter at -745, so Prometheus showed the service as down and `make k8s-smoke` failed at the scrape check. The service kept consuming, and a restart cleared it. It was found by the Kubernetes smoke before the 0.4.0 release ([#154](https://github.com/chrysaor/JayPay/issues/154)).

## Cause

The first wrong step is in kafka-clients: the consumer coordinator adds the difference of two wall-clock readings to `rebalance-latency-total` for each rebalance, so a clock that steps back during a rebalance makes the sum negative. Micrometer's Kafka binder registers every `CumulativeSum` as a counter, the Prometheus client refuses a counter below zero, and the Micrometer Prometheus registry lets that one refusal fail the whole scrape ([micrometer#7302](https://github.com/micrometer-metrics/micrometer/issues/7302), open). In kafka-clients 4.4.0 every other counter adds counts, byte sizes or differences of the monotonic clock. The meter name was not read from the failing process, so it is inferred from the source, the millisecond size of the value and the client id as the only label. Why the clock stepped back on the cluster was not seen either.

## Why the checks missed it

The management port contract that every service runs scraped the endpoint only with the metrics a fresh test context produces, where no counter is negative. Nothing exercised a negative Kafka value, and the Compose stack never produced one. The error names no meter, and the service exposes no endpoint that lists single meters, so the name could not be read on the cluster.

## Fix

`jaypay-common.yml` leaves out `kafka.consumer.coordinator.rebalance.latency.total` in every service, see [0023](../decisions/0023-kafka-rebalance-latency-total-left-out.md).

## What changed so it is caught next time

The management port contract binds a Kafka consumer whose `rebalance-latency-total` is -745 and expects the scrape to answer 200 with the other Kafka meters in it, in every service.
