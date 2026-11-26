package com.jaypay.money.query.adapter.out.aws.dynamodb;

import org.testcontainers.containers.GenericContainer;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeDefinition;
import software.amazon.awssdk.services.dynamodb.model.KeySchemaElement;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.nio.file.Path;

public final class DynamoDbLocal implements AutoCloseable {

    private static final Path TABLE_SCHEMA = Path.of("../../../infra/terraform/modules/dynamodb-money-sum/table.json");

    private final GenericContainer<?> container = new GenericContainer<>("amazon/dynamodb-local:3.3.2")
            .withCommand("-jar", "DynamoDBLocal.jar", "-inMemory")
            .withExposedPorts(8000);
    private final String tableName;
    private final DynamoDbClient client;

    private DynamoDbLocal(String tableName) {
        this.tableName = tableName;
        container.start();
        client = DynamoDbClient.builder()
                .endpointOverride(URI.create("http://" + container.getHost() + ":" + container.getMappedPort(8000)))
                .region(Region.AP_NORTHEAST_2)
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("test", "test")))
                .build();
        createTableFromTheSharedSchema();
    }

    public static DynamoDbLocal withTable(String tableName) {
        return new DynamoDbLocal(tableName);
    }

    public String tableName() {
        return tableName;
    }

    public DynamoDbClient client() {
        return client;
    }

    @Override
    public void close() {
        client.close();
        container.stop();
    }

    private void createTableFromTheSharedSchema() {
        JsonNode schema = JsonMapper.builder().build().readTree(TABLE_SCHEMA.toFile());
        client.createTable(table -> table
                .tableName(tableName)
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
}
