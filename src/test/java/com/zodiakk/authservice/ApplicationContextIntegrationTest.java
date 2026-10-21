package com.zodiakk.authservice;

import com.zodiakk.authservice.auth.service.AuthService;
import com.zodiakk.authservice.common.AbstractIntegrationTest;
import com.zodiakk.authservice.integration.employee.service.EmployeeIntegrationService;
import com.zodiakk.authservice.user.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Application context")
class ApplicationContextIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private AuthService authService;

    @Autowired
    private UserService userService;

    @Autowired
    private EmployeeIntegrationService employeeIntegrationService;

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Test
    @DisplayName("loads the full application context with test infrastructure")
    void shouldLoadContext() {
        assertThat(authService).isNotNull();
        assertThat(userService).isNotNull();
        assertThat(employeeIntegrationService).isNotNull();
        assertThat(kafkaTemplate).isNotNull();
        assertThat(mockMvc).isNotNull();
        assertThat(jwtService).isNotNull();
    }
}
