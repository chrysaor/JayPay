package com.jaypay.banking.application.service;

import com.jaypay.banking.application.port.in.GetRegisteredBankAccountCommand;
import com.jaypay.banking.application.port.in.GetRegisteredBankAccountUseCase;
import com.jaypay.banking.application.port.in.RegisterBankAccountCommand;
import com.jaypay.banking.application.port.in.RegisterBankAccountUseCase;
import com.jaypay.banking.application.port.out.GetMembershipPort;
import com.jaypay.banking.application.port.out.GetRegisteredBankAccountPort;
import com.jaypay.banking.application.port.out.MembershipStatus;
import com.jaypay.banking.application.port.out.RegisterBankAccountPort;
import com.jaypay.banking.application.port.out.RegisteredBankAccountAggregatePort;
import com.jaypay.banking.application.port.out.RequestBankAccountInfoPort;
import com.jaypay.banking.domain.ExternalBankingInfo;
import com.jaypay.banking.domain.RegisteredBankAccount;
import com.jaypay.common.UseCase;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@UseCase
@RequiredArgsConstructor
@Transactional
@Slf4j
public class RegisterBankAccountService implements RegisterBankAccountUseCase, GetRegisteredBankAccountUseCase {

    private final GetMembershipPort getMembershipPort;
    private final RegisterBankAccountPort registerBankAccountPort;
    private final RequestBankAccountInfoPort requestBankAccountInfoPort;
    private final GetRegisteredBankAccountPort getRegisteredBankAccountPort;
    private final RegisteredBankAccountAggregatePort registeredBankAccountAggregatePort;

    @Override
    public RegisteredBankAccount registerBankAccount(RegisterBankAccountCommand command) {
        MembershipStatus membershipStatus = getMembershipPort.getMembership(command.membershipId());
        if (!membershipStatus.isValid()) {
            return null;
        }

        ExternalBankingInfo accountInfo = requestBankAccountInfoPort.getBankAccountInfo(command.bankName(), command.bankAccountNumber());
        if (!accountInfo.isValid()) {
            return null;
        }

        String aggregateIdentifier = registeredBankAccountAggregatePort.createRegisteredBankAccountAggregate(
                command.membershipId(), command.bankName(), command.bankAccountNumber()).join();
        return createRegisteredBankAccount(command, aggregateIdentifier);
    }

    @Override
    public void registerBankAccountByEvent(RegisterBankAccountCommand command) {
        registeredBankAccountAggregatePort.createRegisteredBankAccountAggregate(command.membershipId(), command.bankName(), command.bankAccountNumber())
                .whenComplete((aggregateIdentifier, throwable) -> {
                    if (throwable != null) {
                        log.warn("{}", throwable.getMessage());
                    } else {
                        createRegisteredBankAccount(command, aggregateIdentifier);
                    }
                });
    }

    @Override
    public RegisteredBankAccount getRegisteredBankAccount(GetRegisteredBankAccountCommand command) {
        log.info("getRegisteredBankAccount");
        log.info("{}", command);
        return getRegisteredBankAccountPort.getRegisteredBankAccount(command.membershipId()).orElseThrow();
    }

    private RegisteredBankAccount createRegisteredBankAccount(RegisterBankAccountCommand command, String aggregateIdentifier) {
        return registerBankAccountPort.createRegisteredBankAccount(
                new RegisteredBankAccount.MembershipId(command.membershipId()),
                new RegisteredBankAccount.BankName(command.bankName()),
                new RegisteredBankAccount.BankAccountNumber(command.bankAccountNumber()),
                new RegisteredBankAccount.LinkedStatusIsValid(command.isValid()),
                new RegisteredBankAccount.AggregateIdentifier(aggregateIdentifier)
        );
    }
}
