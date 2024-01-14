package com.jaypay.banking.application.service;

import com.jaypay.banking.adapter.out.external.bank.BankAccount;
import com.jaypay.banking.adapter.out.external.bank.GetBankAccountRequest;
import com.jaypay.banking.adapter.out.persistence.RegisteredBankAccountJpaEntity;
import com.jaypay.banking.adapter.out.persistence.RegisteredBankAccountMapper;
import com.jaypay.banking.application.port.in.RegisterBankAccountCommand;
import com.jaypay.banking.application.port.in.RegisterBankAccountUseCase;
import com.jaypay.banking.application.port.out.GetMembershipPort;
import com.jaypay.banking.application.port.out.MembershipStatus;
import com.jaypay.banking.application.port.out.RegisterBankAccountPort;
import com.jaypay.banking.application.port.out.RequestBankAccountInfoPort;
import com.jaypay.banking.domain.RegisteredBankAccount;
import com.jaypay.common.UseCase;
import lombok.RequiredArgsConstructor;

import javax.transaction.Transactional;

@UseCase
@RequiredArgsConstructor
@Transactional
public class RegisterBankAccountService implements RegisterBankAccountUseCase {

    private final GetMembershipPort getMembershipPort;
    private final RegisterBankAccountPort registerBankAccountPort;
    private final RequestBankAccountInfoPort requestBankAccountInfoPort;
    private final RegisteredBankAccountMapper mapper;

    @Override
    public RegisteredBankAccount registerBankAccount(RegisterBankAccountCommand command) {

        // 은행 계좌를 등록해야하는 서비스 (비즈니스 로직) -> 멤버십 서비스 호출 필요
        // command.getMembershipId()
        // Call membership service
        MembershipStatus membershipStatus = getMembershipPort.getMembership(command.getMembershipId());
        if (!membershipStatus.isValid()) {
            return null;
        }

        // 1. 외부 실제 은행에 등록이 가능한 계좌인지(정상인지) 확인한다.
        // 외부의 은행에 이 계좌 정상인지? 확인을 해야해요.
        // Biz Logic -> External System
        // Port -> Adapter -> External System
        // 실제 외부의 은행계좌 정보를 Get
        BankAccount accountInfo = requestBankAccountInfoPort.getBankAccountInfo(new GetBankAccountRequest(
                command.getBankName(), command.getBankAccountNumber())
        );

        // 2. 등록가능한 계좌라면, 등록한다. 성공하면, 등록에 성공한 등록 정보를 리턴
        // 2-1. 등록가능하지 않은 계좌라면. 에러를 리턴
        if(accountInfo.isValid()) {
            // 등록 정보 저장
            RegisteredBankAccountJpaEntity savedAccountInfo = registerBankAccountPort.createRegisteredBankAccount(
                    new RegisteredBankAccount.MembershipId(String.format("%s", command.getMembershipId())),
                    new RegisteredBankAccount.BankName(command.getBankName()),
                    new RegisteredBankAccount.BankAccountNumber(command.getBankAccountNumber()),
                    new RegisteredBankAccount.LinkedStatusIsValid(command.isValid())
            );
            return mapper.mapToDomainEntity(savedAccountInfo);
        } else {
            return null;
        }
    }
}