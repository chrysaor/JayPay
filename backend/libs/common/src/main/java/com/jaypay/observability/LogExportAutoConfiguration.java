package com.jaypay.observability;

import ch.qos.logback.classic.LoggerContext;
import io.opentelemetry.api.OpenTelemetry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;

@AutoConfiguration(afterName = "org.springframework.boot.opentelemetry.autoconfigure.OpenTelemetrySdkAutoConfiguration")
@ConditionalOnClass(LoggerContext.class)
@ConditionalOnBean(OpenTelemetry.class)
public class LogExportAutoConfiguration {

    @Bean
    LogbackOpenTelemetryBridge logbackOpenTelemetryBridge(OpenTelemetry openTelemetry) {
        return new LogbackOpenTelemetryBridge(openTelemetry);
    }
}
