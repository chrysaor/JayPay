package com.jaypay.common.logging;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.slf4j.LoggerFactory;

import java.util.List;

public final class LogRecorder implements AutoCloseable {

    private final Logger logger;
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    private LogRecorder(Class<?> source) {
        logger = (Logger) LoggerFactory.getLogger(source);
        appender.start();
        logger.addAppender(appender);
    }

    public static LogRecorder of(Class<?> source) {
        return new LogRecorder(source);
    }

    public List<String> lines() {
        return appender.list.stream()
                .map(event -> event.getLevel() + " " + event.getFormattedMessage()
                        + (event.getThrowableProxy() == null ? "" : " [" + event.getThrowableProxy().getClassName() + "]"))
                .toList();
    }

    @Override
    public void close() {
        logger.detachAppender(appender);
        appender.stop();
    }
}
