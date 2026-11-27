package com.jaypay.common.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jaypay.messaging.MessageJsonAutoConfiguration;
import org.axonframework.serialization.SimpleSerializedObject;
import org.axonframework.serialization.SimpleSerializedType;
import org.axonframework.serialization.json.JacksonSerializer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class StoredRechargeMessagesTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String ROLLBACK_FINISHED_OF_0_4_0 =
            "{\"rollbackFirmBankingId\":\"b\",\"membershipId\":\"1\",\"rollbackFirmbankingAggregateIdentifier\":\"a\"}";

    private final JacksonSerializer serializer = JacksonSerializer.builder()
            .objectMapper(new MessageJsonAutoConfiguration().defaultAxonObjectMapper())
            .build();

    static Stream<Arguments> storedPayloads() {
        return Stream.of(
                Arguments.of(CheckMembershipCommand.class,
                        "{\"checkMembershipId\":\"m\",\"rechargingRequestId\":\"r\",\"membershipId\":\"1\"}",
                        "\"membershipId\":\"1\""),
                Arguments.of(CheckedMembershipEvent.class,
                        "{\"checkMembershipId\":\"m\",\"rechargingRequestId\":\"r\",\"membershipId\":\"1\",\"valid\":false}",
                        "\"valid\":false"),
                Arguments.of(CheckRegisteredBankAccountCommand.class,
                        "{\"aggregateIdentifier\":\"a\",\"rechargeRequestId\":\"r\",\"membershipId\":\"1\",\"checkRegisteredBankAccountId\":\"c\","
                                + "\"bankName\":\"Shinhan bank\",\"bankAccountNumber\":\"111\",\"amount\":5000}",
                        "\"amount\":5000"),
                Arguments.of(CheckedRegisteredBankAccountEvent.class,
                        "{\"rechargingRequestId\":\"r\",\"checkRegisteredBankAccountId\":\"c\",\"membershipId\":\"1\",\"amount\":5000,"
                                + "\"firmbankingRequestAggregateIdentifier\":\"f\",\"fromBankName\":\"Shinhan bank\",\"fromBankAccountNumber\":\"111\",\"checked\":true}",
                        "\"amount\":5000"),
                Arguments.of(RequestFirmBankingCommand.class,
                        "{\"requestFirmBankingId\":\"q\",\"aggregateIdentifier\":\"a\",\"rechargeRequestId\":\"r\",\"membershipId\":\"1\",\"fromBankName\":\"A\","
                                + "\"fromBankAccountNumber\":\"1\",\"toBankName\":\"B\",\"toBankAccountNumber\":\"2\",\"moneyAmount\":5000}",
                        "\"moneyAmount\":5000"),
                Arguments.of(RequestFirmBankingFinishedEvent.class,
                        "{\"requestFirmBankingId\":\"q\",\"rechargingRequestId\":\"r\",\"membershipId\":\"1\",\"toBankName\":\"B\",\"toBankAccountNumber\":\"2\","
                                + "\"moneyAmount\":5000,\"status\":0,\"requestFirmbankingAggregateIdentifier\":\"a\"}",
                        "\"moneyAmount\":5000"),
                Arguments.of(RollbackFirmBankingRequestCommand.class,
                        "{\"rollbackFirmbankingId\":\"b\",\"aggregateIdentifier\":\"a\",\"rechargeRequestId\":\"r\",\"membershipId\":\"1\",\"bankName\":\"B\","
                                + "\"bankAccountNumber\":\"2\",\"moneyAmount\":5000}",
                        "\"moneyAmount\":5000")
        );
    }

    @ParameterizedTest
    @MethodSource("storedPayloads")
    void readsAStoredIntAmountIntoTheLongField(Class<?> messageType, String storedJson, String amount) {
        Object message = serializer.deserialize(new SimpleSerializedObject<>(
                storedJson, String.class, new SimpleSerializedType(messageType.getName(), null)));

        assertThat(message).isInstanceOf(messageType);
        assertThat(serializer.serialize(message, String.class).getData()).contains(amount);
    }

    @Test
    void readsTheRollbackFinishedOfBankingService040AsAConfirmedRefund() {
        Object message = serializer.deserialize(new SimpleSerializedObject<>(ROLLBACK_FINISHED_OF_0_4_0,
                String.class, new SimpleSerializedType(RollbackFirmBankingFinishedEvent.class.getName(), null)));

        assertThat(message).isEqualTo(new RollbackFirmBankingFinishedEvent("b", "1", "a"));
    }

    @Test
    void writesAConfirmedRefundAsTheEventThatMoneyService040Reads() throws Exception {
        String written = serializer.serialize(new RollbackFirmBankingFinishedEvent("b", "1", "a"), String.class).getData();

        assertThat(JSON.readTree(written)).isEqualTo(JSON.readTree(ROLLBACK_FINISHED_OF_0_4_0));
    }

    @ParameterizedTest
    @MethodSource("storedPayloads")
    void leavesTheBankAccountNumberOutOfTheLogLine(Class<?> messageType, String storedJson, String amount) {
        Object message = serializer.deserialize(new SimpleSerializedObject<>(
                storedJson, String.class, new SimpleSerializedType(messageType.getName(), null)));

        assertThat(message.toString()).doesNotContain("111").doesNotContain("AccountNumber='2'").doesNotContain("AccountNumber=2");
    }
}
