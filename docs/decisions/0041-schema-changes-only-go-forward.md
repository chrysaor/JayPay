# 0041. A schema change only goes forward, and the previous release runs on the new schema

## Release

0.7.0 (unreleased)

## Status

accepted

## Context

Hibernate builds and updates the tables when a service starts, and the owner asked how a schema change is taken back when a release fails. A migration tool is being considered ([#243](https://github.com/chrysaor/JayPay/issues/243)).

A change that reached a database cannot be taken back reliably. Data of a dropped column does not return. Data that the new version wrote since is lost. A script that reverts a change runs for the first time during the failure.

## Options

- A: revert the schema with the release, with an undo or rollback script for every change. Flyway has undo in its paid editions only, and Liquibase has rollback.
- B: never revert the schema. The previous application version runs on the schema that the failed release left, and a change that would break it is split over releases.

## Decision

B. The owner made it a rule for development ([#242](https://github.com/chrysaor/JayPay/issues/242)): the application goes back, the database only goes forward.

- A schema change is only ever applied forward. A wrong change is corrected by a new change.
- The previous release must run on the schema that a release leaves.
- A new table and a new column that is nullable or has a default are safe in one release.
- Dropping or renaming a column or a table, narrowing a type, and adding NOT NULL or a constraint to a column that exists are split: add in one release, remove in a later one.

The rule is in `CLAUDE.md` under "Code rules" and in `CONTRIBUTING.md` under "Schema changes", with the three releases of a rename as the example.

With `ddl-auto: update`, which only adds columns and tables, the schema already goes forward only. The rule matters as soon as a migration tool can drop and rename.

## What we gave up

- A rename or a removal takes two or three releases, and the code writes to two columns in between.
- Nothing checks the rule yet. A test that starts the previous release on the new schema comes with the migration tool. `SchemaUpgradeTest` checks the other direction only, the new release on the tables of an earlier one.
- A change that damaged data is not covered. That needs backup and recovery, which the local stacks do not have.

## When to decide again

When a schema change cannot be split, or when the stack runs where a database can be restored to a point in time.

## Links

- Issues [#242](https://github.com/chrysaor/JayPay/issues/242) and [#243](https://github.com/chrysaor/JayPay/issues/243)
- Decision [0022](0022-enum-columns-without-value-checks.md), the schema without a migration tool
