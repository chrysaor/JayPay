package com.jaypay.banking.adapter.out.persistence;

import com.jaypay.banking.application.port.out.GetRegisteredBankAccountPort;
import com.jaypay.banking.application.port.out.RegisterBankAccountPort;
import com.jaypay.banking.domain.RegisteredBankAccount;
import com.jaypay.common.PersistenceAdapter;
import lombok.RequiredArgsConstructor;

import java.util.Optional;

@PersistenceAdapter
@RequiredArgsConstructor
public class RegisteredBankAccountPersistenceAdapter implements RegisterBankAccountPort, GetRegisteredBankAccountPort {

    private final SpringDataRegisteredBankAccountRepository bankAccountRepository;

    @Override
    public RegisteredBankAccount createRegisteredBankAccount(RegisteredBankAccount.MembershipId membershipId, RegisteredBankAccount.BankName bankName, RegisteredBankAccount.BankAccountNumber bankAccountNumber, RegisteredBankAccount.LinkedStatusIsValid linkedStatusIsValid, RegisteredBankAccount.AggregateIdentifier aggregateIdentifier) {
        return toDomain(bankAccountRepository.save(
                new RegisteredBankAccountJpaEntity(
                        membershipId.getMembershipId(),
                        bankName.getBankName(),
                        bankAccountNumber.getBankAccountNumber(),
                        linkedStatusIsValid.isLinkedStatusIsValid(),
                        aggregateIdentifier.getAggregateIdentifier()
                )
        ));
    }

    @Override
    public Optional<RegisteredBankAccount> getRegisteredBankAccount(String membershipId) {
        return bankAccountRepository.findFirstByMembershipIdOrderByRegisteredBankAccountIdDesc(membershipId)
                .map(RegisteredBankAccountPersistenceAdapter::toDomain);
    }

    private static RegisteredBankAccount toDomain(RegisteredBankAccountJpaEntity entity) {
        return RegisteredBankAccount.generateRegisteredBankAccount(
                new RegisteredBankAccount.RegisteredBankAccountId(entity.getRegisteredBankAccountId() + ""),
                new RegisteredBankAccount.MembershipId(entity.getMembershipId()),
                new RegisteredBankAccount.BankName(entity.getBankName()),
                new RegisteredBankAccount.BankAccountNumber(entity.getBankAccountNumber()),
                new RegisteredBankAccount.LinkedStatusIsValid(entity.isLinkedStatusIsValid()),
                new RegisteredBankAccount.AggregateIdentifier(entity.getAggregateIdentifier())
        );
    }
}
