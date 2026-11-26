package com.jaypay.money.adapter.in.axon;

import com.jaypay.common.event.CheckMembershipCommand;
import com.jaypay.common.kafka.KafkaTestBroker;
import com.jaypay.money.adapter.axon.event.RechargingRequestCreatedEvent;
import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.config.EventProcessingConfiguration;
import org.axonframework.eventhandling.EventProcessor;
import org.axonframework.eventhandling.gateway.EventGateway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "spring.datasource.url=jdbc:tc:postgresql:19.0-alpine:///first_start")
@ActiveProfiles("test")
class RechargeSagaFirstStartTest {

    private static final String PROCESSOR = "MoneyRechargeSagaProcessor";
    private static final Duration PATIENCE = Duration.ofSeconds(20);

    @Autowired
    private EventProcessingConfiguration processors;

    @Autowired
    private JdbcTemplate database;

    @Autowired
    private EventGateway events;

    @Autowired
    private MembershipChecks membershipChecks;

    @DynamicPropertySource
    static void kafka(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", KafkaTestBroker::bootstrapServers);
    }

    @Test
    void aRechargeRequestedBeforeTheSagaProcessorFirstStartsIsStillProcessed() throws Exception {
        EventProcessor processor = processors.eventProcessor(PROCESSOR).orElseThrow();
        processor.shutDown();
        database.update("delete from token_entry where processor_name = ?", PROCESSOR);
        String requestId = UUID.randomUUID().toString();

        events.publish(new RechargingRequestCreatedEvent(requestId, "900", 700, "bank-900", "jay-bank", "1002-900"));
        processor.start();

        assertThat(membershipChecks.eventuallyContains(requestId)).isTrue();
    }

    @TestConfiguration
    static class MembershipOverAxon {
        @Bean
        MembershipChecks membershipChecks() {
            return new MembershipChecks();
        }
    }

    static final class MembershipChecks {
        private final List<String> checked = new CopyOnWriteArrayList<>();

        @CommandHandler
        public void check(CheckMembershipCommand command) {
            checked.add(command.rechargingRequestId());
        }

        boolean eventuallyContains(String rechargeId) throws InterruptedException {
            long until = System.nanoTime() + PATIENCE.toNanos();
            while (System.nanoTime() < until) {
                if (checked.contains(rechargeId)) {
                    return true;
                }
                Thread.sleep(100);
            }
            return false;
        }
    }
}
