package com.jaypay.banking.application.service;

import com.jaypay.banking.application.port.in.RegisterBankAccountCommand;
import com.jaypay.banking.application.port.out.GetMembershipPort;
import com.jaypay.banking.application.port.out.GetRegisteredBankAccountPort;
import com.jaypay.banking.application.port.out.RegisterBankAccountPort;
import com.jaypay.banking.application.port.out.RegisteredBankAccountAggregatePort;
import com.jaypay.banking.application.port.out.RequestBankAccountInfoPort;
import com.jaypay.common.logging.LogRecorder;
import org.axonframework.commandhandling.CommandExecutionException;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RegisterBankAccountServiceTest {

    private final RegisterBankAccountPort registerBankAccountPort = mock(RegisterBankAccountPort.class);
    private final RegisteredBankAccountAggregatePort aggregatePort = mock(RegisteredBankAccountAggregatePort.class);
    private final RegisterBankAccountService service = new RegisterBankAccountService(
            mock(GetMembershipPort.class), registerBankAccountPort, mock(RequestBankAccountInfoPort.class),
            mock(GetRegisteredBankAccountPort.class), aggregatePort);

    @Test
    void logsARegistrationByEventThatAxonRefusedAsErrorWithTheMemberAndTheExceptionButNotTheAccountNumber() {
        when(aggregatePort.createRegisteredBankAccountAggregate(any(), any(), any()))
                .thenReturn(CompletableFuture.failedFuture(new CommandExecutionException("no handler", null)));

        try (LogRecorder logs = LogRecorder.of(RegisterBankAccountService.class)) {
            service.registerBankAccountByEvent(new RegisterBankAccountCommand("7", "jaybank", "100-200-300", true));

            assertThat(logs.lines()).containsExactly(
                    "ERROR The bank account of member 7 was not registered [org.axonframework.commandhandling.CommandExecutionException]");
        }
        verifyNoInteractions(registerBankAccountPort);
    }
}
