package com.jaypay.eventflow;

import com.jaypay.common.kafka.KafkaTestBroker;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class FlowSchemaTest {

    @Autowired
    private JdbcTemplate database;

    @DynamicPropertySource
    static void kafka(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", KafkaTestBroker::bootstrapServers);
    }

    @Test
    void aDatabaseCreatedTodayAcceptsTheValuesOfALaterVersion() {
        List<String> checks = database.queryForList("""
                select pg_get_constraintdef(oid) from pg_constraint
                where contype = 'c' and conrelid::regclass::text in ('flow', 'flow_step')""", String.class);

        assertThat(checks).isEmpty();
    }
}
