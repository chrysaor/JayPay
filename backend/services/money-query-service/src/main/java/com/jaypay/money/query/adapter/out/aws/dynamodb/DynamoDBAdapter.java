package com.jaypay.money.query.adapter.out.aws.dynamodb;

import com.jaypay.money.query.application.port.out.GetMoneySumByAddressPort;
import com.jaypay.money.query.application.port.out.InsertMoneyIncreaseEventByAddress;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItem;
import software.amazon.awssdk.services.dynamodb.model.TransactionCanceledException;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Map;

@Component
@Slf4j
public class DynamoDBAdapter implements GetMoneySumByAddressPort, InsertMoneyIncreaseEventByAddress {

    private static final String TOTAL_SK = "-1";
    private static final String RECHARGE_SK = "recharge";
    private static final String CONDITIONAL_CHECK_FAILED = "ConditionalCheckFailed";
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final DynamoDbClient dynamoDbClient;
    private final String tableName;
    private final MoneySumByAddressMapper moneySumByAddressMapper = new MoneySumByAddressMapper();

    public DynamoDBAdapter(DynamoDbClient dynamoDbClient, @Value("${dynamodb.table-name}") String tableName) {
        this.dynamoDbClient = dynamoDbClient;
        this.tableName = tableName;
    }

    @Override
    public int getMoneySumByAddressPort(String addressName) {
        GetItemResponse response = dynamoDbClient.getItem(request -> request.tableName(tableName).key(key(addressName, TOTAL_SK)));
        return response.hasItem() ? moneySumByAddressMapper.mapToMoneySumByAddress(response.item()).getBalance() : 0;
    }

    @Override
    public void insertMoneyIncreaseEventByAddress(String rechargingRequestId, String addressName, long moneyIncrease) {
        String day = OffsetDateTime.now(ZoneOffset.UTC).format(DAY);
        AttributeValue amount = AttributeValue.fromN(String.valueOf(moneyIncrease));
        try {
            dynamoDbClient.transactWriteItems(request -> request.transactItems(
                    recordOnce(rechargingRequestId, addressName, day, amount),
                    add(addressName + "#" + day + "#summary", amount),
                    add(addressName, amount)));
        } catch (TransactionCanceledException e) {
            if (!alreadyCounted(e)) {
                throw e;
            }
            log.info("Recharge {} is already in the region total, so it is not added again", rechargingRequestId);
        }
    }

    private TransactWriteItem recordOnce(String rechargingRequestId, String addressName, String day, AttributeValue amount) {
        return TransactWriteItem.builder().put(put -> put
                .tableName(tableName)
                .item(Map.of(
                        "PK", AttributeValue.fromS("recharge#" + rechargingRequestId),
                        "SK", AttributeValue.fromS(RECHARGE_SK),
                        "address", AttributeValue.fromS(addressName),
                        "day", AttributeValue.fromS(day),
                        "balance", amount))
                .conditionExpression("attribute_not_exists(PK)")).build();
    }

    private TransactWriteItem add(String pk, AttributeValue amount) {
        return TransactWriteItem.builder().update(update -> update
                .tableName(tableName)
                .key(key(pk, TOTAL_SK))
                .updateExpression("ADD balance :amount")
                .expressionAttributeValues(Map.of(":amount", amount))).build();
    }

    private static boolean alreadyCounted(TransactionCanceledException e) {
        return e.hasCancellationReasons() && CONDITIONAL_CHECK_FAILED.equals(e.cancellationReasons().getFirst().code());
    }

    private static Map<String, AttributeValue> key(String pk, String sk) {
        return Map.of("PK", AttributeValue.fromS(pk), "SK", AttributeValue.fromS(sk));
    }
}
