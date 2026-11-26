package com.jaypay.persistence;

import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.orm.jpa.EntityManagerFactoryInfo;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

class EnumValueChecks implements BeanPostProcessor {

    private static final String CHECKS_ON_ONE_COLUMN = """
            select c.conname from pg_constraint c
            join pg_attribute a on a.attrelid = c.conrelid and a.attnum = c.conkey[1]
            where c.contype = 'c' and cardinality(c.conkey) = 1
            and c.conrelid = to_regclass(?) and a.attname = ?""";

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        if (bean instanceof EntityManagerFactoryInfo factory) {
            drop(factory.getNativeEntityManagerFactory().unwrap(SessionFactoryImplementor.class));
        }
        return bean;
    }

    private static void drop(SessionFactoryImplementor sessionFactory) {
        Set<EnumColumn> columns = new LinkedHashSet<>();
        sessionFactory.getMappingMetamodel().forEachEntityDescriptor(entity -> entity.forEachSelectable((index, column) -> {
            if (!column.isFormula() && column.getJdbcMapping().getJavaTypeDescriptor().getJavaTypeClass().isEnum()) {
                columns.add(new EnumColumn(column.getContainingTableExpression(), column.getSelectionExpression()));
            }
        }));
        sessionFactory.inTransaction(session -> session.doWork(connection -> {
            for (EnumColumn column : columns) {
                for (String check : checksOn(connection, column)) {
                    try (Statement statement = connection.createStatement()) {
                        statement.execute("alter table " + column.table() + " drop constraint if exists \"" + check.replace("\"", "\"\"") + "\"");
                    }
                }
            }
        }));
    }

    private static List<String> checksOn(Connection connection, EnumColumn column) throws SQLException {
        try (PreparedStatement query = connection.prepareStatement(CHECKS_ON_ONE_COLUMN)) {
            query.setString(1, column.table());
            query.setString(2, column.name());
            List<String> checks = new ArrayList<>();
            try (ResultSet rows = query.executeQuery()) {
                while (rows.next()) {
                    checks.add(rows.getString(1));
                }
            }
            return checks;
        }
    }

    private record EnumColumn(String table, String name) {
    }
}
