# 0043. Seed data is kept in plain files, and the credentials of its accounts are generated at install

## Release

0.7.0 (unreleased)

## Status

accepted

## Context

Once customers sign in ([#91](https://github.com/chrysaor/JayPay/issues/91)), nobody can look around a fresh stack with a member id alone. [#251](https://github.com/chrysaor/JayPay/issues/251) adds `make seed`, which fills a running stack with customers, a merchant and transactions, and with accounts that can sign in.

The repository is public and is scanned for secrets by gitleaks and by GitHub. The owner asked how the data can be kept without an account being exposed and an alert going off, and named an encrypted bundle as one way.

The scanners react to values that look like passwords, tokens and keys. Names, addresses, amounts and made-up account numbers do not trigger them. Of everything a seed holds, only the passwords of its accounts are secret.

The staff accounts already work without a committed secret: `.env.example` holds a placeholder, and `scripts/init-env.py` writes a random value into `infra/docker/.env` on the machine at the first `make up`.

## Options

- A: plain data files and credentials generated at install. The data is committed as made-up values, and the password of the seeded accounts is generated per machine like the staff passwords. No new tool and no key.
- B: a bundle encrypted with SOPS and age. Only the values are encrypted, so the scanners see ciphertext. Two new tools, and a key that a public repository cannot hold, so a person who clones it cannot use the seed.
- C: git-crypt. Whole files are encrypted. One new tool, the same key problem, no readable diff, and a wrong setup commits plain text.
- D: a generator with a fixed seed. Code makes the same data every time and no data file exists. A new library, and more than a handful of accounts to look around needs.
- E: SQL or a dump restored into the databases. It does not run the real flows, so the event flow and the Axon event store disagree with the tables, it breaks when a schema changes, and a dump can carry password hashes.

## Decision

A. The owner picked it from a report of the five options.

- The seed data is a plain file in the repository with made-up values only: addresses at `example.com` and account numbers that are plainly not real.
- No credential of a seeded account is committed or printed. `.env.example` gains a placeholder, and `scripts/init-env.py` generates the value on the machine.
- `make seed` sends requests through the gateway, like the smoke test, so the data is made by the real flows.
- Running it again leaves the same state.

## What we gave up

- The password of the seeded accounts differs per machine, so a person has to read it from `infra/docker/.env` before the first sign-in.
- The data file is readable by everyone. Nothing that has to stay private can go into it.
- B was not tried in this repository. What this record says about SOPS, age and git-crypt comes from how the tools are documented to work, not from running them here.

## When to decide again

When a private repository holds a real environment, phase 6 of [#71](https://github.com/chrysaor/JayPay/issues/71): values that are secret and shared by a team are what an encrypted bundle is for. When a load test ([#159](https://github.com/chrysaor/JayPay/issues/159)) needs thousands of records, which is what a generator is for.

## Links

- Issue [#251](https://github.com/chrysaor/JayPay/issues/251)
