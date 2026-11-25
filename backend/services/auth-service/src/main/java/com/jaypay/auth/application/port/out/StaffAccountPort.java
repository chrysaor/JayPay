package com.jaypay.auth.application.port.out;

import com.jaypay.auth.domain.StaffAccount;

import java.util.List;
import java.util.Optional;

public interface StaffAccountPort {

    Optional<StaffAccount> find(String username);

    List<StaffAccount> list();

    boolean insertIfAbsent(StaffAccount account);

    void update(StaffAccount account);

    List<StaffAccount> lockActiveAdmins();
}
