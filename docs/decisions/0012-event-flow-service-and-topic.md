# 0012. Event flow as its own service fed by a Kafka topic

## Release

0.4.0

## Status

accepted

## Context

Operators could not see where a top-up was or which step stopped it. The owner wanted the admin console to show how each request moves through the services while it happens, with the failed step marked, and without bank account numbers or other personal data.

## Options

- Read the traces that ClickHouse already holds, through one read-only endpoint. No new storage.
- Read the Axon event store. It knows only Axon events, so remittance, payment and settlement would be missing.
- A new Kafka topic that every service writes its steps to, and a consumer that keeps the flows. The cleanest model and the most work.

## Decision

The owner chose the topic. money-service, membership-service, banking-service, remittance-service, payment-service and settlement-service write one record per step to `jaypay.flow.events` after their transaction commits, with member ids and amounts only, give up after 2 seconds and never fail the money flow. A new workload, event-flow-service, with its own `event_flow` database, stores the steps and serves `GET /flows` and `GET /flows/{flowType}/{flowId}`. It deletes flows whose newest step is older than `FLOW_RETENTION_PERIOD`, 30 days by default. The admin console polls the list every 5 seconds and an open flow every 2 seconds. The top-up saga came first, in [#131](https://github.com/chrysaor/JayPay/pull/131), and remittance, payment and settlement followed in [#145](https://github.com/chrysaor/JayPay/pull/145).

## What we gave up

- One more workload with 384m of memory and its own database, instead of a table in money-service. Flows cross services, and the read model can fail apart from the money.
- A step written while Kafka is down is lost. The money flow wins over the record.
- Polling instead of server-sent events, so a viewer sees a step within about 2 seconds and no new component is needed.
- settlement-service writes its steps when its batch run commits, so a run that rolls back writes none.

## When to decide again

When steps lost while Kafka is down start to matter. [#124](https://github.com/chrysaor/JayPay/issues/124) left that open.

## Links

- Issue [#124](https://github.com/chrysaor/JayPay/issues/124)
- PR [#131](https://github.com/chrysaor/JayPay/pull/131), [#145](https://github.com/chrysaor/JayPay/pull/145)
