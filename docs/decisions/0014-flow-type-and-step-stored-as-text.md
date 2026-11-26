# 0014. Event flow stores its flow type and step as text

## Release

0.4.0

## Status

accepted

## Context

The second version of event-flow-service added the remittance, payment and settlement flows. On a database that the first version had created, every step of the new flows was refused. Hibernate creates an enum column with a check constraint that lists the values known at that moment, and `ddl-auto: update` never changes the constraint. Every test and CI job starts from an empty database, so none of them saw it. See the [retrospective](../retrospectives/2026-11-26-event-flow-schema-refuses-new-steps.md).

## Options

- Store the columns as text, so the schema no longer lists the values.
- Manage the schema with a migration tool. That needs the owner's approval for a new tool.

## Decision

event-flow-service stores the flow type and the step as text. `FlowSchemaTest` fails when the schema restricts either column. A PR is merged only after the smoke passed on a stack that kept its volumes from `develop`.

## What we gave up

- The database no longer refuses a value that the code does not know. Only the code checks it.
- The schema is still made by Hibernate, with no migration tool.

## When to decide again

When [#148](https://github.com/chrysaor/JayPay/issues/148) is decided. banking-service, money-service, payment-service and remittance-service, all released, still store their status as an enum column, and the choice there is the same: text, or a migration tool.

## Links

- Issue [#124](https://github.com/chrysaor/JayPay/issues/124), [#148](https://github.com/chrysaor/JayPay/issues/148)
- PR [#145](https://github.com/chrysaor/JayPay/pull/145)
- [Retrospective](../retrospectives/2026-11-26-event-flow-schema-refuses-new-steps.md)
