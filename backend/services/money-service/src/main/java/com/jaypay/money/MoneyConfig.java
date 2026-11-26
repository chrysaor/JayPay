package com.jaypay.money;

import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.config.ConfigurationScopeAwareProvider;
import org.axonframework.config.EventProcessingConfigurer;
import org.axonframework.deadline.DeadlineManager;
import org.axonframework.deadline.SimpleDeadlineManager;
import org.axonframework.eventhandling.TrackingEventProcessorConfiguration;
import org.axonframework.messaging.StreamableMessageSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@ComponentScan("com.jaypay.common")
public class MoneyConfig {

    @Autowired
    void readRechargeEventsFromTheStartOfTheStream(EventProcessingConfigurer processors) {
        processors.registerTrackingEventProcessorConfiguration("MoneyRechargeSagaProcessor",
                axon -> TrackingEventProcessorConfiguration.forSingleThreadedProcessing()
                        .andInitialTrackingToken(StreamableMessageSource::createTailToken));
    }

    @Bean
    DeadlineManager deadlineManager(org.axonframework.config.Configuration axon, TransactionManager transactionManager) {
        return SimpleDeadlineManager.builder()
                .scopeAwareProvider(new ConfigurationScopeAwareProvider(axon))
                .transactionManager(transactionManager)
                .build();
    }
}
