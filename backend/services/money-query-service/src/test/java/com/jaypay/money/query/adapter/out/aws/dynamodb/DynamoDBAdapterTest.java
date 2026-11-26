package com.jaypay.money.query.adapter.out.aws.dynamodb;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DynamoDBAdapterTest {

    private static final DynamoDbLocal DYNAMODB = DynamoDbLocal.withTable("money-sum-adapter-test");

    @AfterAll
    static void stop() {
        DYNAMODB.close();
    }

    @Test
    void addsEveryIncreaseToTheTotalOfItsAddressInTheConfiguredTable() {
        DynamoDBAdapter adapter = new DynamoDBAdapter(DYNAMODB.client(), DYNAMODB.tableName());

        adapter.insertMoneyIncreaseEventByAddress("Seocho", 3000);
        adapter.insertMoneyIncreaseEventByAddress("Seocho", 2000);
        adapter.insertMoneyIncreaseEventByAddress("Mapo", 700);

        assertThat(adapter.getMoneySumByAddressPort("Seocho")).isEqualTo(5000);
        assertThat(adapter.getMoneySumByAddressPort("Mapo")).isEqualTo(700);
        assertThat(adapter.getMoneySumByAddressPort("Jongno")).isZero();
    }

    @Test
    void reportsAFailingTableInsteadOfAnsweringZero() {
        DynamoDBAdapter adapter = new DynamoDBAdapter(DYNAMODB.client(), "no-such-table");

        assertThatThrownBy(() -> adapter.getMoneySumByAddressPort("Seocho")).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> adapter.insertMoneyIncreaseEventByAddress("Seocho", 3000)).isInstanceOf(ResourceNotFoundException.class);
    }
}
