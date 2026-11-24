package com.jaypay.banking.adapter.in.web;

import com.jaypay.banking.application.port.in.GetRegisteredBankAccountCommand;
import com.jaypay.banking.application.port.in.GetRegisteredBankAccountUseCase;
import com.jaypay.banking.domain.RegisteredBankAccount;
import com.jaypay.common.WebAdapter;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@WebAdapter
@RestController
@RequiredArgsConstructor
public class GetRegisteredBankAccountController {

    private final GetRegisteredBankAccountUseCase getRegisteredBankAccountUseCase;

    @GetMapping(path = "/banking/account/{membershipId}")
    RegisteredBankAccount getRegisteredBankAccount(@PathVariable String membershipId) {
        GetRegisteredBankAccountCommand command = GetRegisteredBankAccountCommand.builder().membershipId(membershipId).build();
        return getRegisteredBankAccountUseCase.getRegisteredBankAccount(command);
    }

}