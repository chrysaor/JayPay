package com.jaypay.membership.adapter.in.axon;

import com.jaypay.common.event.CheckMembershipCommand;
import com.jaypay.common.event.CheckedMembershipEvent;
import com.jaypay.common.kafka.KafkaTestBroker;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jaypay.membership.adapter.out.persistence.MembershipJpaEntity;
import com.jaypay.membership.adapter.out.persistence.SpringDataMembershipRepository;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.eventhandling.EventBus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class CheckMembershipTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final List<CheckedMembershipEvent> published = new CopyOnWriteArrayList<>();

    @Autowired
    private CommandGateway commandGateway;

    @Autowired
    private EventBus eventBus;

    @Autowired
    private SpringDataMembershipRepository memberships;

    @DynamicPropertySource
    static void kafka(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", KafkaTestBroker::bootstrapServers);
    }

    @BeforeEach
    void listen() {
        eventBus.subscribe(messages -> messages.forEach(message -> {
            if (message.getPayload() instanceof CheckedMembershipEvent event) {
                published.add(event);
            }
        }));
    }

    @Test
    void confirmsAValidMember() {
        String member = saved(true);

        assertThat(check(member)).isEqualTo(true);
    }

    @Test
    void refusesAMemberThatIsNotValid() {
        String member = saved(false);

        assertThat(check(member)).isEqualTo(false);
    }

    @Test
    void refusesAMemberThatDoesNotExist() {
        assertThat(check("900000000000000001")).isEqualTo(false);
    }

    @Test
    void reportsTheCheckOfAValidMemberToTheFlowOfItsRecharge() throws Exception {
        String member = saved(true);
        String rechargeId = UUID.randomUUID().toString();

        commandGateway.sendAndWait(new CheckMembershipCommand(UUID.randomUUID().toString(), rechargeId, member));

        JsonNode step = flowStep(rechargeId);
        assertThat(step.get("step").asText()).isEqualTo("MEMBERSHIP_CHECKED");
        assertThat(step.get("outcome").asText()).isEqualTo("SUCCEEDED");
        assertThat(step.get("service").asText()).isEqualTo("membership-service");
        assertThat(step.get("membershipId").asText()).isEqualTo(member);
        assertThat(step.toString()).doesNotContain("member@example.com", "Seocho");
    }

    @Test
    void reportsWhyTheCheckOfAMemberThatDoesNotExistFailed() throws Exception {
        String rechargeId = UUID.randomUUID().toString();

        commandGateway.sendAndWait(new CheckMembershipCommand(UUID.randomUUID().toString(), rechargeId, "900000000000000002"));

        JsonNode step = flowStep(rechargeId);
        assertThat(step.get("step").asText()).isEqualTo("MEMBERSHIP_CHECKED");
        assertThat(step.get("outcome").asText()).isEqualTo("FAILED");
        assertThat(step.get("reason").asText()).isEqualTo("The member does not exist or is not valid");
    }

    private static JsonNode flowStep(String rechargeId) throws Exception {
        List<String> values = KafkaTestBroker.awaitValues("jaypay.flow.events", rechargeId, found -> !found.isEmpty(), Duration.ofSeconds(20));
        assertThat(values).hasSize(1);
        return JSON.readTree(values.getFirst());
    }

    private Boolean check(String member) {
        String checkId = UUID.randomUUID().toString();
        String rechargeId = UUID.randomUUID().toString();

        commandGateway.sendAndWait(new CheckMembershipCommand(checkId, rechargeId, member));

        return published.stream()
                .filter(event -> event.checkMembershipId().equals(checkId))
                .filter(event -> event.rechargingRequestId().equals(rechargeId) && event.membershipId().equals(member))
                .map(CheckedMembershipEvent::valid)
                .reduce((first, second) -> {
                    throw new AssertionError("more than one answer to check " + checkId);
                })
                .orElse(null);
    }

    private String saved(boolean valid) {
        return memberships.save(new MembershipJpaEntity("Member", "member@example.com", "Seocho", valid, false, "")).getMembershipId().toString();
    }
}
