package com.jaypay.banking.adapter.axon;

import com.jaypay.banking.adapter.axon.command.CreateRegisteredBankAccountCommand;
import com.jaypay.banking.adapter.axon.event.CreateRegisteredBankAccountEvent;
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
                Arguments.of(CreateRegisteredBankAccountEvent.class,
                        "{\"membershipId\":\"1\",\"bankName\":\"Shinhan bank\",\"bankAccountNumber\":\"12345\"}")
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
