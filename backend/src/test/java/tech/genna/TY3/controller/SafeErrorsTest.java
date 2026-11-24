package tech.genna.TY3.controller;

import org.junit.jupiter.api.Test;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.slf4j.LoggerFactory;
import tech.genna.TY3.model.exception.*;
import static org.junit.jupiter.api.Assertions.*;

class SafeErrorsTest {
    @Test void privateExceptionDetailsStayOutOfResponseAndLogs() {
        String secret="private-token material-text selectedOptionIds SQL-values";
        var log=(Logger)LoggerFactory.getLogger(GlobalExceptionHandler.class);
        var capture=new ListAppender<ILoggingEvent>();capture.start();log.addAppender(capture);
        try {
            var handler=new GlobalExceptionHandler();
            assertFalse(handler.handleNotFound(new ResourceNotFoundException(secret)).getBody().toString().contains(secret));
            assertFalse(handler.handleFileStorage(new FileStorageException(secret,new RuntimeException(secret))).getBody().toString().contains(secret));
            assertFalse(handler.handleUnexpected(new RuntimeException(secret)).getBody().toString().contains(secret));
            assertFalse(capture.list.isEmpty());
            for(var event:capture.list) {
                assertFalse(event.getFormattedMessage().contains(secret));
                assertNull(event.getThrowableProxy());
            }
        } finally {log.detachAppender(capture);capture.stop();}
    }
}
