package com.jaypay.money.adapter.in.axon;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jaypay.common.event.CheckMembershipCommand;
import com.jaypay.common.event.CheckRegisteredBankAccountCommand;
import com.jaypay.common.event.CheckedMembershipEvent;
import com.jaypay.common.event.CheckedRegisteredBankAccountEvent;
import com.jaypay.common.event.RequestFirmBankingCommand;
import com.jaypay.common.event.RequestFirmBankingFinishedEvent;
import com.jaypay.common.event.RollbackFirmBankingFinishedEvent;
import com.jaypay.common.event.RollbackFirmBankingRequestCommand;
import com.jaypay.common.event.WalletRechargedEvent;
import com.jaypay.common.kafka.KafkaTestBroker;
import com.jaypay.money.adapter.out.persistence.MemberMoneyJpaEntity;
import com.jaypay.money.adapter.out.persistence.MoneyChangingRequestJpaEntity;
import com.jaypay.money.adapter.out.persistence.SpringDataMemberMoneyRepository;
import com.jaypay.money.adapter.out.persistence.SpringDataMoneyChangingRequestRepository;
import com.jaypay.money.domain.MoneyChangingRequest.MoneyChangingStatus;
import com.sun.net.httpserver.HttpServer;
import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.eventhandling.EventHandler;
import org.axonframework.eventhandling.gateway.EventGateway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "money.recharge.deadline=3s",
        "money.recharge.rollback.retry-after=2s",
        "money.recharge.rollback.attempts=2"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RechargeSagaTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Duration PATIENCE = Duration.ofSeconds(20);
    private static final AtomicInteger MEMBERS = new AtomicInteger(500);
    private static final Set<String> WITHOUT_ACCOUNT = ConcurrentHashMap.newKeySet();
    private static final Set<String> ACCOUNT_LOOKUP_DROPPED = ConcurrentHashMap.newKeySet();
    private static final Set<String> ACCOUNT_LOOKUP_FAILING = ConcurrentHashMap.newKeySet();
    private static final HttpServer OTHER_SERVICES = startOtherServices();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SpringDataMemberMoneyRepository wallets;

    @Autowired
    private SpringDataMoneyChangingRequestRepository changes;

    @Autowired
    private FakeBanking banking;

    @Autowired
    private FakeMembership membership;

    @Autowired
    private WalletRecharges walletRecharges;

    @DynamicPropertySource
    static void otherServices(DynamicPropertyRegistry registry) {
        String url = "http://127.0.0.1:" + OTHER_SERVICES.getAddress().getPort();
        registry.add("service.membership.url", () -> url);
        registry.add("service.banking.url", () -> url);
        registry.add("spring.kafka.bootstrap-servers", KafkaTestBroker::bootstrapServers);
    }

    @AfterAll
    static void stopOtherServices() {
        OTHER_SERVICES.stop(0);
    }

    @Test
    void aRechargeTheBankConfirmsCreditsTheWalletOnce() throws Exception {
        String member = memberWithWallet();
        String requestId = UUID.randomUUID().toString();

        assertThat(recharge(requestId, member, 700).get("moneyChangingResultStatus").asInt()).isEqualTo(MoneyChangingStatus.PENDING.code());

        assertThat(eventually(() -> statusOf(requestId).filter(status -> status != MoneyChangingStatus.PENDING))).isEqualTo(MoneyChangingStatus.SUCCEEDED);
        assertThat(balanceOf(member)).isEqualTo(700);
        assertThat(recharge(requestId, member, 700).get("moneyChangingResultStatus").asInt()).isEqualTo(MoneyChangingStatus.SUCCEEDED.code());
        assertThat(banking.charges(requestId)).isEqualTo(1);
        assertThat(membership.checks(requestId)).isEqualTo(1);
    }

    @Test
    void aRechargeTheBankConfirmsTellsOtherServicesOnceThatTheWalletWasIncreased() throws Exception {
        String member = memberWithWallet();
        String requestId = UUID.randomUUID().toString();

        recharge(requestId, member, 700);

        assertThat(eventually(() -> Optional.of(walletRecharges.of(requestId)).filter(seen -> !seen.isEmpty())))
                .containsExactly(new WalletRechargedEvent(requestId, member, 700));
    }

    @Test
    void aRechargeWhoseChargeTheBankConfirmsAfterTheDeadlineIsRolledBackWithoutTellingThatTheWalletWasIncreased() throws Exception {
        String member = memberWithWallet();
        banking.hold(account(member));
        String requestId = UUID.randomUUID().toString();

        recharge(requestId, member, 700);
        assertThat(eventually(() -> statusOf(requestId).filter(status -> status != MoneyChangingStatus.PENDING))).isEqualTo(MoneyChangingStatus.FAILED);
        assertThat(eventually(() -> Optional.of(banking.rollbacks(requestId)).filter(count -> count > 0))).isEqualTo(1);
        banking.confirmHeld(requestId);
        String laterMember = memberWithWallet();
        String laterRequestId = UUID.randomUUID().toString();
        recharge(laterRequestId, laterMember, 300);
        eventually(() -> Optional.of(walletRecharges.of(laterRequestId)).filter(seen -> !seen.isEmpty()));

        assertThat(walletRecharges.of(requestId)).isEmpty();
        assertThat(balanceOf(member)).isZero();
    }

    @Test
    void aRechargeTheBankConfirmsReportsItsRequestAndItsWalletIncreaseToTheFlowTopic() throws Exception {
        String member = memberWithWallet();
        String requestId = UUID.randomUUID().toString();

        recharge(requestId, member, 700);

        List<JsonNode> steps = flowSteps(requestId, 2);
        assertThat(steps).extracting(step -> step.get("step").asText() + " " + step.get("outcome").asText())
                .containsExactly("RECHARGE_REQUESTED SUCCEEDED", "WALLET_INCREASED SUCCEEDED");
        assertThat(steps).allSatisfy(step -> {
            assertThat(step.get("flowType").asText()).isEqualTo("RECHARGE");
            assertThat(step.get("service").asText()).isEqualTo("money-service");
            assertThat(step.get("membershipId").asText()).isEqualTo(member);
            assertThat(step.get("amount").asLong()).isEqualTo(700);
        });
    }

    @Test
    void aRechargeOfAMemberThatMembershipDoesNotConfirmFailsBeforeTheBank() throws Exception {
        String member = memberWithWallet();
        membership.refuse(member);
        String requestId = UUID.randomUUID().toString();

        recharge(requestId, member, 700);

        assertThat(eventually(() -> statusOf(requestId).filter(status -> status != MoneyChangingStatus.PENDING))).isEqualTo(MoneyChangingStatus.FAILED);
        assertThat(balanceOf(member)).isZero();
        assertThat(membership.checks(requestId)).isEqualTo(1);
        assertThat(banking.checks(requestId)).isZero();
        assertThat(banking.charges(requestId)).isZero();
    }

    @Test
    void aRechargeMembershipNeverAnswersFailsAtTheDeadlineBeforeTheBank() throws Exception {
        String member = memberWithWallet();
        membership.silence(member);
        String requestId = UUID.randomUUID().toString();

        recharge(requestId, member, 700);

        assertThat(eventually(() -> statusOf(requestId).filter(status -> status != MoneyChangingStatus.PENDING))).isEqualTo(MoneyChangingStatus.FAILED);
        assertThat(balanceOf(member)).isZero();
        assertThat(banking.checks(requestId)).isZero();
        assertThat(banking.charges(requestId)).isZero();
    }

    @Test
    void aRechargeWhoseMembershipCheckCannotBeDeliveredFailsAtTheDeadlineBeforeTheBank() throws Exception {
        String member = memberWithWallet();
        membership.disconnect(member);
        String requestId = UUID.randomUUID().toString();

        recharge(requestId, member, 700);

        assertThat(eventually(() -> statusOf(requestId).filter(status -> status != MoneyChangingStatus.PENDING))).isEqualTo(MoneyChangingStatus.FAILED);
        assertThat(balanceOf(member)).isZero();
        assertThat(banking.checks(requestId)).isZero();
        assertThat(banking.charges(requestId)).isZero();
    }

    @Test
    void aRechargeFromAnAccountThatFailsTheCheckStopsBeforeTheBankAndFails() throws Exception {
        String member = memberWithWallet();
        banking.failCheckOf(account(member));
        String requestId = UUID.randomUUID().toString();

        recharge(requestId, member, 700);

        assertThat(eventually(() -> statusOf(requestId).filter(status -> status != MoneyChangingStatus.PENDING))).isEqualTo(MoneyChangingStatus.FAILED);
        assertThat(balanceOf(member)).isZero();
        assertThat(banking.charges(requestId)).isZero();
    }

    @Test
    void aRechargeTheBankRefusesFailsAndLeavesTheBalance() throws Exception {
        String member = memberWithWallet();
        banking.refuse(account(member));
        String requestId = UUID.randomUUID().toString();

        recharge(requestId, member, 700);

        assertThat(eventually(() -> statusOf(requestId).filter(status -> status != MoneyChangingStatus.PENDING))).isEqualTo(MoneyChangingStatus.FAILED);
        assertThat(balanceOf(member)).isZero();
        assertThat(recharge(requestId, member, 700).get("moneyChangingResultStatus").asInt()).isEqualTo(MoneyChangingStatus.FAILED.code());
        assertThat(banking.charges(requestId)).isEqualTo(1);
        assertThat(banking.rollbacks(requestId)).isZero();
    }

    @Test
    void aRechargeTheBankRefusesReportsWhyItFailed() throws Exception {
        String member = memberWithWallet();
        banking.refuse(account(member));
        String requestId = UUID.randomUUID().toString();

        recharge(requestId, member, 700);

        assertThat(flowSteps(requestId, 2)).extracting(RechargeSagaTest::stepOutcomeAndReason)
                .containsExactly("RECHARGE_REQUESTED SUCCEEDED null", "RECHARGE_FAILED FAILED The bank refused the charge");
    }

    @Test
    void aRechargeOfAMemberMembershipDoesNotConfirmReportsWhyItFailed() throws Exception {
        String member = memberWithWallet();
        membership.refuse(member);
        String requestId = UUID.randomUUID().toString();

        recharge(requestId, member, 700);

        assertThat(flowSteps(requestId, 2)).extracting(RechargeSagaTest::stepOutcomeAndReason)
                .containsExactly("RECHARGE_REQUESTED SUCCEEDED null", "RECHARGE_FAILED FAILED membership-service did not confirm the member");
    }

    @Test
    void aRechargeTheBankNeverConfirmsFailsAtTheDeadlineAndIsRolledBack() throws Exception {
        String member = memberWithWallet();
        banking.silence(account(member));
        String requestId = UUID.randomUUID().toString();

        recharge(requestId, member, 700);

        assertThat(eventually(() -> statusOf(requestId).filter(status -> status != MoneyChangingStatus.PENDING))).isEqualTo(MoneyChangingStatus.FAILED);
        assertThat(eventually(() -> Optional.of(banking.rollbacks(requestId)).filter(count -> count > 0))).isEqualTo(1);
        assertThat(balanceOf(member)).isZero();
    }

    @Test
    void aRechargeThatMissesItsDeadlineReportsTheDeadline() throws Exception {
        String member = memberWithWallet();
        banking.silence(account(member));
        String requestId = UUID.randomUUID().toString();

        recharge(requestId, member, 700);

        assertThat(flowSteps(requestId, 2)).extracting(RechargeSagaTest::stepOutcomeAndReason)
                .containsExactly("RECHARGE_REQUESTED SUCCEEDED null", "RECHARGE_FAILED FAILED The recharge did not finish before its deadline");
    }

    @Test
    void aRechargeOfAMemberWithoutARegisteredAccountFailsAtOnce() throws Exception {
        String member = memberWithWallet();
        WITHOUT_ACCOUNT.add(member);
        String requestId = UUID.randomUUID().toString();

        assertThat(recharge(requestId, member, 700).get("moneyChangingResultStatus").asInt()).isEqualTo(MoneyChangingStatus.FAILED.code());

        assertThat(statusOf(requestId)).contains(MoneyChangingStatus.FAILED);
        assertThat(banking.checks(requestId)).isZero();
    }

    @Test
    void aRechargeOfAMemberWithoutARegisteredAccountReportsThatItsRequestFailed() throws Exception {
        String member = memberWithWallet();
        WITHOUT_ACCOUNT.add(member);
        String requestId = UUID.randomUUID().toString();

        recharge(requestId, member, 700);

        assertThat(flowSteps(requestId, 1)).extracting(RechargeSagaTest::stepOutcomeAndReason)
                .containsExactly("RECHARGE_REQUESTED FAILED The member has no registered bank account");
    }

    @Test
    void aRechargeOfAMemberWithoutAWalletOpensAWalletWithItsAggregate() throws Exception {
        String member = String.valueOf(MEMBERS.incrementAndGet());
        String requestId = UUID.randomUUID().toString();

        recharge(requestId, member, 700);

        assertThat(eventually(() -> statusOf(requestId).filter(status -> status != MoneyChangingStatus.PENDING))).isEqualTo(MoneyChangingStatus.SUCCEEDED);
        MemberMoneyJpaEntity wallet = wallets.findByMembershipId(Long.parseLong(member)).orElseThrow();
        assertThat(wallet.getAggregateIdentifier()).isNotBlank();
        assertThat(wallet.getBalance()).isEqualTo(700);
    }

    @Test
    void openingAWalletTwiceKeepsItsFirstAggregate() throws Exception {
        String member = memberWithWallet();
        String first = wallets.findByMembershipId(Long.parseLong(member)).orElseThrow().getAggregateIdentifier();

        mockMvc.perform(post("/money/create-member-money").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"membershipId\":\"" + member + "\"}"))
                .andExpect(status().isOk());

        assertThat(wallets.findByMembershipId(Long.parseLong(member)).orElseThrow().getAggregateIdentifier()).isEqualTo(first);
    }

    @Test
    void aRepeatOfARechargeTheMemberCheckRefusedStaysFailedAfterTheMemberIsConfirmedAgain() throws Exception {
        String member = memberWithWallet();
        membership.refuse(member);
        String requestId = UUID.randomUUID().toString();

        recharge(requestId, member, 700);
        assertThat(eventually(() -> statusOf(requestId).filter(status -> status != MoneyChangingStatus.PENDING))).isEqualTo(MoneyChangingStatus.FAILED);
        membership.confirmAgain(member);

        assertThat(recharge(requestId, member, 700).get("moneyChangingResultStatus").asInt()).isEqualTo(MoneyChangingStatus.FAILED.code());
        assertThat(membership.checks(requestId)).isEqualTo(1);
        assertThat(banking.charges(requestId)).isZero();
        assertThat(balanceOf(member)).isZero();
    }

    @Test
    void aRepeatOfACreditedRechargeAfterTheMemberAndTheAccountChangeAnswersTheFirstResultWithoutChargingAgain() throws Exception {
        String member = memberWithWallet();
        String requestId = UUID.randomUUID().toString();

        recharge(requestId, member, 700);
        assertThat(eventually(() -> statusOf(requestId).filter(status -> status != MoneyChangingStatus.PENDING))).isEqualTo(MoneyChangingStatus.SUCCEEDED);
        membership.refuse(member);
        banking.refuse(account(member));
        WITHOUT_ACCOUNT.add(member);

        assertThat(recharge(requestId, member, 700).get("moneyChangingResultStatus").asInt()).isEqualTo(MoneyChangingStatus.SUCCEEDED.code());
        assertThat(membership.checks(requestId)).isEqualTo(1);
        assertThat(banking.charges(requestId)).isEqualTo(1);
        assertThat(balanceOf(member)).isEqualTo(700);
    }

    @Test
    void aRechargeWhoseAccountCheckIsNeverAnsweredFailsAtTheDeadlineWithoutACharge() throws Exception {
        String member = memberWithWallet();
        banking.silenceCheckOf(account(member));
        String requestId = UUID.randomUUID().toString();

        recharge(requestId, member, 700);

        assertThat(eventually(() -> statusOf(requestId).filter(status -> status != MoneyChangingStatus.PENDING))).isEqualTo(MoneyChangingStatus.FAILED);
        assertThat(banking.checks(requestId)).isEqualTo(1);
        assertThat(banking.charges(requestId)).isZero();
        assertThat(balanceOf(member)).isZero();
        assertThat(recharge(requestId, member, 700).get("moneyChangingResultStatus").asInt()).isEqualTo(MoneyChangingStatus.FAILED.code());
    }

    @Test
    void aRechargeWhoseAccountCheckCannotBeDeliveredFailsAtTheDeadlineWithoutACharge() throws Exception {
        String member = memberWithWallet();
        banking.disconnectCheckOf(account(member));
        String requestId = UUID.randomUUID().toString();

        recharge(requestId, member, 700);

        assertThat(eventually(() -> statusOf(requestId).filter(status -> status != MoneyChangingStatus.PENDING))).isEqualTo(MoneyChangingStatus.FAILED);
        assertThat(banking.charges(requestId)).isZero();
        assertThat(balanceOf(member)).isZero();
    }

    @Test
    void aRepeatOfARechargeTheAccountCheckRefusedStaysFailedAfterTheAccountPasses() throws Exception {
        String member = memberWithWallet();
        banking.failCheckOf(account(member));
        String requestId = UUID.randomUUID().toString();

        recharge(requestId, member, 700);
        assertThat(eventually(() -> statusOf(requestId).filter(status -> status != MoneyChangingStatus.PENDING))).isEqualTo(MoneyChangingStatus.FAILED);
        banking.passCheckOf(account(member));

        assertThat(recharge(requestId, member, 700).get("moneyChangingResultStatus").asInt()).isEqualTo(MoneyChangingStatus.FAILED.code());
        assertThat(banking.checks(requestId)).isEqualTo(1);
        assertThat(banking.charges(requestId)).isZero();
        assertThat(balanceOf(member)).isZero();
    }

    @Test
    void aRechargeWhoseChargeCannotBeDeliveredFailsAtTheDeadlineWithoutCreditingTheWallet() throws Exception {
        String member = memberWithWallet();
        banking.disconnectChargeOf(account(member));
        String requestId = UUID.randomUUID().toString();

        recharge(requestId, member, 700);

        assertThat(eventually(() -> statusOf(requestId).filter(status -> status != MoneyChangingStatus.PENDING))).isEqualTo(MoneyChangingStatus.FAILED);
        assertThat(banking.charges(requestId)).isZero();
        assertThat(balanceOf(member)).isZero();
        assertThat(walletRecharges.of(requestId)).isEmpty();
    }

    @Test
    void aRepeatWhileTheChargeIsUnansweredChargesNothingAgainAndTheDeadlineRollsTheChargeBackOnce() throws Exception {
        String member = memberWithWallet();
        banking.hold(account(member));
        String requestId = UUID.randomUUID().toString();

        recharge(requestId, member, 700);
        eventually(() -> Optional.of(banking.charges(requestId)).filter(count -> count > 0));

        assertThat(recharge(requestId, member, 700).get("moneyChangingResultStatus").asInt()).isEqualTo(MoneyChangingStatus.PENDING.code());
        assertThat(eventually(() -> statusOf(requestId).filter(status -> status != MoneyChangingStatus.PENDING))).isEqualTo(MoneyChangingStatus.FAILED);
        assertThat(eventually(() -> Optional.of(banking.rollbacks(requestId)).filter(count -> count > 0))).isEqualTo(1);
        assertThat(recharge(requestId, member, 700).get("moneyChangingResultStatus").asInt()).isEqualTo(MoneyChangingStatus.FAILED.code());
        assertThat(banking.charges(requestId)).isEqualTo(1);
        assertThat(banking.rollbacks(requestId)).isEqualTo(1);
        assertThat(balanceOf(member)).isZero();
    }

    @Test
    void aRechargeWhoseRollbackAnswerIsLostIsRolledBackAgainAndIsNotChargedAgain() throws Exception {
        String member = memberWithWallet();
        banking.silence(account(member));
        banking.silenceRollbacksOf(account(member), 1);
        String requestId = UUID.randomUUID().toString();

        recharge(requestId, member, 700);
        assertThat(eventually(() -> statusOf(requestId).filter(status -> status != MoneyChangingStatus.PENDING))).isEqualTo(MoneyChangingStatus.FAILED);
        assertThat(eventually(() -> Optional.of(banking.rollbacks(requestId)).filter(count -> count > 1))).isEqualTo(2);
        banking.answerAgain(account(member));

        assertThat(recharge(requestId, member, 700).get("moneyChangingResultStatus").asInt()).isEqualTo(MoneyChangingStatus.FAILED.code());
        assertThat(banking.charges(requestId)).isEqualTo(1);
        assertThat(balanceOf(member)).isZero();
        assertThat(walletRecharges.of(requestId)).isEmpty();
    }

    @Test
    void aRollbackBankingServiceRefusesIsSentAgainUntilBankingServiceTakesIt() throws Exception {
        String member = memberWithWallet();
        banking.silence(account(member));
        banking.failRollbacksOf(account(member), 1);
        String requestId = UUID.randomUUID().toString();

        recharge(requestId, member, 700);

        assertThat(eventually(() -> Optional.of(banking.rollbacks(requestId)).filter(count -> count > 0))).isEqualTo(1);
        assertThat(banking.failedRollbacks(requestId)).isEqualTo(1);
        assertThat(statusOf(requestId)).contains(MoneyChangingStatus.FAILED);
        assertThat(balanceOf(member)).isZero();
    }

    @Test
    void aRollbackThatIsNeverConfirmedStopsAfterItsAttemptsAndLeavesTheRechargeRollbackUnconfirmed() throws Exception {
        String member = memberWithWallet();
        banking.silence(account(member));
        banking.failRollbacksOf(account(member), Integer.MAX_VALUE);
        String requestId = UUID.randomUUID().toString();

        recharge(requestId, member, 700);

        assertThat(eventually(() -> statusOf(requestId).filter(status -> status == MoneyChangingStatus.ROLLBACK_UNCONFIRMED))).isNotNull();
        assertThat(banking.failedRollbacks(requestId)).isEqualTo(2);
        assertThat(recharge(requestId, member, 700).get("moneyChangingResultStatus").asInt()).isEqualTo(MoneyChangingStatus.ROLLBACK_UNCONFIRMED.code());
        assertThat(banking.charges(requestId)).isEqualTo(1);
        assertThat(balanceOf(member)).isZero();
    }

    @Test
    void aRechargeWhoseAccountLookupAnswerIsLostStoresNothingAndTheRepeatCreditsOnce() throws Exception {
        String member = memberWithWallet();
        ACCOUNT_LOOKUP_DROPPED.add(member);
        String requestId = UUID.randomUUID().toString();

        assertThat(unconfirmedRecharge(requestId, member, 700).get("error").asText()).isEqualTo("REGISTERED_ACCOUNT_NOT_CONFIRMED");
        assertThat(statusOf(requestId)).isEmpty();
        assertThat(banking.checks(requestId)).isZero();

        ACCOUNT_LOOKUP_DROPPED.remove(member);
        recharge(requestId, member, 700);

        assertThat(eventually(() -> statusOf(requestId).filter(status -> status != MoneyChangingStatus.PENDING))).isEqualTo(MoneyChangingStatus.SUCCEEDED);
        assertThat(recharge(requestId, member, 700).get("moneyChangingResultStatus").asInt()).isEqualTo(MoneyChangingStatus.SUCCEEDED.code());
        assertThat(banking.charges(requestId)).isEqualTo(1);
        assertThat(balanceOf(member)).isEqualTo(700);
    }

    @Test
    void aRechargeWhoseAccountLookupFailsWith5xxStoresNothingAndTheRepeatCreditsOnce() throws Exception {
        String member = memberWithWallet();
        ACCOUNT_LOOKUP_FAILING.add(member);
        String requestId = UUID.randomUUID().toString();

        assertThat(unconfirmedRecharge(requestId, member, 700).get("error").asText()).isEqualTo("REGISTERED_ACCOUNT_NOT_CONFIRMED");
        assertThat(unconfirmedRecharge(requestId, member, 700).get("error").asText()).isEqualTo("REGISTERED_ACCOUNT_NOT_CONFIRMED");
        assertThat(statusOf(requestId)).isEmpty();
        assertThat(banking.checks(requestId)).isZero();
        assertThat(balanceOf(member)).isZero();

        ACCOUNT_LOOKUP_FAILING.remove(member);
        recharge(requestId, member, 700);

        assertThat(eventually(() -> statusOf(requestId).filter(status -> status != MoneyChangingStatus.PENDING))).isEqualTo(MoneyChangingStatus.SUCCEEDED);
        assertThat(recharge(requestId, member, 700).get("moneyChangingResultStatus").asInt()).isEqualTo(MoneyChangingStatus.SUCCEEDED.code());
        assertThat(banking.charges(requestId)).isEqualTo(1);
        assertThat(balanceOf(member)).isEqualTo(700);
    }

    private String memberWithWallet() throws Exception {
        String member = String.valueOf(MEMBERS.incrementAndGet());
        mockMvc.perform(post("/money/create-member-money").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"membershipId\":\"" + member + "\"}"))
                .andExpect(status().isOk());
        assertThat(wallets.findByMembershipId(Long.parseLong(member)).orElseThrow().getAggregateIdentifier()).isNotBlank();
        return member;
    }

    private JsonNode recharge(String requestId, String member, long amount) throws Exception {
        String body = mockMvc.perform(post("/money/increase-eda").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requestId\":\"" + requestId + "\",\"targetMembershipId\":\"" + member + "\",\"amount\":" + amount + "}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JSON.readTree(body);
    }

    private JsonNode unconfirmedRecharge(String requestId, String member, long amount) throws Exception {
        String body = mockMvc.perform(post("/money/increase-eda").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requestId\":\"" + requestId + "\",\"targetMembershipId\":\"" + member + "\",\"amount\":" + amount + "}"))
                .andExpect(status().isServiceUnavailable())
                .andReturn().getResponse().getContentAsString();
        return JSON.readTree(body);
    }

    private Optional<MoneyChangingStatus> statusOf(String requestId) {
        return changes.findById(requestId).map(MoneyChangingRequestJpaEntity::getStatus);
    }

    private long balanceOf(String member) {
        return wallets.findByMembershipId(Long.parseLong(member)).map(MemberMoneyJpaEntity::getBalance).orElse(0L);
    }

    private static String account(String member) {
        return "1002-" + member;
    }

    private static List<JsonNode> flowSteps(String requestId, int count) {
        return KafkaTestBroker.awaitValues("jaypay.flow.events", requestId, values -> values.size() >= count, PATIENCE).stream()
                .map(RechargeSagaTest::parse)
                .sorted(Comparator.comparing(step -> Instant.parse(step.get("occurredAt").asText())))
                .toList();
    }

    private static JsonNode parse(String value) {
        try {
            return JSON.readTree(value);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String stepOutcomeAndReason(JsonNode step) {
        return step.get("step").asText() + " " + step.get("outcome").asText() + " " + step.get("reason").asText();
    }

    private static <T> T eventually(Supplier<Optional<T>> probe) throws InterruptedException {
        long until = System.nanoTime() + PATIENCE.toNanos();
        while (System.nanoTime() < until) {
            Optional<T> value = probe.get();
            if (value.isPresent()) {
                return value.get();
            }
            Thread.sleep(100);
        }
        throw new AssertionError("nothing happened within " + PATIENCE);
    }

    private static HttpServer startOtherServices() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/memberships/", exchange -> {
                String member = exchange.getRequestURI().getPath().substring("/memberships/".length());
                respond(exchange, 200, "{\"membershipId\":\"" + member + "\",\"name\":\"Member\",\"email\":\"member@example.com\","
                        + "\"address\":\"Seocho\",\"valid\":true,\"corp\":false}");
            });
            server.createContext("/banking/account/", exchange -> {
                String member = exchange.getRequestURI().getPath().substring("/banking/account/".length());
                if (WITHOUT_ACCOUNT.contains(member)) {
                    respond(exchange, 404, "{\"status\":404}");
                    return;
                }
                if (ACCOUNT_LOOKUP_DROPPED.contains(member)) {
                    exchange.close();
                    return;
                }
                if (ACCOUNT_LOOKUP_FAILING.contains(member)) {
                    respond(exchange, 503, "{\"status\":503}");
                    return;
                }
                respond(exchange, 200, "{\"registeredBankAccountId\":\"3\",\"membershipId\":\"" + member + "\",\"bankName\":\"jay-bank\","
                        + "\"bankAccountNumber\":\"" + account(member) + "\",\"linkedStatusIsValid\":true,\"aggregateIdentifier\":\"bank-" + member + "\"}");
            });
            server.start();
            return server;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void respond(com.sun.net.httpserver.HttpExchange exchange, int status, String json) throws IOException {
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }

    @TestConfiguration
    static class OtherServicesOverAxon {
        @Bean
        FakeBanking fakeBanking(EventGateway eventGateway) {
            return new FakeBanking(eventGateway);
        }

        @Bean
        FakeMembership fakeMembership(EventGateway eventGateway) {
            return new FakeMembership(eventGateway);
        }

        @Bean
        WalletRecharges walletRecharges() {
            return new WalletRecharges();
        }
    }

    static final class WalletRecharges {
        private final List<WalletRechargedEvent> seen = new CopyOnWriteArrayList<>();

        List<WalletRechargedEvent> of(String rechargeId) {
            return seen.stream().filter(event -> event.rechargingRequestId().equals(rechargeId)).toList();
        }

        @EventHandler
        public void on(WalletRechargedEvent event) {
            seen.add(event);
        }
    }

    static final class FakeMembership {
        private final EventGateway eventGateway;
        private final Set<String> refusedMembers = ConcurrentHashMap.newKeySet();
        private final Set<String> silentMembers = ConcurrentHashMap.newKeySet();
        private final Set<String> disconnectedMembers = ConcurrentHashMap.newKeySet();
        private final List<String> checked = new CopyOnWriteArrayList<>();

        FakeMembership(EventGateway eventGateway) {
            this.eventGateway = eventGateway;
        }

        void refuse(String member) {
            refusedMembers.add(member);
        }

        void silence(String member) {
            silentMembers.add(member);
        }

        void disconnect(String member) {
            disconnectedMembers.add(member);
        }

        void confirmAgain(String member) {
            refusedMembers.remove(member);
        }

        long checks(String rechargeId) {
            return checked.stream().filter(rechargeId::equals).count();
        }

        @CommandHandler
        public void check(CheckMembershipCommand command) {
            if (disconnectedMembers.contains(command.membershipId())) {
                throw new IllegalStateException("membership-service is not connected");
            }
            checked.add(command.rechargingRequestId());
            if (silentMembers.contains(command.membershipId())) {
                return;
            }
            eventGateway.publish(new CheckedMembershipEvent(
                    command.checkMembershipId(),
                    command.rechargingRequestId(),
                    command.membershipId(),
                    !refusedMembers.contains(command.membershipId())));
        }
    }

    static final class FakeBanking {
        private final EventGateway eventGateway;
        private final Set<String> uncheckedAccounts = ConcurrentHashMap.newKeySet();
        private final Set<String> refusedAccounts = ConcurrentHashMap.newKeySet();
        private final Set<String> silentAccounts = ConcurrentHashMap.newKeySet();
        private final Set<String> heldAccounts = ConcurrentHashMap.newKeySet();
        private final Set<String> silentChecks = ConcurrentHashMap.newKeySet();
        private final Set<String> disconnectedChecks = ConcurrentHashMap.newKeySet();
        private final Set<String> disconnectedCharges = ConcurrentHashMap.newKeySet();
        private final Map<String, Integer> silentRollbacks = new ConcurrentHashMap<>();
        private final Map<String, Integer> failingRollbacks = new ConcurrentHashMap<>();
        private final Map<String, RequestFirmBankingFinishedEvent> heldConfirmations = new ConcurrentHashMap<>();
        private final List<String> checked = new CopyOnWriteArrayList<>();
        private final List<String> charged = new CopyOnWriteArrayList<>();
        private final List<String> rolledBack = new CopyOnWriteArrayList<>();
        private final List<String> refusedRollbacks = new CopyOnWriteArrayList<>();

        FakeBanking(EventGateway eventGateway) {
            this.eventGateway = eventGateway;
        }

        void failCheckOf(String account) {
            uncheckedAccounts.add(account);
        }

        void passCheckOf(String account) {
            uncheckedAccounts.remove(account);
        }

        void silenceCheckOf(String account) {
            silentChecks.add(account);
        }

        void disconnectCheckOf(String account) {
            disconnectedChecks.add(account);
        }

        void disconnectChargeOf(String account) {
            disconnectedCharges.add(account);
        }

        void silenceRollbacksOf(String account, int times) {
            silentRollbacks.put(account, times);
        }

        void failRollbacksOf(String account, int times) {
            failingRollbacks.put(account, times);
        }

        void answerAgain(String account) {
            silentAccounts.remove(account);
        }

        void refuse(String account) {
            refusedAccounts.add(account);
        }

        void silence(String account) {
            silentAccounts.add(account);
        }

        void hold(String account) {
            heldAccounts.add(account);
        }

        void confirmHeld(String rechargeId) {
            eventGateway.publish(heldConfirmations.remove(rechargeId));
        }

        long checks(String rechargeId) {
            return checked.stream().filter(rechargeId::equals).count();
        }

        long charges(String rechargeId) {
            return charged.stream().filter(rechargeId::equals).count();
        }

        long rollbacks(String rechargeId) {
            return rolledBack.stream().filter(rechargeId::equals).count();
        }

        long failedRollbacks(String rechargeId) {
            return refusedRollbacks.stream().filter(rechargeId::equals).count();
        }

        @CommandHandler
        public void check(CheckRegisteredBankAccountCommand command) {
            if (disconnectedChecks.contains(command.getBankAccountNumber())) {
                throw new IllegalStateException("banking-service is not connected");
            }
            checked.add(command.getRechargeRequestId());
            if (silentChecks.contains(command.getBankAccountNumber())) {
                return;
            }
            eventGateway.publish(new CheckedRegisteredBankAccountEvent(
                    command.getRechargeRequestId(),
                    command.getCheckRegisteredBankAccountId(),
                    command.getMembershipId(),
                    !uncheckedAccounts.contains(command.getBankAccountNumber()),
                    command.getAmount(),
                    UUID.randomUUID().toString(),
                    command.getBankName(),
                    command.getBankAccountNumber()));
        }

        @CommandHandler
        public void charge(RequestFirmBankingCommand command) {
            String account = command.getFromBankAccountNumber();
            if (disconnectedCharges.contains(account)) {
                throw new IllegalStateException("banking-service is not connected");
            }
            charged.add(command.getRechargeRequestId());
            int status = silentAccounts.contains(account) ? RequestFirmBankingFinishedEvent.UNCONFIRMED
                    : refusedAccounts.contains(account) ? RequestFirmBankingFinishedEvent.REFUSED : RequestFirmBankingFinishedEvent.SUCCEEDED;
            RequestFirmBankingFinishedEvent finished = new RequestFirmBankingFinishedEvent(
                    command.getRequestFirmBankingId(),
                    command.getRechargeRequestId(),
                    command.getMembershipId(),
                    "Company bank",
                    "000",
                    command.getMoneyAmount(),
                    status,
                    command.getAggregateIdentifier());
            if (heldAccounts.contains(account)) {
                heldConfirmations.put(command.getRechargeRequestId(), finished);
                return;
            }
            eventGateway.publish(finished);
        }

        @CommandHandler
        public void rollback(RollbackFirmBankingRequestCommand command) {
            if (takeOne(failingRollbacks, command.getBankAccountNumber())) {
                refusedRollbacks.add(command.getRechargeRequestId());
                throw new IllegalStateException("banking-service refused the rollback");
            }
            rolledBack.add(command.getRechargeRequestId());
            if (takeOne(silentRollbacks, command.getBankAccountNumber())) {
                return;
            }
            eventGateway.publish(new RollbackFirmBankingFinishedEvent(command.getRollbackFirmbankingId(), command.getMembershipId(),
                    UUID.randomUUID().toString()));
        }

        private static boolean takeOne(Map<String, Integer> remaining, String account) {
            AtomicBoolean taken = new AtomicBoolean();
            remaining.computeIfPresent(account, (key, left) -> {
                taken.set(true);
                return left > 1 ? left - 1 : null;
            });
            return taken.get();
        }
    }
}
