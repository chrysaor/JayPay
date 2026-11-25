package com.jaypay.auth.application.port.in;

import com.jaypay.auth.domain.StaffAccount;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface StaffAccountsUseCase {

    Optional<StaffAccount> find(String username);

    List<StaffAccount> list();

    StaffAccount create(NewStaffAccount account);

    boolean ensureDefaultAccount(NewStaffAccount account);

    StaffAccount change(AccountChange change);

    void changePassword(PasswordChange change);

    SignedIn signIn(String username, Set<String> factors);
}
