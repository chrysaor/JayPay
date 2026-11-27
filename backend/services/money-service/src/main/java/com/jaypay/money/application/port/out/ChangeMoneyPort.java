package com.jaypay.money.application.port.out;

import com.jaypay.money.domain.MemberMoney;
import com.jaypay.money.domain.MoneyAmount;
import com.jaypay.money.domain.MoneyChangingRequest;
import com.jaypay.money.domain.RequestId;

import java.util.Optional;

public interface ChangeMoneyPort {

    Optional<MoneyChangingRequest> findEarlierOrClaim(MoneyChangingRequest pending);

    Optional<MoneyChangingRequest> findForUpdate(RequestId requestId);

    MoneyChangingRequest settle(MoneyChangingRequest settled);

    Optional<RequestId> findRefundOf(RequestId refundedRequestId);

    MoneyChangingRequest settleRefund(MoneyChangingRequest settled, RequestId refundedRequestId);

    void increaseBalance(MemberMoney.MembershipId membershipId, MoneyAmount amount);

    boolean decreaseBalance(MemberMoney.MembershipId membershipId, MoneyAmount amount);

}
