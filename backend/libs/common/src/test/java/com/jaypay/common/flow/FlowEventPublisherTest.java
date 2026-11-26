package com.jaypay.common.flow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.KafkaException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FlowEventPublisherTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @SuppressWarnings("unchecked")
    private final KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
    private final FlowEventPublisher publisher = new FlowEventPublisher(kafka, "jaypay.flow.events", "banking-service");

    @AfterEach
    void endTransaction() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void sendsOneRecordKeyedByTheFlowIdWithTheStepItsServiceAndItsOutcome() throws Exception {
        when(kafka.send(anyString(), anyString(), anyString())).thenReturn(new CompletableFuture<>());
        Instant before = Instant.now();

        publisher.publish("recharge-7", FlowStep.BANK_CHARGED, FlowOutcome.FAILED, "The bank refused the charge", "12", 3000L);

        ArgumentCaptor<String> value = ArgumentCaptor.forClass(String.class);
        verify(kafka).send(eq("jaypay.flow.events"), eq("recharge-7"), value.capture());
        JsonNode event = JSON.readTree(value.getValue());
        assertThat(event.get("flowType").asText()).isEqualTo("RECHARGE");
        assertThat(event.get("flowId").asText()).isEqualTo("recharge-7");
        assertThat(event.get("step").asText()).isEqualTo("BANK_CHARGED");
        assertThat(event.get("service").asText()).isEqualTo("banking-service");
        assertThat(event.get("outcome").asText()).isEqualTo("FAILED");
        assertThat(event.get("reason").asText()).isEqualTo("The bank refused the charge");
        assertThat(event.get("membershipId").asText()).isEqualTo("12");
        assertThat(event.get("amount").asLong()).isEqualTo(3000);
        assertThat(Instant.parse(event.get("occurredAt").asText())).isBetween(before, Instant.now());
    }

    @Test
    void carriesNoFieldBeyondTheFlowStepSoNoNameEmailAddressOrAccountNumberCanLeak() throws Exception {
        when(kafka.send(anyString(), anyString(), anyString())).thenReturn(new CompletableFuture<>());

        publisher.publish("recharge-8", FlowStep.MEMBERSHIP_CHECKED, FlowOutcome.SUCCEEDED, null, "12", null);

        ArgumentCaptor<String> value = ArgumentCaptor.forClass(String.class);
        verify(kafka).send(anyString(), anyString(), value.capture());
        List<String> fields = new ArrayList<>();
        JSON.readTree(value.getValue()).fieldNames().forEachRemaining(fields::add);
        assertThat(fields).containsExactlyInAnyOrder("flowType", "flowId", "step", "service", "outcome", "reason", "occurredAt", "membershipId", "amount");
    }

    @Test
    void waitsForTheTransactionOfTheStepToCommitAndStampsTheTimeOfTheStep() throws Exception {
        when(kafka.send(anyString(), anyString(), anyString())).thenReturn(new CompletableFuture<>());
        TransactionSynchronizationManager.initSynchronization();
        Instant before = Instant.now();

        publisher.publish("recharge-9", FlowStep.WALLET_INCREASED, FlowOutcome.SUCCEEDED, null, "12", 700L);
        Instant stepped = Instant.now();
        verify(kafka, never()).send(anyString(), anyString(), anyString());

        Thread.sleep(20);
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);

        ArgumentCaptor<String> value = ArgumentCaptor.forClass(String.class);
        verify(kafka).send(eq("jaypay.flow.events"), eq("recharge-9"), value.capture());
        assertThat(Instant.parse(JSON.readTree(value.getValue()).get("occurredAt").asText())).isBetween(before, stepped);
    }

    @Test
    void sendsNothingForAStepWhoseTransactionRollsBack() {
        TransactionSynchronizationManager.initSynchronization();

        publisher.publish("recharge-10", FlowStep.WALLET_INCREASED, FlowOutcome.SUCCEEDED, null, "12", 700L);
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(synchronization -> synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

        verify(kafka, never()).send(anyString(), anyString(), anyString());
    }

    @Test
    void neverThrowsIntoTheMoneyFlowWhenKafkaRefusesTheRecord() {
        when(kafka.send(anyString(), anyString(), anyString())).thenThrow(new KafkaException("broker down"));

        assertThatCode(() -> publisher.publish("recharge-11", FlowStep.BANK_CHARGED, FlowOutcome.SUCCEEDED, null, "12", 700L))
                .doesNotThrowAnyException();
    }

    @Test
    void neverThrowsIntoTheMoneyFlowWhenTheSendFailsLater() {
        when(kafka.send(anyString(), anyString(), anyString())).thenReturn(CompletableFuture.failedFuture(new KafkaException("timeout")));

        assertThatCode(() -> publisher.publish("recharge-12", FlowStep.BANK_CHARGED, FlowOutcome.SUCCEEDED, null, "12", 700L))
                .doesNotThrowAnyException();
        verify(kafka).send(any(), any(), any());
    }
}
