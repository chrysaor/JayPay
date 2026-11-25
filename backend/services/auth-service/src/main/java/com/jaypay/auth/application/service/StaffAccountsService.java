package com.jaypay.auth.application.service;

import com.jaypay.auth.application.port.in.AccountChange;
import com.jaypay.auth.application.port.in.NewStaffAccount;
import com.jaypay.auth.application.port.in.PasswordChange;
import com.jaypay.auth.application.port.in.SignedIn;
import com.jaypay.auth.application.port.in.StaffAccountsUseCase;
import com.jaypay.auth.application.port.out.PasswordHashing;
import com.jaypay.auth.application.port.out.StaffAccountPort;
import com.jaypay.auth.application.port.out.StaffTokenIssuer;
import com.jaypay.auth.domain.StaffAccount;
import com.jaypay.auth.domain.StaffAccountException;
import com.jaypay.common.UseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static com.jaypay.auth.domain.StaffAccountException.Reason.FORBIDDEN;
import static com.jaypay.auth.domain.StaffAccountException.Reason.NOT_FOUND;
import static com.jaypay.auth.domain.StaffAccountException.Reason.USERNAME_TAKEN;
import static com.jaypay.auth.domain.StaffAccountException.Reason.WRONG_PASSWORD;

@UseCase
@RequiredArgsConstructor
class StaffAccountsService implements StaffAccountsUseCase {

    private final StaffAccountPort accounts;
    private final PasswordHashing hashing;
    private final StaffTokenIssuer tokens;

    @Override
    public Optional<StaffAccount> find(String username) {
        return accounts.find(username);
    }

    @Override
    public List<StaffAccount> list() {
        return accounts.list();
    }

    @Override
    @Transactional
    public StaffAccount create(NewStaffAccount account) {
        StaffAccount opened = open(account);
        if (!accounts.insertIfAbsent(opened)) {
            throw new StaffAccountException(USERNAME_TAKEN);
        }
        return opened;
    }

    @Override
    @Transactional
    public boolean ensureDefaultAccount(NewStaffAccount account) {
        return accounts.find(account.username()).isEmpty() && accounts.insertIfAbsent(open(account));
    }

    @Override
    @Transactional
    public StaffAccount change(AccountChange change) {
        if (accounts.lockActiveAdmins().stream().noneMatch(admin -> admin.username().equals(change.actor()))) {
            throw new StaffAccountException(FORBIDDEN);
        }
        StaffAccount changed = existing(change.username())
                .changedBy(change.actor(), change.role(), change.active(), change.mfaEnabled());
        accounts.update(changed);
        return changed;
    }

    @Override
    @Transactional
    public void changePassword(PasswordChange change) {
        StaffAccount account = existing(change.username());
        if (!hashing.matches(change.currentPassword(), account.passwordHash())) {
            throw new StaffAccountException(WRONG_PASSWORD);
        }
        accounts.update(account.withPassword(change.newPassword(), hashing::hash));
    }

    @Override
    public SignedIn signIn(String username, Set<String> factors) {
        StaffAccount account = existing(username);
        StaffTokenIssuer.IssuedToken token = tokens.issue(account.username(), factors);
        return new SignedIn(token.value(), token.expiresAt(), account);
    }

    private StaffAccount open(NewStaffAccount account) {
        return StaffAccount.open(account.username(), account.password(), account.role(), account.mfaEnabled(), hashing::hash);
    }

    private StaffAccount existing(String username) {
        return accounts.find(username).orElseThrow(() -> new StaffAccountException(NOT_FOUND));
    }
}
