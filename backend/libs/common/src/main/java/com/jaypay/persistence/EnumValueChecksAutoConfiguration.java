package com.jaypay.persistence;

import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.orm.jpa.EntityManagerFactoryInfo;

@AutoConfiguration
@ConditionalOnClass({EntityManagerFactoryInfo.class, SessionFactoryImplementor.class})
public class EnumValueChecksAutoConfiguration {

    @Bean
    static EnumValueChecks enumValueChecks() {
        return new EnumValueChecks();
    }
}
