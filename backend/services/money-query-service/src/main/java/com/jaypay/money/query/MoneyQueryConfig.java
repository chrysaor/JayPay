package com.jaypay.money.query;


import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

@Configuration
@ComponentScan("com.jaypay.common")
public class MoneyQueryConfig {

    @Bean
    DynamoDbClient dynamoDbClient(@Value("${dynamodb.region}") String region) {
        return DynamoDbClient.builder().region(Region.of(region)).build();
    }
}
