package com.jaypay.remittance.application.service;

import com.jaypay.common.UseCase;
import com.jaypay.remittance.adapter.out.persistence.RemittanceRequestJpaEntity;
import com.jaypay.remittance.adapter.out.persistence.RemittanceRequestMapper;
import com.jaypay.remittance.application.port.in.RequestRemittanceCommand;
import com.jaypay.remittance.application.port.in.RequestRemittanceUseCase;
import com.jaypay.remittance.application.port.out.RequestRemittancePort;
import com.jaypay.remittance.application.port.out.banking.BankingInfo;
import com.jaypay.remittance.application.port.out.banking.BankingPort;
import com.jaypay.remittance.application.port.out.membership.MembershipPort;
import com.jaypay.remittance.application.port.out.membership.MembershipStatus;
import com.jaypay.remittance.application.port.out.money.MoneyInfo;
import com.jaypay.remittance.application.port.out.money.MoneyPort;
import com.jaypay.remittance.domain.RemittanceRequest;
import lombok.RequiredArgsConstructor;

import javax.transaction.Transactional;


@UseCase
@RequiredArgsConstructor
@Transactional
public class RequestRemittanceService implements RequestRemittanceUseCase {

    private final RequestRemittancePort requestRemittancePort;
    private final RemittanceRequestMapper mapper;
    private final MembershipPort membershipPort;
    private final MoneyPort moneyPort;
    private final BankingPort bankingPort;

    @Override
    public RemittanceRequest requestRemittance(RequestRemittanceCommand command) {
        /*
         * 0. Write a remittance request and update status 'start' (persistence layer)
         * 1. From membership status check (membership service)
         * 2. Check account balance (money service)
         * 2-1. In case of the balance isn't enough, request charge (money service)
         * 3. Check remittance type (customer/bank)
         * 3.1. [Internal] - from customer money -, to customer money +
         * 3.2. [External bank]
         * 3.2.1 External bank account validation (banking service)
         * 3.2.2 If valid, request firm-banking to external account (banking service)
         * 4. Update a remittance request status to 'success' (persistence layer)
         * 5. Write a remittance (persistence layer)
         */

        // 0. Write a remittance request and update status 'start' (persistence layer)
        RemittanceRequestJpaEntity entity = requestRemittancePort.createRemittanceRequestHistory(command);

        // 1. From membership status check (membership service)
        MembershipStatus membershipStatus = membershipPort.getMembershipStatus(command.getFromMembershipId());
        if (!membershipStatus.isValid()) {
            return null;
        }

        // 2. Check account balance (money service)
        MoneyInfo moneyInfo = moneyPort.getMoneyInfo(command.getFromMembershipId());

        // 2-1. In case of the balance isn't enough, request charge (money service)
        if (moneyInfo.getBalance() < command.getAmount()) {
            int rechargeAmount = (int) Math.ceil((command.getAmount() - moneyInfo.getBalance()) / 10000.0) * 10000;
            boolean moneyResult = moneyPort.requestMoneyRecharging(command.getFromMembershipId(), rechargeAmount);

            if (!moneyResult) {
                return null;
            }
        }

        // 3. Check remittance type (customer/bank)
        if (command.getRemittanceType() == 0) {
            // 3.1. [Internal] - from customer money -, to customer money +
            boolean decResult;
            boolean incResult;
            decResult = moneyPort.requestMoneyDecrease(command.getFromMembershipId(), command.getAmount());
            incResult = moneyPort.requestMoneyIncrease(command.getToMembershipId(), command.getAmount());

            if (!decResult || !incResult) {
                return null;
            }
        } else if (command.getRemittanceType() == 1) {
            // 3.2. [External bank]
            // 3.2.1 External bank account validation (banking service)
            BankingInfo bankingInfo = bankingPort.getMembershipBankingInfo(command.getToBankName(), command.getToBankAccountNumber());
            if (!bankingInfo.isValid()) {
                return null;
            }

            // 3.2.2 If valid, request firm-banking to external account (banking service)
            boolean remittanceResult = bankingPort.requestFirmbanking(command.getToBankName(), command.getToBankAccountNumber(), command.getAmount());
            if (!remittanceResult) {
                return null;
            }
        }

        // 4. Update a remittance request status to 'success' (persistence layer)
        entity.setRemittanceStatus("success");
        boolean result = requestRemittancePort.saveRemittanceRequestHistory(entity);
        if (result) {
            return mapper.mapToDomainEntity(entity);
        }

        return null;
    }
}
