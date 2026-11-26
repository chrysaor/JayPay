# The first-start saga test failed in CI and said only its line number

## What happened

`RechargeSagaFirstStartTest` failed on the first attempt of the backend job of [#181](https://github.com/chrysaor/JayPay/pull/181) and passed on the rerun of the same commit. After the saga processor was started again, no membership check arrived within 20 seconds. #181 changed no product code and did not touch the test. It is the second CI failure of this test, after the one in [Two tests that passed or failed by timing](2026-11-26-tests-that-depend-on-timing.md). Tracked in [#185](https://github.com/chrysaor/JayPay/issues/185).

## Cause

Not found yet. The CI log showed `AssertionFailedError at RechargeSagaFirstStartTest.java:65` and nothing else, so it does not say which step stopped.

What was ruled out:

- The failure does not come back by repeating. The whole money-service test task ran 12 times in the file order of macOS and 12 times in a random class order, while other builds ran on the same machine. All 24 passed, and the test took 1.2 to 1.4 seconds every time, so the CI run did not just take a little longer. It went down a path that waited at least 20 seconds.
- Another test class does not share its state. The test has its own PostgreSQL database, so its events, tokens and sagas are not seen by the other Spring contexts in the JVM, and each context has its own command bus.
- The processor was started again. A start that the processor refuses throws, and the test failed at the assertion after it.

The Axon paths that wait that long are a token claimed by another owner, which the token store keeps for 10 seconds, a processing error that retries after 1, 2, 4 and 8 seconds, and a failed fetch of the segments, which backs off the same way.

## Why the checks missed it

The assertion had a description, but Gradle printed only the exception class and the line, so the description never reached the CI log. Nothing showed whether the event was stored, whether the processor claimed its segment again, where its token stood, whether the saga started or whether the command was sent.

## Fix

- The assertion now reports each step when it fails: the global index of the stored top-up, whether the processor runs or is in error, each segment with its position and its last error, the token rows with their owner and claim time, the sagas of the top-up and the membership checks sent.
- Every backend test task prints the full message of a failed test, so a description reaches the CI log in every service.

## What changed so it is caught next time

- The next failure names the step it stopped at, and #185 stays open until a run shows it.
- A test that waits on a processor describes the state it waited for, so its failure can be read from the CI log alone.
