# 0022. Enum columns keep no check of their values, without a migration tool

## Release

0.5.0 (unreleased)

## Status

accepted

## Context

Hibernate creates an enum column with a check constraint that lists the values known at that moment, and `ddl-auto: update` never changes the constraint. [0014](0014-flow-type-and-step-stored-as-text.md) stored the event flow type and step as text, which fixed new databases only. banking-service, money-service, payment-service and remittance-service, all released, and the flow status and step outcome of event-flow-service still stored enum columns, so the first status value added after 0.4.0 would be refused on every database that 0.4.0 created. See the [retrospective](../retrospectives/2026-11-27-status-columns-refuse-later-values.md).

## Options

[#148](https://github.com/chrysaor/JayPay/issues/148) names two:

- Store these columns as text, the way event-flow-service does.
- Manage the schema with a migration tool, which needs the owner's approval.

Text in the entity alone leaves the constraint of an existing database in place, so the text option also needs something that drops it. The owner did not approve a migration tool for this issue.

## Decision

`EnumValueChecks` in `libs/common` runs in every service that uses JPA, right after Hibernate has built or updated the schema. It reads the enum columns from the Hibernate mapping and drops each check constraint that covers only one of them. It runs on every start, so a database from any earlier version loses its value checks on the first start, and a new database loses them as soon as Hibernate creates them. The entities keep `@Enumerated(EnumType.STRING)`, and checks named on an entity stay.

`SchemaUpgradeContract` starts each service on the tables that 0.4.0 created and fails when an enum column refuses a value that 0.4.0 did not know.

## What we gave up

- The database no longer refuses a value that the code does not know. Only the code checks it.
- The schema is still made by Hibernate, with no migration tool. Changes that `ddl-auto: update` cannot make, such as a narrower column or a renamed column, still need a hand-written step.
- A check written by hand on a single enum column would be dropped too. None exists.
- Every start runs one catalog query for each enum column. The schema is PostgreSQL only.

## When to decide again

When a schema change that `ddl-auto: update` cannot make is needed, or when the owner approves a migration tool.

## Links

- Issue [#148](https://github.com/chrysaor/JayPay/issues/148), [#124](https://github.com/chrysaor/JayPay/issues/124)
- PR [#180](https://github.com/chrysaor/JayPay/pull/180)
- [0014](0014-flow-type-and-step-stored-as-text.md)
- [Retrospective](../retrospectives/2026-11-27-status-columns-refuse-later-values.md)
