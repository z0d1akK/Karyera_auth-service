package com.zodiakk.authservice.integration.employee.config;

import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;

public class EmployeeFeignConfig {

    @Bean
    public RequestInterceptor employeeInternalKeyInterceptor(@Value("${integration.employee-service.internal-key}") String internalKey) {
        return template -> template.header("X-Internal-Key", internalKey);
    }
}
