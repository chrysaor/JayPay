package com.jaypay.banking;

import com.jaypay.banking.application.port.in.TransferMoneyUseCase;
import com.jaypay.banking.application.port.out.BankAnswer;
import com.jaypay.banking.application.port.out.ExternalBankPort;
import com.jaypay.banking.domain.BankTransfer;
import com.jaypay.common.event.RequestFirmBankingCommand;
import com.jaypay.common.event.RequestFirmBankingFinishedEvent;
import com.jaypay.common.event.RollbackFirmBankingFinishedEvent;
import com.jaypay.common.event.RollbackFirmBankingRefusedEvent;
import com.jaypay.common.event.RollbackFirmBankingRequestCommand;
import com.jaypay.common.event.RollbackFirmBankingUnconfirmedEvent;
import com.jaypay.common.kafka.KafkaTestBroker;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.eventsourcing.eventstore.EventStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BankTransferTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final int CONCURRENT_REQUESTS = 16;
    private static final String COMPANY_BANK_NAME = "jaypay-company";
    private static final String COMPANY_BANK_ACCOUNT_NUMBER = "123-333-9999";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CommandGateway commandGateway;

    @Autowired
    private EventStore eventStore;

    @Autowired
    private RecordingBank bank;

    @Autowired
    private TransferMoneyUseCase transferMoneyUseCase;

    @DynamicPropertySource
    static void kafka(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", KafkaTestBroker::bootstrapServers);
    }

    @BeforeEach
    void openTheBank() {
        bank.reset();
        bank.clear();
    }

    @Test
    void aRepeatedTransferIsSentOnceAndAnswersTheFirstResult() throws Exception {
        String requestId = newRequestId();

        for (int i = 0; i < 3; i++) {
            transfer(requestId, "222", 5000)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.requestId").value(requestId))
                    .andExpect(jsonPath("$.toBankAccountNumber").value("222"))
                    .andExpect(jsonPath("$.moneyAmount").value(5000))
                    .andExpect(jsonPath("$.transferStatus").value("SUCCEEDED"));
        }

        assertThat(bank.sent(requestId)).isEqualTo(1);
    }

    @Test
    void concurrentCopiesOfOneTransferSendItOnce() throws Exception {
        String requestId = newRequestId();
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> answers = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(CONCURRENT_REQUESTS)) {
            for (int i = 0; i < CONCURRENT_REQUESTS; i++) {
                answers.add(pool.submit(() -> {
                    start.await();
                    return transfer(requestId, "333", 7000).andReturn().getResponse().getStatus();
                }));
            }
            start.countDown();
            for (Future<Integer> answer : answers) {
                assertThat(answer.get()).isEqualTo(200);
            }
        }

        assertThat(bank.sent(requestId)).isEqualTo(1);
    }

    @Test
    void aRequestIdReusedForADifferentTransferIsRefusedAndNotSent() throws Exception {
        String requestId = newRequestId();
        transfer(requestId, "444", 1000).andExpect(status().isOk());

        transfer(requestId, "444", 1001)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("REQUEST_ID_REUSED"));
        transfer(requestId, "445", 1000).andExpect(status().isConflict());

        assertThat(bank.sent(requestId)).isEqualTo(1);
    }

    @Test
    void aTransferTheBankDidNotAnswerIsAskedAgainUnderTheSameIdAndLandsOnce() throws Exception {
        String requestId = newRequestId();
        bank.silence("555");

        transfer(requestId, "555", 3000)
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("TRANSFER_NOT_CONFIRMED"));
        transfer(requestId, "555", 3000).andExpect(status().isServiceUnavailable());

        bank.reset();
        transfer(requestId, "555", 3000)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transferStatus").value("SUCCEEDED"));
        transfer(requestId, "555", 3000).andExpect(status().isOk());

        assertThat(bank.asked(requestId)).isEqualTo(3);
        assertThat(bank.sent(requestId)).isEqualTo(1);
    }

    @Test
    void aRefusedTransferStaysRefusedWithoutAskingTheBankAgain() throws Exception {
        String requestId = newRequestId();
        bank.refuse("666");

        transfer(requestId, "666", 3000)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transferStatus").value("FAILED"));
        bank.reset();
        transfer(requestId, "666", 3000)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transferStatus").value("FAILED"));

        assertThat(bank.asked(requestId)).isEqualTo(1);
        assertThat(bank.sent(requestId)).isZero();
    }

    @Test
    void aTransferLeavesTheCompanyAccountWhateverSourceTheCallerNames() throws Exception {
        String requestId = newRequestId();

        mockMvc.perform(post("/banking/firmbanking/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"requestId": "%s", "fromBankName": "jaypay", "fromBankAccountNumber": "111-222-333", "toBankName": "KB bank", "toBankAccountNumber": "778", "moneyAmount": 2000}
                                """.formatted(requestId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fromBankName").value(COMPANY_BANK_NAME))
                .andExpect(jsonPath("$.fromBankAccountNumber").value(COMPANY_BANK_ACCOUNT_NUMBER));

        assertThat(bank.delivered).containsExactly(new BankTransfer(
                requestId, COMPANY_BANK_NAME, COMPANY_BANK_ACCOUNT_NUMBER, "KB bank", "778", 2000));
    }

    @Test
    void aTransferStoredWithAnotherSourceAccountIsAnsweredAsARepeatOfTheSameRequest() throws Exception {
        String requestId = newRequestId();
        transferMoneyUseCase.transfer(new BankTransfer(requestId, "jaypay", "an-older-company-account", "KB bank", "779", 2000));

        transfer(requestId, "779", 2000)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transferStatus").value("SUCCEEDED"));
        transfer(requestId, "779", 2001).andExpect(status().isConflict());

        assertThat(bank.sent(requestId)).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "10000001", "1.5", "null"})
    void refusesAnAmountOutsideOneWonToTheLimit(String amount) throws Exception {
        String requestId = newRequestId();

        mockMvc.perform(post("/banking/firmbanking/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(requestId, "777", amount)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_AMOUNT"));

        assertThat(bank.asked(requestId)).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "has space", "an-id-that-is-far-too-long-to-be-a-request-id-because-it-runs-past-64"})
    void refusesAMalformedRequestId(String requestId) throws Exception {
        mockMvc.perform(post("/banking/firmbanking/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(requestId, "777", "1000")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_REQUEST_ID"));
    }

    @Test
    void theRechargeChargeMovesTheMemberMoneyIntoTheCompanyAccountOnce() {
        String rechargeId = UUID.randomUUID().toString();
        String aggregateId = UUID.randomUUID().toString();

        commandGateway.sendAndWait(charge(rechargeId, aggregateId, "888", 3000));

        assertThat(bank.delivered).containsExactly(new BankTransfer(
                "recharge-" + rechargeId, "Shinhan bank", "888", COMPANY_BANK_NAME, COMPANY_BANK_ACCOUNT_NUMBER, 3000));
        assertThat(finished(aggregateId).getStatus()).isEqualTo(RequestFirmBankingFinishedEvent.SUCCEEDED);
        assertThat(finished(aggregateId).getMoneyAmount()).isEqualTo(3000L);
    }

    @Test
    void theFinishedChargeNamesTheCompanyAccountTheChargeWentTo() {
        String rechargeId = UUID.randomUUID().toString();
        String aggregateId = UUID.randomUUID().toString();

        commandGateway.sendAndWait(charge(rechargeId, aggregateId, "887", 3000));

        assertThat(finished(aggregateId)).extracting(RequestFirmBankingFinishedEvent::getToBankName, RequestFirmBankingFinishedEvent::getToBankAccountNumber)
                .containsExactly(COMPANY_BANK_NAME, COMPANY_BANK_ACCOUNT_NUMBER);
    }

    @Test
    void aChargeTheBankRefusesFinishesAsRefused() {
        String rechargeId = UUID.randomUUID().toString();
        String aggregateId = UUID.randomUUID().toString();
        bank.refuse("889");

        commandGateway.sendAndWait(charge(rechargeId, aggregateId, "889", 3000));

        assertThat(finished(aggregateId).getStatus()).isEqualTo(RequestFirmBankingFinishedEvent.REFUSED);
    }

    @Test
    void aChargeTheBankDoesNotAnswerFinishesAsUnconfirmed() {
        String rechargeId = UUID.randomUUID().toString();
        String aggregateId = UUID.randomUUID().toString();
        bank.silence("890");

        commandGateway.sendAndWait(charge(rechargeId, aggregateId, "890", 3000));

        assertThat(finished(aggregateId).getStatus()).isEqualTo(RequestFirmBankingFinishedEvent.UNCONFIRMED);
    }

    @Test
    void aRollbackReturnsALandedChargeToTheMemberOnce() {
        String rechargeId = UUID.randomUUID().toString();
        commandGateway.sendAndWait(charge(rechargeId, UUID.randomUUID().toString(), "901", 4000));

        commandGateway.sendAndWait(rollback(rechargeId));
        commandGateway.sendAndWait(rollback(rechargeId));

        assertThat(bank.delivered).containsExactly(
                new BankTransfer("recharge-" + rechargeId, "Woori bank", "901", COMPANY_BANK_NAME, COMPANY_BANK_ACCOUNT_NUMBER, 4000),
                new BankTransfer("recharge-" + rechargeId + "-refund", COMPANY_BANK_NAME, COMPANY_BANK_ACCOUNT_NUMBER, "Woori bank", "901", 4000));
    }

    @Test
    void aRollbackWhoseRefundTheBankDoesNotAnswerFinishesUnconfirmedAndTheNextRollbackAsksTheBankAgain() {
        String rechargeId = UUID.randomUUID().toString();
        String refundId = "recharge-" + rechargeId + "-refund";
        commandGateway.sendAndWait(charge(rechargeId, UUID.randomUUID().toString(), "908", 4000));
        bank.silence("908");

        assertThat(rollbackAnswerOf(rollback(rechargeId))).containsExactly(RollbackFirmBankingUnconfirmedEvent.class);
        bank.reset();
        assertThat(rollbackAnswerOf(rollback(rechargeId))).containsExactly(RollbackFirmBankingFinishedEvent.class);
        assertThat(rollbackAnswerOf(rollback(rechargeId))).containsExactly(RollbackFirmBankingFinishedEvent.class);

        assertThat(bank.asked(refundId)).isEqualTo(2);
        assertThat(bank.sent(refundId)).isEqualTo(1);
    }

    @Test
    void aRollbackWhoseRefundTheBankRefusesFinishesRefusedWithoutAskingTheBankAgain() {
        String rechargeId = UUID.randomUUID().toString();
        String refundId = "recharge-" + rechargeId + "-refund";
        commandGateway.sendAndWait(charge(rechargeId, UUID.randomUUID().toString(), "909", 4000));
        bank.refuse("909");

        assertThat(rollbackAnswerOf(rollback(rechargeId))).containsExactly(RollbackFirmBankingRefusedEvent.class);
        bank.reset();
        assertThat(rollbackAnswerOf(rollback(rechargeId))).containsExactly(RollbackFirmBankingRefusedEvent.class);

        assertThat(bank.asked(refundId)).isEqualTo(1);
        assertThat(bank.sent(refundId)).isZero();
    }

    @Test
    void aRollbackWhileTheBankStillDoesNotAnswerTheChargeFinishesUnconfirmed() {
        String rechargeId = UUID.randomUUID().toString();
        bank.silence("910");
        commandGateway.sendAndWait(charge(rechargeId, UUID.randomUUID().toString(), "910", 4000));

        assertThat(rollbackAnswerOf(rollback(rechargeId))).containsExactly(RollbackFirmBankingUnconfirmedEvent.class);
        bank.reset();
        assertThat(rollbackAnswerOf(rollback(rechargeId))).containsExactly(RollbackFirmBankingFinishedEvent.class);

        assertThat(bank.sent("recharge-" + rechargeId)).isEqualTo(1);
        assertThat(bank.sent("recharge-" + rechargeId + "-refund")).isEqualTo(1);
    }

    @Test
    void aRollbackOfAChargeTheBankRefusedFinishesConfirmedWithoutARefund() {
        String rechargeId = UUID.randomUUID().toString();
        bank.refuse("911");
        commandGateway.sendAndWait(charge(rechargeId, UUID.randomUUID().toString(), "911", 4000));
        bank.reset();

        assertThat(rollbackAnswerOf(rollback(rechargeId))).containsExactly(RollbackFirmBankingFinishedEvent.class);

        assertThat(bank.asked("recharge-" + rechargeId + "-refund")).isZero();
    }

    @Test
    void aRollbackSettlesAnUnconfirmedChargeBeforeItRefunds() {
        String rechargeId = UUID.randomUUID().toString();
        bank.silence("902");
        commandGateway.sendAndWait(charge(rechargeId, UUID.randomUUID().toString(), "902", 4000));
        bank.reset();

        commandGateway.sendAndWait(rollback(rechargeId));

        assertThat(bank.delivered).extracting(BankTransfer::requestId)
                .containsExactly("recharge-" + rechargeId, "recharge-" + rechargeId + "-refund");
        assertThat(bank.sent("recharge-" + rechargeId)).isEqualTo(1);
    }

    @Test
    void aRollbackBeforeTheChargeStopsTheChargeFromBeingSent() {
        String rechargeId = UUID.randomUUID().toString();
        String aggregateId = UUID.randomUUID().toString();

        commandGateway.sendAndWait(rollback(rechargeId));
        commandGateway.sendAndWait(charge(rechargeId, aggregateId, "903", 4000));

        assertThat(bank.delivered).isEmpty();
        assertThat(finished(aggregateId).getStatus()).isEqualTo(RequestFirmBankingFinishedEvent.REFUSED);
    }

    @Test
    void aChargeReportsItsOutcomeToTheFlowOfItsRechargeWithoutTheAccount() {
        String rechargeId = UUID.randomUUID().toString();

        commandGateway.sendAndWait(charge(rechargeId, UUID.randomUUID().toString(), "904", 3000));

        JsonNode step = flowSteps(rechargeId, 1).getFirst();
        assertThat(stepOutcomeAndReason(step)).isEqualTo("BANK_CHARGED SUCCEEDED null");
        assertThat(step.get("service").asText()).isEqualTo("banking-service");
        assertThat(step.get("membershipId").asText()).isEqualTo("1");
        assertThat(step.get("amount").asLong()).isEqualTo(3000);
        assertThat(step.toString()).doesNotContain("Woori bank", "Shinhan bank");
    }

    @Test
    void aChargeTheBankRefusesReportsWhy() {
        String rechargeId = UUID.randomUUID().toString();
        bank.refuse("905");

        commandGateway.sendAndWait(charge(rechargeId, UUID.randomUUID().toString(), "905", 3000));

        assertThat(flowSteps(rechargeId, 1)).extracting(BankTransferTest::stepOutcomeAndReason)
                .containsExactly("BANK_CHARGED FAILED The bank refused the charge");
    }

    @Test
    void aChargeTheBankDoesNotAnswerStaysInProgress() {
        String rechargeId = UUID.randomUUID().toString();
        bank.silence("906");

        commandGateway.sendAndWait(charge(rechargeId, UUID.randomUUID().toString(), "906", 3000));

        assertThat(flowSteps(rechargeId, 1)).extracting(BankTransferTest::stepOutcomeAndReason)
                .containsExactly("BANK_CHARGED IN_PROGRESS The bank has not confirmed the charge yet");
    }

    @Test
    void aRollbackReportsThatTheChargeWentBack() {
        String rechargeId = UUID.randomUUID().toString();
        commandGateway.sendAndWait(charge(rechargeId, UUID.randomUUID().toString(), "907", 4000));

        commandGateway.sendAndWait(rollback(rechargeId));

        assertThat(flowSteps(rechargeId, 2)).extracting(BankTransferTest::stepOutcomeAndReason)
                .containsExactly("BANK_CHARGED SUCCEEDED null", "CHARGE_ROLLED_BACK SUCCEEDED null");
    }

    private static List<JsonNode> flowSteps(String rechargeId, int count) {
        return KafkaTestBroker.awaitValues("jaypay.flow.events", rechargeId, values -> values.size() >= count, Duration.ofSeconds(20)).stream()
                .map(BankTransferTest::parse)
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

    private ResultActions transfer(String requestId, String toAccount, long amount) throws Exception {
        return mockMvc.perform(post("/banking/firmbanking/request")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(requestId, toAccount, String.valueOf(amount))));
    }

    private static String body(String requestId, String toAccount, String amount) {
        return """
                {"requestId": "%s", "toBankName": "KB bank", "toBankAccountNumber": "%s", "moneyAmount": %s}
                """.formatted(requestId, toAccount, amount);
    }

    private static RequestFirmBankingCommand charge(String rechargeId, String aggregateId, String memberAccount, long amount) {
        String bankName = memberAccount.startsWith("9") ? "Woori bank" : "Shinhan bank";
        return new RequestFirmBankingCommand("firmbanking-" + rechargeId, aggregateId, rechargeId, "1",
                bankName, memberAccount, amount);
    }

    private static RollbackFirmBankingRequestCommand rollback(String rechargeId) {
        return new RollbackFirmBankingRequestCommand("rollback-" + UUID.randomUUID(), UUID.randomUUID().toString(), rechargeId, "1",
                "Woori bank", "901", 4000);
    }

    private List<Class<?>> rollbackAnswerOf(RollbackFirmBankingRequestCommand rollback) {
        String aggregateId = commandGateway.sendAndWait(rollback);
        return eventStore.readEvents(aggregateId).asStream()
                .<Class<?>>map(message -> message.getPayloadType())
                .toList();
    }

    private RequestFirmBankingFinishedEvent finished(String aggregateId) {
        return eventStore.readEvents(aggregateId).asStream()
                .map(message -> message.getPayload())
                .filter(RequestFirmBankingFinishedEvent.class::isInstance)
                .map(RequestFirmBankingFinishedEvent.class::cast)
                .findFirst()
                .orElseThrow();
    }

    private static String newRequestId() {
        return "transfer-" + UUID.randomUUID();
    }

    @TestConfiguration
    static class Bank {
        @Bean
        @Primary
        RecordingBank recordingBank() {
            return new RecordingBank();
        }
    }

    static final class RecordingBank implements ExternalBankPort {
        private final Map<String, Integer> asked = new ConcurrentHashMap<>();
        private final Map<String, Integer> sent = new ConcurrentHashMap<>();
        private final Set<String> silent = ConcurrentHashMap.newKeySet();
        private final Set<String> refused = ConcurrentHashMap.newKeySet();
        private final List<BankTransfer> delivered = new CopyOnWriteArrayList<>();

        void reset() {
            silent.clear();
            refused.clear();
        }

        void clear() {
            asked.clear();
            sent.clear();
            delivered.clear();
        }

        void silence(String account) {
            silent.add(account);
        }

        void refuse(String account) {
            refused.add(account);
        }

        int asked(String requestId) {
            return asked.getOrDefault(requestId, 0);
        }

        int sent(String requestId) {
            return sent.getOrDefault(requestId, 0);
        }

        @Override
        public synchronized BankAnswer send(BankTransfer transfer) {
            asked.merge(transfer.requestId(), 1, Integer::sum);
            if (silent.contains(transfer.toBankAccountNumber()) || silent.contains(transfer.fromBankAccountNumber())) {
                return BankAnswer.NO_ANSWER;
            }
            if (sent.containsKey(transfer.requestId())) {
                return BankAnswer.ACCEPTED;
            }
            if (refused.contains(transfer.toBankAccountNumber()) || refused.contains(transfer.fromBankAccountNumber())) {
                return BankAnswer.REFUSED;
            }
            sent.put(transfer.requestId(), 1);
            delivered.add(transfer);
            return BankAnswer.ACCEPTED;
        }
    }
}
