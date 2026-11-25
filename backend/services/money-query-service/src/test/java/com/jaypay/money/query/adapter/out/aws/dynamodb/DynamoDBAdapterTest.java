package com.jaypay.money.query.adapter.out.aws.dynamodb;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeDefinition;
import software.amazon.awssdk.services.dynamodb.model.KeySchemaElement;
import software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DynamoDBAdapterTest {

    private static final Path TABLE_SCHEMA = Path.of("../../../infra/terraform/modules/dynamodb-money-sum/table.json");
    private static final String TABLE_NAME = "money-sum-adapter-test";

    private static final GenericContainer<?> DYNAMODB = new GenericContainer<>("amazon/dynamodb-local:3.3.2")
            .withCommand("-jar", "DynamoDBLocal.jar", "-inMemory")
            .withExposedPorts(8000);

    private static DynamoDbClient client;

    @BeforeAll
    static void createTableFromTheSharedSchema() {
        DYNAMODB.start();
        client = DynamoDbClient.builder()
                .endpointOverride(URI.create("http://" + DYNAMODB.getHost() + ":" + DYNAMODB.getMappedPort(8000)))
                .region(Region.AP_NORTHEAST_2)
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("test", "test")))
                .build();
        JsonNode schema = JsonMapper.builder().build().readTree(TABLE_SCHEMA.toFile());
        client.createTable(table -> table
                .tableName(TABLE_NAME)
                .billingMode(schema.get("BillingMode").asString())
                .attributeDefinitions(schema.get("AttributeDefinitions").valueStream()
                        .map(attribute -> AttributeDefinition.builder()
                                .attributeName(attribute.get("AttributeName").asString())
                                .attributeType(attribute.get("AttributeType").asString())
                                .build())
                        .toList())
                .keySchema(schema.get("KeySchema").valueStream()
                        .map(key -> KeySchemaElement.builder()
                                .attributeName(key.get("AttributeName").asString())
                                .keyType(key.get("KeyType").asString())
                                .build())
                        .toList()));
    }

    @AfterAll
    static void stop() {
        client.close();
        DYNAMODB.stop();
    }

    @Test
    void addsEveryIncreaseToTheTotalOfItsAddressInTheConfiguredTable() {
        DynamoDBAdapter adapter = new DynamoDBAdapter(client, TABLE_NAME);

        adapter.insertMoneyIncreaseEventByAddress("Seocho", 3000);
        adapter.insertMoneyIncreaseEventByAddress("Seocho", 2000);
        adapter.insertMoneyIncreaseEventByAddress("Mapo", 700);

        assertThat(adapter.getMoneySumByAddressPort("Seocho")).isEqualTo(5000);
        assertThat(adapter.getMoneySumByAddressPort("Mapo")).isEqualTo(700);
        assertThat(adapter.getMoneySumByAddressPort("Jongno")).isZero();
    }

    @Test
    void reportsAFailingTableInsteadOfAnsweringZero() {
        DynamoDBAdapter adapter = new DynamoDBAdapter(client, "no-such-table");

        assertThatThrownBy(() -> adapter.getMoneySumByAddressPort("Seocho")).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> adapter.insertMoneyIncreaseEventByAddress("Seocho", 3000)).isInstanceOf(ResourceNotFoundException.class);
    }
}
