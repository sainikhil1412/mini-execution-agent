package com.agent.exception;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.context.request.WebRequest;

import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

@DisplayName("GlobalExceptionHandler Tests")
class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler exceptionHandler;
    private WebRequest webRequest;

    @BeforeEach
    void setUp() {
        exceptionHandler = new GlobalExceptionHandler();
        webRequest = mock(WebRequest.class);
    }

    @Test
    @DisplayName("Should handle AgentException")
    void shouldHandleAgentException() {
        // Given
        String errorCode = "VALIDATION_ERROR";
        String message = "Invalid plan data";
        AgentException agentException = new AgentException(errorCode, message);

        // When
        ResponseEntity<Map<String, Object>> response = 
                exceptionHandler.handleAgentException(agentException);

        // Then
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertEquals("ERROR", body.get("status"));
        assertEquals(errorCode, body.get("error_code"));
        assertEquals(message, body.get("message"));
        assertNotNull(body.get("timestamp"));
        assertTrue(body.get("timestamp").toString().contains(LocalDateTime.now().getYear() + ""));
    }

    @Test
    @DisplayName("Should handle generic Exception")
    void shouldHandleGenericException() {
        // Given
        String message = "Unexpected error occurred";
        RuntimeException genericException = new RuntimeException(message);

        // When
        ResponseEntity<Map<String, Object>> response = 
                exceptionHandler.handleGenericException(genericException);

        // Then
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertEquals("ERROR", body.get("status"));
        assertEquals("INTERNAL_ERROR", body.get("error_code"));
        assertEquals(message, body.get("message"));
        assertNotNull(body.get("timestamp"));
        assertTrue(body.get("timestamp").toString().contains(LocalDateTime.now().getYear() + ""));
    }

    @Test
    @DisplayName("Should handle null message in AgentException")
    void shouldHandleNullMessageInAgentException() {
        // Given
        AgentException agentException = new AgentException("TEST_ERROR", null);

        // When
        ResponseEntity<Map<String, Object>> response = 
                exceptionHandler.handleAgentException(agentException);

        // Then
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertEquals("ERROR", body.get("status"));
        assertEquals("TEST_ERROR", body.get("error_code"));
        assertNull(body.get("message"));
        assertNotNull(body.get("timestamp"));
    }

    @Test
    @DisplayName("Should handle null message in generic Exception")
    void shouldHandleNullMessageInGenericException() {
        // Given
        RuntimeException genericException = new RuntimeException((String) null);

        // When
        ResponseEntity<Map<String, Object>> response = 
                exceptionHandler.handleGenericException(genericException);

        // Then
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertEquals("ERROR", body.get("status"));
        assertEquals("INTERNAL_ERROR", body.get("error_code"));
        assertNull(body.get("message"));
        assertNotNull(body.get("timestamp"));
    }

    @Test
    @DisplayName("Should handle AgentException with nested cause")
    void shouldHandleAgentExceptionWithNestedCause() {
        // Given
        String causeMessage = "Root cause of the error";
        Exception cause = new IllegalArgumentException(causeMessage);
        AgentException agentException = new AgentException("VALIDATION_ERROR", "Validation failed", cause);

        // When
        ResponseEntity<Map<String, Object>> response = 
                exceptionHandler.handleAgentException(agentException);

        // Then
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertEquals("ERROR", body.get("status"));
        assertEquals("VALIDATION_ERROR", body.get("error_code"));
        assertEquals("Validation failed", body.get("message"));
        assertNotNull(body.get("timestamp"));
    }

    @Test
    @DisplayName("Should handle generic Exception with nested cause")
    void shouldHandleGenericExceptionWithNestedCause() {
        // Given
        String causeMessage = "Root cause of the error";
        Exception cause = new IllegalArgumentException(causeMessage);
        RuntimeException genericException = new RuntimeException("Generic error", cause);

        // When
        ResponseEntity<Map<String, Object>> response = 
                exceptionHandler.handleGenericException(genericException);

        // Then
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertEquals("ERROR", body.get("status"));
        assertEquals("INTERNAL_ERROR", body.get("error_code"));
        assertEquals("Generic error", body.get("message"));
        assertNotNull(body.get("timestamp"));
    }

    @Test
    @DisplayName("Should handle different types of AgentExceptions")
    void shouldHandleDifferentTypesOfAgentExceptions() {
        // Test various error codes
        String[] errorCodes = {
                "VALIDATION_ERROR",
                "PARSE_ERROR", 
                "CSV_ERROR",
                "AUDIT_ERROR",
                "EXECUTION_ERROR",
                "LLM_MOCK_ERROR"
        };

        for (String errorCode : errorCodes) {
            // Given
            AgentException agentException = new AgentException(errorCode, "Test message for " + errorCode);

            // When
            ResponseEntity<Map<String, Object>> response = 
                    exceptionHandler.handleAgentException(agentException);

            // Then
            assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
            
            Map<String, Object> body = response.getBody();
            assertNotNull(body);
            assertEquals("ERROR", body.get("status"));
            assertEquals(errorCode, body.get("error_code"));
            assertEquals("Test message for " + errorCode, body.get("message"));
            assertNotNull(body.get("timestamp"));
        }
    }

    @Test
    @DisplayName("Should ensure timestamp format consistency")
    void shouldEnsureTimestampFormatConsistency() throws InterruptedException {
        // Given
        AgentException agentException = new AgentException("TEST_ERROR", "Test message");

        // When - make two calls with slight delay to ensure different timestamps
        ResponseEntity<Map<String, Object>> response1 = 
                exceptionHandler.handleAgentException(agentException);
        
        Thread.sleep(1); // Small delay to ensure different timestamp
        
        ResponseEntity<Map<String, Object>> response2 = 
                exceptionHandler.handleAgentException(agentException);

        // Then
        Map<String, Object> body1 = response1.getBody();
        Map<String, Object> body2 = response2.getBody();
        
        assertNotNull(body1.get("timestamp"));
        assertNotNull(body2.get("timestamp"));
        
        // Timestamps should be different (or at least formatted consistently)
        String timestamp1 = body1.get("timestamp").toString();
        String timestamp2 = body2.get("timestamp").toString();
        
        // Both should be in ISO format
        assertTrue(timestamp1.matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}.*"));
        assertTrue(timestamp2.matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}.*"));
    }

    @Test
    @DisplayName("Should handle empty error code in AgentException")
    void shouldHandleEmptyErrorCodeInAgentException() {
        // Given
        AgentException agentException = new AgentException("", "Empty error code");

        // When
        ResponseEntity<Map<String, Object>> response = 
                exceptionHandler.handleAgentException(agentException);

        // Then
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertEquals("ERROR", body.get("status"));
        assertEquals("", body.get("error_code"));
        assertEquals("Empty error code", body.get("message"));
        assertNotNull(body.get("timestamp"));
    }

    @Test
    @DisplayName("Should handle very long error messages")
    void shouldHandleVeryLongErrorMessages() {
        // Given
        String longMessage = "A".repeat(10000); // Very long message
        AgentException agentException = new AgentException("LONG_MESSAGE_ERROR", longMessage);

        // When
        ResponseEntity<Map<String, Object>> response = 
                exceptionHandler.handleAgentException(agentException);

        // Then
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertEquals("ERROR", body.get("status"));
        assertEquals("LONG_MESSAGE_ERROR", body.get("error_code"));
        assertEquals(longMessage, body.get("message"));
        assertNotNull(body.get("timestamp"));
    }
}
