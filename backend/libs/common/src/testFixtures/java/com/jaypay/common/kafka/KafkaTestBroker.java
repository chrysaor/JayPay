package com.jaypay.common.kafka;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.testcontainers.kafka.KafkaContainer;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

public final class KafkaTestBroker {

    private static final KafkaContainer CONTAINER = new KafkaContainer("apache/kafka:4.4.0");

    private KafkaTestBroker() {
    }

    public static synchronized String bootstrapServers() {
        if (!CONTAINER.isRunning()) {
            CONTAINER.start();
        }
        return CONTAINER.getBootstrapServers();
    }

    public static void send(String topic, String key, String value) {
        send(topic, key, value, Map.of());
    }

    public static void send(String topic, String key, String value, Map<String, String> headers) {
        Map<String, Object> config = Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers(),
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        try (KafkaProducer<String, String> producer = new KafkaProducer<>(config)) {
            ProducerRecord<String, String> record = new ProducerRecord<>(topic, key, value);
            headers.forEach((name, header) -> record.headers().add(name, header.getBytes(StandardCharsets.UTF_8)));
            producer.send(record).get(30, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new IllegalStateException("could not send to " + topic, e);
        }
    }

    public static ConsumerRecord<String, String> awaitRecord(String topic, String key, Duration timeout) {
        Map<String, Object> config = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "test-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        Instant deadline = Instant.now().plus(timeout);
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(config)) {
            consumer.subscribe(List.of(topic));
            while (Instant.now().isBefore(deadline)) {
                for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(500))) {
                    if (key.equals(record.key())) {
                        return record;
                    }
                }
            }
        }
        throw new AssertionError("no record with key " + key + " on " + topic + " within " + timeout);
    }

    public static List<String> awaitValues(String topic, String key, Predicate<List<String>> complete, Duration timeout) {
        Map<String, Object> config = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "test-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        List<String> values = new ArrayList<>();
        Instant deadline = Instant.now().plus(timeout);
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(config)) {
            consumer.subscribe(List.of(topic));
            while (Instant.now().isBefore(deadline)) {
                for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(500))) {
                    if (key.equals(record.key())) {
                        values.add(record.value());
                    }
                }
                if (complete.test(values)) {
                    return values;
                }
            }
        }
        throw new AssertionError("the records with key " + key + " on " + topic + " were still " + values + " after " + timeout);
    }

    public static String header(ConsumerRecord<String, String> record, String name) {
        var header = record.headers().lastHeader(name);
        return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
    }
}
