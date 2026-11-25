package com.jaypay.common;

import com.jaypay.money.adapter.in.web.MoneyController;
import com.jaypay.money.aggregation.adapter.in.web.AggregationController;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;

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

    private <T> T woven(T controller) {
        AspectJProxyFactory factory = new AspectJProxyFactory(controller);
        factory.setProxyTargetClass(true);
        factory.addAspect(new LoggingAspect(producer));
        return factory.getProxy();
    }
}
