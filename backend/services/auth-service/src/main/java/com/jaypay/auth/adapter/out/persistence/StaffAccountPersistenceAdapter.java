package com.jaypay.auth.adapter.out.persistence;

import com.jaypay.auth.application.port.out.StaffAccountPort;
import com.jaypay.auth.domain.StaffAccount;
import com.jaypay.auth.domain.StaffRole;
import com.jaypay.common.PersistenceAdapter;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.Optional;

@PersistenceAdapter
@RequiredArgsConstructor
class StaffAccountPersistenceAdapter implements StaffAccountPort {

    private final SpringDataStaffAccountRepository repository;

    @Override
    public Optional<StaffAccount> find(String username) {
        return repository.findById(username).map(StaffAccountJpaEntity::toDomain);
    }

    @Override
    public List<StaffAccount> list() {
        return repository.findAllByOrderByUsernameAsc().stream().map(StaffAccountJpaEntity::toDomain).toList();
    }

    @Override
    public boolean insertIfAbsent(StaffAccount account) {
        return repository.insertIfAbsent(account.username(), account.passwordHash(), account.role().name(),
                account.active(), account.mfaEnabled()) == 1;
    }

    @Override
    public void update(StaffAccount account) {
        repository.save(StaffAccountJpaEntity.of(account));
    }

    @Override
    public List<StaffAccount> lockActiveAdmins() {
        return repository.findByRoleAndActiveTrue(StaffRole.ADMIN).stream().map(StaffAccountJpaEntity::toDomain).toList();
    }
}
