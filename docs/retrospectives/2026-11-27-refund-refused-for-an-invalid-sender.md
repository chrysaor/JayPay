# A remittance refund asked after the sender became invalid never landed

## What happened

The failure path table of [#142](https://github.com/chrysaor/JayPay/issues/142) left the cell empty, and [#184](https://github.com/chrysaor/JayPay/issues/184) read the gap from the code. A probe against the real money-service then showed it: an increase asked while the member was not valid answered `FAILED`, and the same request id answered `FAILED` again after the member was valid again, with the balance unchanged. A remittance in `REFUNDING` whose sender was not valid when the refund first reached money-service could never be refunded. Every repeat answered 503, and the sender's money stayed taken. No user or operator reported it.

## Cause

remittance-service sent its refund as an ordinary `POST /money/increase`. money-service checks the member on every increase and stores a refusal under the request id as a final result, so it could not tell money it had taken and now owed back from a new increase.

## Why the checks missed it

- The fake money-service in `RemittanceInvariantsTest` applied every change for a member that was not valid. The real money-service refuses it. With a fake that refuses like the real one, the refund test failed on `develop`, and the other tests of the class still passed.
- The test of a refund whose answer was lost passed because the fake had already applied the first refund. No test refused a refund first and sent it again.
- The failure path table marked the cell as not covered, and nothing turns a cell that is not covered into a failing check.

## Fix

The PR for [#184](https://github.com/chrysaor/JayPay/issues/184) adds `POST /money/refund`, which names the decrease it gives back and passes whatever the state of the member, once per decrease. remittance-service sends its refund there. A refund stored as a refused increase before the upgrade is applied by its next repeat. The decision is in [0029](../decisions/0029-refund-passes-whatever-the-member-state.md).

## What changed so it is caught next time

- The fake money-service in `RemittanceInvariantsTest` refuses a change for a member that is not valid with the status `2`, as the real one does, and its `/money/refund` applies only a decrease it recorded.
- `RemittanceInvariantsTest` sends a refund that failed with 5xx again after the sender became invalid, and a debit whose answer was lost again after the sender, the receiver or both became invalid, and checks the status and both balances.
- `WalletInvariantsTest` checks a refund for a member that is not valid or that membership-service cannot confirm, the refusal of a refund that names no succeeded decrease or a decrease already refunded, concurrent copies of a refund, and a refund stored as a refused increase by an earlier version.
