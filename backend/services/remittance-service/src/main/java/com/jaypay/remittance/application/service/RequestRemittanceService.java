package com.jaypay.remittance.application.service;

import lombok.extern.slf4j.Slf4j;
import com.jaypay.common.UseCase;
import com.jaypay.remittance.application.port.in.RequestRemittanceCommand;
import com.jaypay.remittance.application.port.in.RequestRemittanceUseCase;
import com.jaypay.remittance.application.port.out.NewRemittanceRequest;
import com.jaypay.remittance.application.port.out.RequestRemittancePort;
import com.jaypay.remittance.application.port.out.banking.BankingInfo;
import com.jaypay.remittance.application.port.out.banking.BankingPort;
import com.jaypay.remittance.application.port.out.membership.MembershipPort;
import com.jaypay.remittance.application.port.out.membership.MembershipStatus;
import com.jaypay.remittance.application.port.out.money.MoneyInfo;
import com.jaypay.remittance.application.port.out.money.MoneyPort;
import com.jaypay.remittance.domain.RemittanceRequest;
import lombok.RequiredArgsConstructor;

import jakarta.transaction.Transactional;


@UseCase
@RequiredArgsConstructor
@Transactional
@Slf4j
public class RequestRemittanceService implements RequestRemittanceUseCase {

    private final RequestRemittancePort requestRemittancePort;
    private final MembershipPort membershipPort;
    private final MoneyPort moneyPort;
    private final BankingPort bankingPort;

    @Override
    public RemittanceRequest requestRemittance(RequestRemittanceCommand command) {
        long remittanceRequestHistoryId = requestRemittancePort.createRemittanceRequestHistory(new NewRemittanceRequest(
                command.fromMembershipId(),
                command.toMembershipId(),
                command.toBankName(),
                command.toBankAccountNumber(),
                command.remittanceType(),
                command.amount()
        ));

        MembershipStatus membershipStatus = membershipPort.getMembershipStatus(command.fromMembershipId());
        if (!membershipStatus.isValid()) {
            return null;
        }

        MoneyInfo moneyInfo = moneyPort.getMoneyInfo(command.fromMembershipId());

        String moneyRequestId = "remittance-" + remittanceRequestHistoryId;
        if (moneyInfo.getBalance() < command.amount()) {
            long rechargeAmount = (long) Math.ceil((command.amount() - moneyInfo.getBalance()) / 10000.0) * 10000;
            boolean moneyResult = moneyPort.requestMoneyRecharging(moneyRequestId + "-recharge", command.fromMembershipId(), rechargeAmount);
            log.info("{}", moneyResult);
            if (!moneyResult) {
                return null;
            }
        }

        if (command.remittanceType() == 0) {
            boolean decResult;
            boolean incResult;
            decResult = moneyPort.requestMoneyDecrease(moneyRequestId + "-decrease", command.fromMembershipId(), command.amount());
            incResult = moneyPort.requestMoneyIncrease(moneyRequestId + "-increase", command.toMembershipId(), command.amount());

            if (!decResult || !incResult) {
                return null;
            }
        } else if (command.remittanceType() == 1) {
            BankingInfo bankingInfo = bankingPort.getMembershipBankingInfo(command.toBankName(), command.toBankAccountNumber());
            if (!bankingInfo.valid()) {
                return null;
            }

            boolean remittanceResult = bankingPort.requestFirmbanking(command.toBankName(), command.toBankAccountNumber(), command.amount());
            if (!remittanceResult) {
                return null;
            }
        }

        return requestRemittancePort.saveRemittanceRequestStatus(remittanceRequestHistoryId, "success");
    }
}
