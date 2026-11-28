package com.jaypay.money.query.application.service;

import com.jaypay.common.UseCase;
import com.jaypay.money.query.application.port.in.IncreaseMoneySumByAddressUseCase;
import com.jaypay.money.query.application.port.out.GetMemberAddressInfoPort;
import com.jaypay.money.query.application.port.out.InsertMoneyIncreaseEventByAddress;
import com.jaypay.money.query.application.port.out.MemberAddressInfo;
import com.jaypay.money.query.domain.MemberAddressNotConfirmedException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;

import java.time.Duration;
import java.util.Optional;

@UseCase
@Slf4j
public class IncreaseMoneySumByAddressService implements IncreaseMoneySumByAddressUseCase {

    private final GetMemberAddressInfoPort getMemberAddressInfoPort;
    private final InsertMoneyIncreaseEventByAddress insertMoneyIncreaseEventByAddress;
    private final Duration membershipPatience;
    private final Duration membershipRetryAfter;

    public IncreaseMoneySumByAddressService(GetMemberAddressInfoPort getMemberAddressInfoPort,
                                            InsertMoneyIncreaseEventByAddress insertMoneyIncreaseEventByAddress,
                                            @Value("${region-total.membership-patience}") Duration membershipPatience,
                                            @Value("${region-total.membership-retry-after}") Duration membershipRetryAfter) {
        this.getMemberAddressInfoPort = getMemberAddressInfoPort;
        this.insertMoneyIncreaseEventByAddress = insertMoneyIncreaseEventByAddress;
        this.membershipPatience = membershipPatience;
        this.membershipRetryAfter = membershipRetryAfter;
    }

    @Override
    public void increaseMoneySum(String rechargingRequestId, String membershipId, long moneyAmount) {
        Optional<MemberAddressInfo> member;
        try {
            member = addressWithinTheAttempts(membershipId);
        } catch (MemberAddressNotConfirmedException notConfirmed) {
            log.error("The recharge {} of {} for member {} is left out of the region total: membership-service did not give the address within {}",
                    rechargingRequestId, moneyAmount, membershipId, membershipPatience, notConfirmed);
            return;
        }
        if (member.isEmpty()) {
            log.warn("The recharge {} is left out of the region total: member {} does not exist", rechargingRequestId, membershipId);
            return;
        }
        insertMoneyIncreaseEventByAddress.insertMoneyIncreaseEventByAddress(rechargingRequestId, member.get().getAddress(), moneyAmount);
    }

    private Optional<MemberAddressInfo> addressWithinTheAttempts(String membershipId) {
        long giveUpAt = System.nanoTime() + membershipPatience.toNanos();
        while (true) {
            try {
                return getMemberAddressInfoPort.getMemberAddressInfo(membershipId);
            } catch (MemberAddressNotConfirmedException notConfirmed) {
                if (System.nanoTime() + membershipRetryAfter.toNanos() > giveUpAt) {
                    throw notConfirmed;
                }
                log.warn("membership-service did not give the address of member {}, asking again in {}", membershipId, membershipRetryAfter);
                waitBeforeTheNextAttempt(notConfirmed);
            }
        }
    }

    private void waitBeforeTheNextAttempt(MemberAddressNotConfirmedException notConfirmed) {
        try {
            Thread.sleep(membershipRetryAfter);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw notConfirmed;
        }
    }
}
