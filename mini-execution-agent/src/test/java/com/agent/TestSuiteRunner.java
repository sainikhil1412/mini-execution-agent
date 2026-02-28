package com.agent;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Test suite runner for all tests.
 * This class ensures that the Spring context loads correctly for testing.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@DisplayName("Complete Test Suite")
class TestSuiteRunner {

    @Test
    @DisplayName("Should load complete application context for testing")
    void contextLoads() {
        // This test ensures that the entire Spring application context loads successfully
        // including all controllers, services, and components needed for testing
    }
}
