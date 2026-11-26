# An existing event flow database refused the steps of new flows

## What happened

The second version of event-flow-service added remittance, payment and settlement flows. `make verify` and CI passed. On a stack whose database the first version had created, every step of the new flows was refused, and the smoke failed with `FLOW_NOT_FOUND`. It was found before the merge, by running the smoke on a stack that kept its volumes.

## Cause

The flow type and step columns were enum columns. Hibernate creates such a column with a check constraint that lists the values known at that moment, and `ddl-auto: update` never changes the constraint. The database of the first version allowed only the top-up type and its seven steps.

## Why the checks missed it

Every test and every CI job creates its database from nothing, so the schema always matches the code. No check starts from the database of an earlier version. The worker that wrote the change did not run the stack, and the review of the diff did not connect a new enum value with the stored schema.

## Fix

[#145](https://github.com/chrysaor/JayPay/pull/145) stores the flow type and step as text, so the schema no longer lists the values.

## What changed so it is caught next time

- `FlowSchemaTest` fails when the schema restricts the flow type or the step.
- A PR is merged only after the smoke passed on a stack that kept its volumes from `develop`.
- Four released services have the same enum columns. [#148](https://github.com/chrysaor/JayPay/issues/148) tracks them and the upgrade test that would catch the whole class.
