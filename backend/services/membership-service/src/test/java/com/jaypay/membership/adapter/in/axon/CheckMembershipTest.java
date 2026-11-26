package com.jaypay.membership.adapter.in.axon;

import com.jaypay.common.event.CheckMembershipCommand;
import com.jaypay.common.event.CheckedMembershipEvent;
import com.jaypay.membership.adapter.out.persistence.MembershipJpaEntity;
import com.jaypay.membership.adapter.out.persistence.SpringDataMembershipRepository;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.eventhandling.EventBus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class CheckMembershipTest {

    private final List<CheckedMembershipEvent> published = new CopyOnWriteArrayList<>();

    @Autowired
    private CommandGateway commandGateway;

    @Autowired
    private EventBus eventBus;

    @Autowired
    private SpringDataMembershipRepository memberships;

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
