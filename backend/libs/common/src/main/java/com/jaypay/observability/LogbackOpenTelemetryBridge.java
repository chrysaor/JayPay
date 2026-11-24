package com.jaypay.observability;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.SmartInitializingSingleton;

public class LogbackOpenTelemetryBridge implements SmartInitializingSingleton, DisposableBean {

    private static final String APPENDER_NAME = "OTEL";

    private final OpenTelemetry openTelemetry;
    private OpenTelemetryAppender appender;

    public LogbackOpenTelemetryBridge(OpenTelemetry openTelemetry) {
        this.openTelemetry = openTelemetry;
    }

    @Override
    public void afterSingletonsInstantiated() {
        if (!(LoggerFactory.getILoggerFactory() instanceof LoggerContext context)) {
            return;
        }
        Logger root = context.getLogger(Logger.ROOT_LOGGER_NAME);
        stop(root.getAppender(APPENDER_NAME), root);

        appender = new OpenTelemetryAppender();
        appender.setName(APPENDER_NAME);
        appender.setContext(context);
        appender.setOpenTelemetry(openTelemetry);
        appender.setCaptureExperimentalAttributes(true);
        appender.start();
        root.addAppender(appender);
    }

    @Override
    public void destroy() {
        if (appender != null && LoggerFactory.getILoggerFactory() instanceof LoggerContext context) {
            Logger root = context.getLogger(Logger.ROOT_LOGGER_NAME);
            if (root.getAppender(APPENDER_NAME) == appender) {
                stop(appender, root);
            }
        }
    }

    private static void stop(Appender<ILoggingEvent> current, Logger root) {
        if (current != null) {
            root.detachAppender(current);
            current.stop();
        }
    }
}
