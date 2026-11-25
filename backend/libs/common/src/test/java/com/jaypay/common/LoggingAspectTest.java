package com.jaypay.common;

import com.jaypay.money.adapter.in.web.MoneyController;
import com.jaypay.money.aggregation.adapter.in.web.AggregationController;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class LoggingAspectTest {

    private final LoggingProducer producer = mock(LoggingProducer.class);

    @Test
    void logsWebAdapterCallsOfAServiceDirectlyUnderJaypay() {
        woven(new MoneyController()).increase();

        verify(producer).sendMessage("logging", "Before executing method: increase");
    }

    @Test
    void logsWebAdapterCallsOfAServiceInANestedPackage() {
        woven(new AggregationController()).sum();

        verify(producer).sendMessage("logging", "Before executing method: sum");
    }

    @Test
    void sendsOnlyTheMethodNameAndNeverTheArgumentsSuchAsABankAccountNumber() {
        woven(new MoneyController()).transfer("jay-bank", "1002-3333-4444", 5000);

        ArgumentCaptor<String> message = ArgumentCaptor.forClass(String.class);
        verify(producer).sendMessage(eq("logging"), message.capture());
        assertThat(message.getValue()).isEqualTo("Before executing method: transfer").doesNotContain("1002-3333-4444");
    }

    private <T> T woven(T controller) {
        AspectJProxyFactory factory = new AspectJProxyFactory(controller);
        factory.setProxyTargetClass(true);
        factory.addAspect(new LoggingAspect(producer));
        return factory.getProxy();
    }
}
