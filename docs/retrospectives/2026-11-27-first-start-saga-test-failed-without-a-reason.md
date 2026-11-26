# The first-start saga test stopped the processor before its worker ran

## What happened

`RechargeSagaFirstStartTest` failed on the first attempt of the backend job of [#181](https://github.com/chrysaor/JayPay/pull/181) and passed on the rerun of the same commit. After the saga processor was started again, no membership check arrived within 20 seconds. #181 changed no product code and did not touch the test. It is the second CI failure of this test, after the one in [Two tests that passed or failed by timing](2026-11-26-tests-that-depend-on-timing.md). Tracked in [#185](https://github.com/chrysaor/JayPay/issues/185).

## Cause

The test stopped the saga processor in the moment between its claim of the segment and the start of its worker, and Axon Framework 4.13.2 then leaves the processor unable to work after the next start.

- The launcher thread of a `TrackingEventProcessor` records the segment in `processingStatus()` inside the transaction that claims the token. After the transaction it takes one of the processor's threads, here the only one, and then runs the worker.
- The test waited until `processingStatus()` was not empty and called `shutDown()` right away. When the shutdown landed before the worker ran, the launcher freed the segment without giving the thread back. `availableProcessorThreads()` stayed at 0.
- After `start()` the processor ran, claimed no segment and read no event, for as long as the JVM lived. The top-up was never processed, so no membership check arrived.

The test polled every 100 ms, so it hit that moment only now and then. A probe that polls without sleeping hits it on the first try: 0 threads after the shutdown, and 12 seconds after the next start the processor still has no segment. A probe that waits until the worker has caught up before it shuts down ran 300 cycles without losing the thread.

No service stops and starts a processor inside one JVM, so only the test was affected. The first investigation in [#187](https://github.com/chrysaor/JayPay/pull/187) repeated the test 34 times, did not see the failure and stopped at the list of what it had ruled out. The cause was found by reading `TrackingEventProcessor` for a path that never recovers, since a test that takes 1.2 seconds and then waits 20 does not point at a slow runner.

## Why the checks missed it

The assertion had a description, but Gradle printed only the exception class and the line, so the description never reached the CI log. Nothing showed whether the event was stored, whether the processor claimed its segment again, where its token stood, whether the saga started or whether the command was sent.

## Fix

- The test waits until a worker of the processor has caught up, which only a running worker reports, and stops the processor after that.
- The assertion reports each step when it fails: the global index of the stored top-up, whether the processor runs or is in error, each segment with its position and its last error, the token rows with their owner and claim time, the sagas of the top-up and the membership checks sent.
- Every backend test task prints the full message of a failed test, so a description reaches the CI log in every service.

## What changed so it is caught next time

- A test that stops a processor waits for a state that only its running worker reports, not for the claim.
- A failure that does not come back by repeating is not closed as unknown. The next step is to read the code for a path that explains the symptom and to force that path.
- A test that waits on a processor describes the state it waited for, so its failure can be read from the CI log alone.
