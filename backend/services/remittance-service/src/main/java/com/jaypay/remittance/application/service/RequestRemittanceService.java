package com.jaypay.remittance.application.service;

import lombok.extern.slf4j.Slf4j;
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

import jakarta.transaction.Transactional;


@UseCase
@RequiredArgsConstructor
@Transactional
@Slf4j
public class RequestRemittanceService implements RequestRemittanceUseCase {

    private final RequestRemittancePort requestRemittancePort;
    private final RemittanceRequestMapper mapper;
    private final MembershipPort membershipPort;
    private final MoneyPort moneyPort;
    private final BankingPort bankingPort;

    @Override
    public RemittanceRequest requestRemittance(RequestRemittanceCommand command) {
        RemittanceRequestJpaEntity entity = requestRemittancePort.createRemittanceRequestHistory(command);

        MembershipStatus membershipStatus = membershipPort.getMembershipStatus(command.getFromMembershipId());
        if (!membershipStatus.isValid()) {
            return null;
        }

        MoneyInfo moneyInfo = moneyPort.getMoneyInfo(command.getFromMembershipId());

        if (moneyInfo.getBalance() < command.getAmount()) {
            int rechargeAmount = (int) Math.ceil((command.getAmount() - moneyInfo.getBalance()) / 10000.0) * 10000;
            boolean moneyResult = moneyPort.requestMoneyRecharging(command.getFromMembershipId(), rechargeAmount);
            log.info("{}", moneyResult);
            if (!moneyResult) {
                return null;
            }
        }

        if (command.getRemittanceType() == 0) {
            boolean decResult;
            boolean incResult;
            decResult = moneyPort.requestMoneyDecrease(command.getFromMembershipId(), command.getAmount());
            incResult = moneyPort.requestMoneyIncrease(command.getToMembershipId(), command.getAmount());

            if (!decResult || !incResult) {
                return null;
            }
        } else if (command.getRemittanceType() == 1) {
            BankingInfo bankingInfo = bankingPort.getMembershipBankingInfo(command.getToBankName(), command.getToBankAccountNumber());
            if (!bankingInfo.isValid()) {
                return null;
            }

            boolean remittanceResult = bankingPort.requestFirmbanking(command.getToBankName(), command.getToBankAccountNumber(), command.getAmount());
            if (!remittanceResult) {
                return null;
            }
        }

        entity.setRemittanceStatus("success");
        boolean result = requestRemittancePort.saveRemittanceRequestHistory(entity);
        if (result) {
            return mapper.mapToDomainEntity(entity);
        }

        return null;
    }
}
