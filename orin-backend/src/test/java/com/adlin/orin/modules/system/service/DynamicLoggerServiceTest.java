package com.adlin.orin.modules.system.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.logging.LogLevel;
import org.springframework.boot.logging.LoggingSystem;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * DynamicLoggerService 单元测试
 */
@ExtendWith(MockitoExtension.class)
class DynamicLoggerServiceTest {

    @Mock
    private LoggingSystem loggingSystem;

    @InjectMocks
    private DynamicLoggerService dynamicLoggerService;

    @Test
    void testSetLogLevel_ValidLevel() {
        // Given
        String loggerName = "com.adlin.orin";
        String level = "INFO";

        // When
        dynamicLoggerService.setLogLevel(loggerName, level);

        // Then
        verify(loggingSystem).setLogLevel(loggerName, LogLevel.INFO);
    }

    @Test
    void testSetLogLevel_InvalidLevel() {
        // Given
        String loggerName = "com.adlin.orin";
        String invalidLevel = "INVALID";

        // When & Then
        assertThrows(IllegalArgumentException.class, () -> {
            dynamicLoggerService.setLogLevel(loggerName, invalidLevel);
        });
    }

    @Test
    void testSetLogLevel_NullLevel() {
        // Given
        String loggerName = "com.adlin.orin";
        String level = "NULL";

        // When
        dynamicLoggerService.setLogLevel(loggerName, level);

        // Then
        verify(loggingSystem).setLogLevel(loggerName, null);
    }

    @Test
    void testResetLogger() {
        // Given
        String loggerName = "com.adlin.orin";

        // When
        dynamicLoggerService.resetLogger(loggerName);

        // Then
        verify(loggingSystem).setLogLevel(loggerName, null);
    }

    @Test
    void testBatchSetLogLevel() {
        // Given
        Map<String, String> loggerLevels = Map.of(
                "com.adlin.orin", "DEBUG",
                "org.springframework", "WARN");

        // When
        dynamicLoggerService.batchSetLogLevel(loggerLevels);

        // Then
        verify(loggingSystem).setLogLevel("com.adlin.orin", LogLevel.DEBUG);
        verify(loggingSystem).setLogLevel("org.springframework", LogLevel.WARN);
    }

    @Test
    void testGetSupportedLevels() {
        // When
        List<String> levels = dynamicLoggerService.getSupportedLevels();

        // Then
        assertNotNull(levels);
        assertTrue(levels.contains("TRACE"));
        assertTrue(levels.contains("DEBUG"));
        assertTrue(levels.contains("INFO"));
        assertTrue(levels.contains("WARN"));
        assertTrue(levels.contains("ERROR"));
        assertTrue(levels.contains("OFF"));
    }

    @Test
    void testResetAllLoggers() {
        // When
        dynamicLoggerService.resetAllLoggers();

        // Then
        verify(loggingSystem, atLeastOnce()).setLogLevel(anyString(), isNull());
    }
}
