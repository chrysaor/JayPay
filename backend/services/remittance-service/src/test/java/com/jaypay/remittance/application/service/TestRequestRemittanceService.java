package com.jaypay.remittance.application.service;

import com.jaypay.remittance.adapter.out.persistence.RemittanceRequestMapper;
import com.jaypay.remittance.application.port.in.RequestRemittanceCommand;
import com.jaypay.remittance.application.port.out.RequestRemittancePort;
import com.jaypay.remittance.application.port.out.banking.BankingPort;
import com.jaypay.remittance.application.port.out.membership.MembershipPort;
import com.jaypay.remittance.application.port.out.membership.MembershipStatus;
import com.jaypay.remittance.application.port.out.money.MoneyInfo;
import com.jaypay.remittance.application.port.out.money.MoneyPort;
import com.jaypay.remittance.domain.RemittanceRequest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.stream.Stream;

import static org.mockito.Mockito.*;

public class TestRequestRemittanceService {

    @InjectMocks
    private RequestRemittanceService requestRemittanceService;

    @Mock
    private RequestRemittancePort requestRemittancePort;
    @Mock
    private RemittanceRequestMapper mapper;
    @Mock
    private MembershipPort membershipPort;
    @Mock
    private MoneyPort moneyPort;
    @Mock
    private BankingPort bankingPort;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);

        requestRemittanceService
                = new RequestRemittanceService(requestRemittancePort, mapper, membershipPort, moneyPort, bankingPort);
    }

    private static Stream<RequestRemittanceCommand> provideRequestRemittanceCommand () {
        return Stream.of(
                RequestRemittanceCommand.builder()
                        .fromMembershipId("3")
                        .toMembershipId("4")
                        .toBankName("Bank22")
                        .remittanceType(0)
                        .toBankAccountNumber("123-456")
                        .amount(150000)
                        .build()
        );
    }

    @ParameterizedTest
    @MethodSource("provideRequestRemittanceCommand")
    public void testRequestRemittanceServiceWhenFromMembershipInvalid(RequestRemittanceCommand testCommand) {
        when(requestRemittancePort.createRemittanceRequestHistory(testCommand))
                .thenReturn(null);
        when(membershipPort.getMembershipStatus(testCommand.getFromMembershipId()))
                .thenReturn(new MembershipStatus(testCommand.getFromMembershipId(), false));

        RemittanceRequest got = requestRemittanceService.requestRemittance(testCommand);

        verify(requestRemittancePort, times(1)).createRemittanceRequestHistory(testCommand);
        verify(membershipPort, times(1)).getMembershipStatus(testCommand.getFromMembershipId());

        Assertions.assertNull(got);
    }

    @ParameterizedTest
    @MethodSource("provideRequestRemittanceCommand")
    public void testRequestRemittanceServiceWhenNotEnoughMoney(RequestRemittanceCommand testCommand) {
        MoneyInfo dummyMoneyInfo = new MoneyInfo(
                testCommand.getFromMembershipId(),
                10000
        );

        when(requestRemittancePort.createRemittanceRequestHistory(testCommand))
                .thenReturn(null);
        when(membershipPort.getMembershipStatus(testCommand.getFromMembershipId()))
                .thenReturn(new MembershipStatus(testCommand.getFromMembershipId(), true));
        when(moneyPort.getMoneyInfo(testCommand.getFromMembershipId()))
                .thenReturn(dummyMoneyInfo);

        int rechargeAmount = (int) Math.ceil((testCommand.getAmount() - dummyMoneyInfo.getBalance()) / 10000.0) * 10000;
        when(moneyPort.requestMoneyRecharging(testCommand.getFromMembershipId(), rechargeAmount))
                .thenReturn(false);

        RemittanceRequest got = requestRemittanceService.requestRemittance(testCommand);

        verify(requestRemittancePort, times(1)).createRemittanceRequestHistory(testCommand);
        verify(membershipPort, times(1)).getMembershipStatus(testCommand.getFromMembershipId());
        verify(moneyPort, times(1)).getMoneyInfo(testCommand.getFromMembershipId());
        verify(moneyPort, times(1)).requestMoneyRecharging(testCommand.getFromMembershipId(), rechargeAmount);

        Assertions.assertNull(got);
    }
}
