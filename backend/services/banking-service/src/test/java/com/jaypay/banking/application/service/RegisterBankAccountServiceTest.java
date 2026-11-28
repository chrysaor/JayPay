package com.jaypay.banking.application.service;

import com.jaypay.banking.application.port.in.RegisterBankAccountCommand;
import com.jaypay.banking.application.port.out.GetMembershipPort;
import com.jaypay.banking.application.port.out.GetRegisteredBankAccountPort;
import com.jaypay.banking.application.port.out.MembershipStatus;
import com.jaypay.banking.application.port.out.RegisterBankAccountPort;
import com.jaypay.banking.application.port.out.RegisteredBankAccountAggregatePort;
import com.jaypay.banking.application.port.out.RequestBankAccountInfoPort;
import com.jaypay.banking.domain.ExternalBankingInfo;
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
    private final GetMembershipPort getMembershipPort = mock(GetMembershipPort.class);
    private final RequestBankAccountInfoPort requestBankAccountInfoPort = mock(RequestBankAccountInfoPort.class);
    private final RegisterBankAccountService service = new RegisterBankAccountService(
            getMembershipPort, registerBankAccountPort, requestBankAccountInfoPort,
            mock(GetRegisteredBankAccountPort.class), aggregatePort);

    @Test
    void registersNothingForAnAccountTheBankReportsAsNotValid() {
        when(getMembershipPort.getMembership("8")).thenReturn(new MembershipStatus("8", true));
        when(requestBankAccountInfoPort.getBankAccountInfo("jaybank", "100-200-400")).thenReturn(
                ExternalBankingInfo.generatedExternalBankingInfo(
                        new ExternalBankingInfo.BankName("jaybank"),
                        new ExternalBankingInfo.BankAccountNumber("100-200-400"),
                        new ExternalBankingInfo.IsValid(false)));

        assertThat(service.registerBankAccount(new RegisterBankAccountCommand("8", "jaybank", "100-200-400", true))).isNull();

        verifyNoInteractions(aggregatePort, registerBankAccountPort);
    }

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
