# 0011. Kafka stays for events, and the Kafka task top-up goes

## Release

0.4.0 (unreleased)

## Status

accepted

## Context

Three top-up paths existed side by side: synchronous, a Kafka task and an Axon saga. The Kafka task path, `POST /money/increase-async`, waited on an in-memory latch with no timeout, so money-service could not run two replicas, and task-consumer marked every subtask successful without checking anything. Its only callers were the smoke test and the Kafka trace check.

## Options

- Fix the task path: bound the wait and make task-consumer check the member and the bank account.
- Remove the task path and keep what it was an example of, a top-up that checks the member and the bank account before it moves money, as steps of the Axon saga.

## Decision

The owner chose to remove the Kafka task flow: `POST /money/increase-async`, the task-consumer workload and the topics `jaypay.task.topic` and `jaypay.task.result.topic`. The recharge saga now starts with a member check. Kafka itself stays, for the log topic and for events between services, and the event flow topic in [0012](0012-event-flow-service-and-topic.md) is the next use of it.

## What we gave up

- The repository no longer shows a request and reply over Kafka.
- Kafka volumes from before still hold the two task topics, which nothing reads or writes.
- When the deadline fails a top-up during the member check, the saga still sends a rollback to banking-service, which records it as `CANCELLED`.

## When to decide again

When the rule for which flow uses a saga, HTTP orchestration or Kafka is written, step 6 of the review in [#135](https://github.com/chrysaor/JayPay/issues/135). Kafka stays in every option of that review.

## Links

- Issue [#99](https://github.com/chrysaor/JayPay/issues/99), [#114](https://github.com/chrysaor/JayPay/issues/114)
- PR [#128](https://github.com/chrysaor/JayPay/pull/128)
