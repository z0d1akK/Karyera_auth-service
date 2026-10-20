package com.zodiakk.authservice.integration.employee.client;

import com.zodiakk.authservice.integration.employee.config.EmployeeFeignConfig;
import com.zodiakk.authservice.integration.employee.dto.EmployeeAccountInfoDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.UUID;

@FeignClient(
        name = "employee-service",
        url = "${integration.employee-service.url}",
        configuration = EmployeeFeignConfig.class
)
public interface EmployeeClient {

    @GetMapping("/internal/employees/{id}/account-info")
    EmployeeAccountInfoDto getAccountInfo(@PathVariable("id") UUID id);
}