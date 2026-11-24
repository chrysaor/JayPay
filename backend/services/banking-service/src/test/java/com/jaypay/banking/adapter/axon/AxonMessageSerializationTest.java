package com.jaypay.banking.adapter.axon;

import com.jaypay.banking.adapter.axon.command.CreateFirmBankingRequestCommand;
import com.jaypay.banking.adapter.axon.command.CreateRegisteredBankAccountCommand;
import com.jaypay.banking.adapter.axon.command.UpdateFirmBankingRequestCommand;
import com.jaypay.banking.adapter.axon.event.CreateRegisteredBankAccountEvent;
import com.jaypay.banking.adapter.axon.event.FirmBankingRequestCreatedEvent;
import com.jaypay.banking.adapter.axon.event.FirmBankingRequestUpdatedEvent;
import org.axonframework.serialization.SimpleSerializedObject;
import org.axonframework.serialization.SimpleSerializedType;
import org.axonframework.serialization.json.JacksonSerializer;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class AxonMessageSerializationTest {

    private final JacksonSerializer serializer = JacksonSerializer.defaultSerializer();

    static Stream<Arguments> storedPayloads() {
        return Stream.of(
                Arguments.of(CreateRegisteredBankAccountCommand.class,
                        "{\"membershipId\":\"1\",\"bankName\":\"Shinhan bank\",\"bankAccountNumber\":\"12345\"}"),
                Arguments.of(CreateFirmBankingRequestCommand.class,
                        "{\"fromBankName\":\"A\",\"fromBankAccountNumber\":\"1\",\"toBankName\":\"B\",\"toBankAccountNumber\":\"2\",\"moneyAmount\":5000}"),
                Arguments.of(UpdateFirmBankingRequestCommand.class,
                        "{\"aggregateIdentifier\":\"aggregate-1\",\"firmbankingStatus\":2}"),
                Arguments.of(CreateRegisteredBankAccountEvent.class,
                        "{\"membershipId\":\"1\",\"bankName\":\"Shinhan bank\",\"bankAccountNumber\":\"12345\"}"),
                Arguments.of(FirmBankingRequestCreatedEvent.class,
                        "{\"fromBankName\":\"A\",\"fromBankAccountNumber\":\"1\",\"toBankName\":\"B\",\"toBankAccountNumber\":\"2\",\"moneyAmount\":5000}"),
                Arguments.of(FirmBankingRequestUpdatedEvent.class,
                        "{\"firmBankingStatus\":1}")
        );
    }

    @ParameterizedTest
    @MethodSource("storedPayloads")
    void readsAndWritesTheStoredJsonShape(Class<?> messageType, String storedJson) {
        Object message = serializer.deserialize(new SimpleSerializedObject<>(
                storedJson, String.class, new SimpleSerializedType(messageType.getName(), null)));

        assertThat(message).isInstanceOf(messageType);
        assertThat(serializer.serialize(message, String.class).getData()).isEqualTo(storedJson);
    }
}
