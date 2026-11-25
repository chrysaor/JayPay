package com.jaypay.money.query.adapter.out.aws.dynamodb;

import lombok.extern.slf4j.Slf4j;
import com.jaypay.money.query.application.port.out.GetMoneySumByAddressPort;
import com.jaypay.money.query.application.port.out.InsertMoneyIncreaseEventByAddress;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

@Component
@Slf4j
public class DynamoDBAdapter implements GetMoneySumByAddressPort, InsertMoneyIncreaseEventByAddress {
    private final DynamoDbClient dynamoDbClient;
    private final String tableName;
    private final MoneySumByAddressMapper moneySumByAddressMapper = new MoneySumByAddressMapper();

    public DynamoDBAdapter(DynamoDbClient dynamoDbClient, @Value("${dynamodb.table-name}") String tableName) {
        this.dynamoDbClient = dynamoDbClient;
        this.tableName = tableName;
    }

    @Override
    public int getMoneySumByAddressPort(String addressName) {
        return getItem(addressName, "-1").getBalance();
    }


    private void putItem(String pk, String sk, int balance) {
        HashMap<String, AttributeValue> attrMap = new HashMap<>();
        attrMap.put("PK", AttributeValue.builder().s(pk).build());
        attrMap.put("SK", AttributeValue.builder().s(sk).build());
        attrMap.put("balance", AttributeValue.builder().n(String.valueOf(balance)).build());

        PutItemRequest request = PutItemRequest.builder()
                .tableName(tableName)
                .item(attrMap)
                .build();

        dynamoDbClient.putItem(request);
    }

    private MoneySumByAddress getItem(String pk, String sk) {
        HashMap<String, AttributeValue> attrMap = new HashMap<>();
        attrMap.put("PK", AttributeValue.builder().s(pk).build());
        attrMap.put("SK", AttributeValue.builder().s(sk).build());

        GetItemRequest request = GetItemRequest.builder()
                .tableName(tableName)
                .key(attrMap)
                .build();

        GetItemResponse response = dynamoDbClient.getItem(request);
        if (response.hasItem()) {
            response.item().forEach((key, value) -> log.info("{}: {}", key, value));
            return moneySumByAddressMapper.mapToMoneySumByAddress(response.item());
        }
        return new MoneySumByAddress("", "", 0);
    }

    @Override
    public void insertMoneyIncreaseEventByAddress(String addressName, int moneyIncrease) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd");
        String dateTime = now.format(formatter);

        String pk = addressName + "#" + dateTime;
        String sk = String.valueOf(moneyIncrease);
        putItem(pk, sk, moneyIncrease);

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
        HashMap<String, AttributeValue> attrMap = new HashMap<>();
        attrMap.put("PK", AttributeValue.builder().s(pk).build());
        attrMap.put("SK", AttributeValue.builder().s(sk).build());

        String balanceStr = String.valueOf(balance);
        UpdateItemRequest updateItemRequest = UpdateItemRequest.builder()
                .tableName(tableName)
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

        Map<String, AttributeValue> attributes = response.attributes();
        if (attributes != null) {
            for (Map.Entry<String, AttributeValue> entry : attributes.entrySet()) {
                String attributeName = entry.getKey();
                AttributeValue attributeValue = entry.getValue();
                log.info("{}: {}", attributeName, attributeValue);
            }
        } else {
            log.info("Item was updated, but no attributes were returned.");
        }
    }

}
