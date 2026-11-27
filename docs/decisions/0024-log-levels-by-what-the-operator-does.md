# 0024. Log levels follow what the operator has to do

## Release

0.5.0 (unreleased)

## Status

accepted

## Context

The backend had 54 log calls and no rule for their level ([#189](https://github.com/chrysaor/JayPay/issues/189)). Handlers logged their own name at `INFO` on every command and event, one `INFO` line printed a whole command that can carry a bank account number, and every record sent to the logging topic gave an `INFO` line. Expected refusals, such as a member that is not valid or a transfer refused as invalid, were `WARN`, and `ERROR` stood for a failed rollback and for a refused payout, with no shared meaning. Ten calls logged only `getMessage()` of an exception and lost its class and stack trace. No line was `DEBUG`, so raising the level of a service showed nothing more of our code.

## Options

The issue proposed one rule with four meanings and asked the owner whether an expected refusal is `INFO` and not `WARN`. Only that rule was recorded.

## Decision

The owner agreed. The rule is in [CONTRIBUTING.md](../../CONTRIBUTING.md#logging):

- `ERROR`: an operator has to act, because money may be wrong or the system gave up.
- `WARN`: something outside the service failed, and the system recovers by itself through a retry, a deadline or the next run.
- `INFO`: one line for each business state change, with its id. An expected refusal is `INFO` with its id and its reason, so the count of `WARN` lines means that something is broken.
- `DEBUG`: detail of one call, off by default.

A refusal counts as expected only when the caller who asked gets it as the answer. A payout the bank refused and a merchant without a bank account come up inside the scheduled settlement run, where nobody is told, so they are `ERROR`.

Every line names its id, a caught exception is the last argument, and no line prints an account number or a whole request or command. A failure is logged once, where it is caught, so the web error handlers that answer 4xx or 503 log nothing. The backend build fails on a log call with a fixed text and no argument. Spring Boot reads `LOGGING_LEVEL_COM_JAYPAY` from the environment, which raises the level of one service without a new image.

## What we gave up

- An expected refusal no longer stands out among the `INFO` lines. Its count is to come from a business metric in [#190](https://github.com/chrysaor/JayPay/issues/190).
- The build checks only the fixed-text lines. Whether a line has the right level, an id that means something or no account number stays with the review.

## When to decide again

When the business metrics of #190 count the refusals.

## Links

- Issue [#189](https://github.com/chrysaor/JayPay/issues/189)
- Issue [#190](https://github.com/chrysaor/JayPay/issues/190)
