package com.jaypay.banking.application.service;

import lombok.extern.slf4j.Slf4j;
import com.jaypay.banking.adapter.axon.command.CreateRegisteredBankAccountCommand;
import com.jaypay.banking.adapter.out.external.bank.BankAccount;
import com.jaypay.banking.adapter.out.external.bank.GetBankAccountRequest;
import com.jaypay.banking.adapter.out.persistence.RegisteredBankAccountJpaEntity;
import com.jaypay.banking.adapter.out.persistence.RegisteredBankAccountMapper;
import com.jaypay.banking.application.port.in.GetRegisteredBankAccountCommand;
import com.jaypay.banking.application.port.in.GetRegisteredBankAccountUseCase;
import com.jaypay.banking.application.port.in.RegisterBankAccountCommand;
import com.jaypay.banking.application.port.in.RegisterBankAccountUseCase;
import com.jaypay.banking.application.port.out.*;
import com.jaypay.banking.domain.RegisteredBankAccount;
import com.jaypay.common.UseCase;
import lombok.RequiredArgsConstructor;
import org.axonframework.commandhandling.gateway.CommandGateway;

import jakarta.transaction.Transactional;

@UseCase
@RequiredArgsConstructor
@Transactional
@Slf4j
public class RegisterBankAccountService implements RegisterBankAccountUseCase, GetRegisteredBankAccountUseCase {

    private final GetMembershipPort getMembershipPort;
    private final RegisterBankAccountPort registerBankAccountPort;
    private final RequestBankAccountInfoPort requestBankAccountInfoPort;
    private final GetRegisteredBankAccountPort getRegisteredBankAccountPort;
    private final RegisteredBankAccountMapper mapper;
    private final CommandGateway commandGateway;

    @Override
    public RegisteredBankAccount registerBankAccount(RegisterBankAccountCommand command) {

        MembershipStatus membershipStatus = getMembershipPort.getMembership(command.getMembershipId());
        if (!membershipStatus.isValid()) {
            return null;
        }

        BankAccount accountInfo = requestBankAccountInfoPort.getBankAccountInfo(new GetBankAccountRequest(
                command.getBankName(), command.getBankAccountNumber())
        );

        if(accountInfo.isValid()) {
            RegisteredBankAccountJpaEntity savedAccountInfo = registerBankAccountPort.createRegisteredBankAccount(
                    new RegisteredBankAccount.MembershipId(String.format("%s", command.getMembershipId())),
                    new RegisteredBankAccount.BankName(command.getBankName()),
                    new RegisteredBankAccount.BankAccountNumber(command.getBankAccountNumber()),
                    new RegisteredBankAccount.LinkedStatusIsValid(command.isValid()),
                    new RegisteredBankAccount.AggregateIdentifier("")
            );
            return mapper.mapToDomainEntity(savedAccountInfo);
        } else {
            return null;
        }
    }

    @Override
    public void registerBankAccountByEvent(RegisterBankAccountCommand command) {

        commandGateway.send(new CreateRegisteredBankAccountCommand(command.getMembershipId(), command.getBankName(), command.getBankAccountNumber()))
                .whenComplete(
                        (result, throwable) -> {
                            if (throwable != null) {
                                log.warn("{}", throwable.getMessage());
                            } else {
                                registerBankAccountPort.createRegisteredBankAccount(
                                        new RegisteredBankAccount.MembershipId(String.format("%s", command.getMembershipId())),
                                        new RegisteredBankAccount.BankName(command.getBankName()),
                                        new RegisteredBankAccount.BankAccountNumber(command.getBankAccountNumber()),
                                        new RegisteredBankAccount.LinkedStatusIsValid(command.isValid()),
                                        new RegisteredBankAccount.AggregateIdentifier(result.toString())
                                );
                            }
                        }
                );

    }

    @Override
    public RegisteredBankAccount getRegisteredBankAccount(GetRegisteredBankAccountCommand command) {
        log.info("getRegisteredBankAccount");
        log.info("{}", command);
        return mapper.mapToDomainEntity(getRegisteredBankAccountPort.getRegisteredBankAccount(command));
    }
}