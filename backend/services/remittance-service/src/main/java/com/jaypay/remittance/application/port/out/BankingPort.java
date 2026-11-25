package com.jaypay.remittance.application.port.out;

import com.jaypay.remittance.domain.Receiver;

public interface BankingPort {

    boolean isValid(Receiver.BankAccount account);

    Outcome transfer(Receiver.BankAccount account, long amount);
}
