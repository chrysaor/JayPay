package com.jaypay.money.query.adapter.out.aws.dynamodb;

import com.jaypay.money.query.adapter.axon.QueryMoneySumByAddress;
import com.jaypay.money.query.application.port.out.GetMoneySumByAddressPort;
import com.jaypay.money.query.application.port.out.InsertMoneyIncreaseEventByAddress;
import com.jaypay.money.query.domain.MoneySumByRegion;
import org.axonframework.queryhandling.QueryHandler;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Component
public class DynamoDBAdapter implements GetMoneySumByAddressPort, InsertMoneyIncreaseEventByAddress {
    private static final String TABLE_NAME = "MoneyIncreaseEventByRegion";
    private static final String ACCESS_KEY = "AKIAVR3JWGYJBUPTCAZG";
    private static final String SECRET_KEY = "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";
    private final DynamoDbClient dynamoDbClient;
    private final MoneySumByAddressMapper moneySumByAddressMapper;

    public DynamoDBAdapter() {
        this.moneySumByAddressMapper = new MoneySumByAddressMapper();
        AwsBasicCredentials credentials = AwsBasicCredentials.create(ACCESS_KEY, SECRET_KEY);
        this.dynamoDbClient = DynamoDbClient.builder()
                .region(Region.AP_NORTHEAST_2)
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                .build();
    }

    @Override
    public int getMoneySumByAddressPort(String addressName) {
        return getItem(addressName, "-1").getBalance();
    }


    private void putItem(String pk, String sk, int balance) {
        try {
            HashMap<String, AttributeValue> attrMap = new HashMap<>();
            attrMap.put("PK", AttributeValue.builder().s(pk).build());
            attrMap.put("SK", AttributeValue.builder().s(sk).build());
            attrMap.put("balance", AttributeValue.builder().n(String.valueOf(balance)).build());

            PutItemRequest request = PutItemRequest.builder()
                    .tableName(TABLE_NAME)
                    .item(attrMap)
                    .build();

            dynamoDbClient.putItem(request);
        } catch (DynamoDbException e) {
            System.err.println("Error adding an item to the table: " + e.getMessage());
        }
    }

    private MoneySumByAddress getItem(String pk, String sk) {
        try {
            HashMap<String, AttributeValue> attrMap = new HashMap<>();
            attrMap.put("PK", AttributeValue.builder().s(pk).build());
            attrMap.put("SK", AttributeValue.builder().s(sk).build());

            GetItemRequest request = GetItemRequest.builder()
                    .tableName(TABLE_NAME)
                    .key(attrMap)
                    .build();

            GetItemResponse response = dynamoDbClient.getItem(request);
            if (response.hasItem()) {
                response.item().forEach((key, value) -> System.out.println(key + ": " + value));
                return moneySumByAddressMapper.mapToMoneySumByAddress(response.item());
            }
        } catch (DynamoDbException e) {
            System.err.println("Error getting an item from the table: " + e.getMessage());
        }
        return new MoneySumByAddress("", "", 0);
    }

    private void queryItem(String id) {
        try {
            // PK 만 써도 돼요.
            HashMap<String, Condition> attrMap = new HashMap<>();
            attrMap.put("PK", Condition.builder()
                    .attributeValueList(AttributeValue.builder().s(id).build())
                    .comparisonOperator(ComparisonOperator.EQ)
                    .build());

            QueryRequest request = QueryRequest.builder()
                    .tableName(TABLE_NAME)
                    .keyConditions(attrMap)
                    .build();

            QueryResponse response = dynamoDbClient.query(request);
            response.items().forEach(System.out::println);
        } catch (DynamoDbException e) {
            System.err.println("Error getting an item from the table: " + e.getMessage());
        }
    }

    @Override
    public void insertMoneyIncreaseEventByAddress(String addressName, int moneyIncrease) {
        // Date info
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd");
        String dateTime = now.format(formatter);

        // 1. insert raw event
        // Local/Event - PK: 강남구#230728, SK: 5000
        String pk = addressName + "#" + dateTime;
        String sk = String.valueOf(moneyIncrease);
        putItem(pk, sk, moneyIncrease);

        // 2. query and update with two local events
        // Local/Daily - PK: 강남구#230728#summary, SK: -1, balance: +5000
        String summaryPk = pk + "#summary";
        String summarySk = "-1";
        MoneySumByAddress moneySumByAddress = getItem(summaryPk, summarySk);
        if (moneySumByAddress == null) {
            putItem(summaryPk, summarySk, moneyIncrease);
        } else {
            int balance = moneySumByAddress.getBalance();
            balance += moneyIncrease;
            updateItem(summaryPk, summarySk, balance);
        }

        // Local - PK: 강남구, balance: +5000
        String summarySk2 = "-1";
        MoneySumByAddress moneySumByAddress2 = getItem(addressName, summarySk2);
        if (moneySumByAddress2 == null) {
            putItem(addressName, summarySk2, moneyIncrease);
        } else {
            int balance2 = moneySumByAddress2.getBalance();
            balance2 += moneyIncrease;
            updateItem(addressName, summarySk2, balance2);
        }
    }

    private void updateItem(String pk, String sk, int balance) {
        try {
            HashMap<String, AttributeValue> attrMap = new HashMap<>();
            attrMap.put("PK", AttributeValue.builder().s(pk).build());
            attrMap.put("SK", AttributeValue.builder().s(sk).build());

            String balanceStr = String.valueOf(balance);
            // Create an UpdateItemRequest
            UpdateItemRequest updateItemRequest = UpdateItemRequest.builder()
                    .tableName(TABLE_NAME)
                    .key(attrMap)
                    .attributeUpdates(
                            new HashMap<>() {{
                                put("balance", AttributeValueUpdate.builder()
                                        .value(AttributeValue.builder().n(balanceStr).build())
                                        .action(AttributeAction.PUT)
                                        .build());
                            }}
                    ).build();

            UpdateItemResponse response = dynamoDbClient.updateItem(updateItemRequest);

            // 결과 출력.
            Map<String, AttributeValue> attributes = response.attributes();
            if (attributes != null) {
                for (Map.Entry<String, AttributeValue> entry : attributes.entrySet()) {
                    String attributeName = entry.getKey();
                    AttributeValue attributeValue = entry.getValue();
                    System.out.println(attributeName + ": " + attributeValue);
                }
            } else {
                System.out.println("Item was updated, but no attributes were returned.");
            }
        } catch (DynamoDbException e) {
            System.err.println("Error getting an item from the table: " + e.getMessage());
        }
    }

    @QueryHandler
    public MoneySumByRegion query (QueryMoneySumByAddress query){
        return MoneySumByRegion.generateMoneySumByRegion(
                new MoneySumByRegion.MoneySumByRegionId(UUID.randomUUID().toString()),
                new MoneySumByRegion.RegionName(query.getAddress()),
                new MoneySumByRegion.MoneySum(this.getMoneySumByAddressPort(query.getAddress()))
        );
    }

}
