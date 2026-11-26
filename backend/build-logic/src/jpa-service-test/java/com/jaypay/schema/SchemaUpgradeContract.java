package com.jaypay.schema;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:tc:postgresql:19.0-alpine:///jaypay?TC_INITSCRIPT=schema-0.4.0.sql",
        "spring.jpa.hibernate.ddl-auto=update"})
@ActiveProfiles("test")
public abstract class SchemaUpgradeContract {

    private static final String VALUE_ADDED_LATER = "ADDED_IN_A_LATER_VERSION";

    @Autowired
    private JdbcTemplate database;

    protected abstract List<String> enumColumns();

    protected abstract List<String> checksThatStay();

    @Test
    void aDatabaseThatTheLastReleaseCreatedTakesAValueAddedLater() {
        for (String tableAndColumn : enumColumns()) {
            String[] parts = tableAndColumn.split("\\.");
            int changed = database.update("update " + parts[0] + " set " + parts[1] + " = ?", VALUE_ADDED_LATER);

            assertThat(changed).as(tableAndColumn).isPositive();
        }
    }

    @Test
    void keepsTheChecksThatAreNotAboutEnumValues() {
        List<String> checks = database.queryForList("select conname from pg_constraint where contype = 'c'", String.class);

        assertThat(checks).containsAll(checksThatStay());
    }
}
