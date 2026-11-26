# The status columns of four released services would refuse a value added later

## What happened

After [#145](https://github.com/chrysaor/JayPay/pull/145) fixed the flow type and step of event-flow-service, the same kind of column was still in banking-service, money-service, payment-service and remittance-service, and in the flow status and step outcome of event-flow-service. All of them are released in 0.4.0. No user saw it yet, because no status value has been added since 0.4.0. The first one added would have been refused on every database that 0.4.0 created. It was found by reading the code for [#148](https://github.com/chrysaor/JayPay/issues/148).

## Cause

The columns are mapped with `@Enumerated(EnumType.STRING)`. Hibernate creates such a column with a check constraint that lists the values the code knows at that moment, for example `bank_transfer_status_check`, and `ddl-auto: update` never changes or drops a constraint. Storing the column as text in the entity, as #145 did, fixes only new databases: an existing database keeps the constraint.

## Why the checks missed it

Every test and every CI job creates its database from nothing, so the schema always matches the code. No test started from the database of a released version, and #145 added a check for the two event flow columns only, on a new database.

## Fix

Each service that uses JPA now drops every single-column check constraint on an enum column right after Hibernate has built or updated the schema, on every start. An existing database loses the constraints of 0.4.0 on its first start with the new version, and a new database loses them as soon as Hibernate creates them. Checks named on an entity, such as `wallet_balance_not_negative`, stay. See the [decision](../decisions/0022-enum-columns-without-value-checks.md).

## What changed so it is caught next time

- `SchemaUpgradeContract`, which the `jaypay.jpa-service` convention plugin gives every JPA service, starts the service with `ddl-auto: update` on the tables that 0.4.0 created, from the service's `schema-0.4.0.sql`, and fails when an enum column refuses a value that 0.4.0 did not know, or when a named check is gone. banking-service, money-service, payment-service, remittance-service and event-flow-service run it.
- `FlowSchemaTest` now fails when a new event flow database has any check constraint on its tables.
