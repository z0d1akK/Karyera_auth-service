package com.zodiakk.authservice.integration.employee.service.impl;

import com.zodiakk.authservice.common.exception.ErrorMessages;
import com.zodiakk.authservice.integration.employee.client.EmployeeClient;
import com.zodiakk.authservice.integration.employee.dto.EmployeeAccountInfoDto;
import com.zodiakk.authservice.integration.employee.exception.EmployeeIntegrationException;
import com.zodiakk.authservice.integration.employee.exception.EmployeeNotFoundException;
import com.zodiakk.authservice.integration.employee.exception.EmployeeServiceUnavailableException;
import com.zodiakk.authservice.integration.employee.service.EmployeeIntegrationService;
import feign.FeignException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EmployeeIntegrationServiceImpl implements EmployeeIntegrationService {

    private final EmployeeClient employeeClient;

    @Override
    @CircuitBreaker(
            name = "employeeService",
            fallbackMethod = "getAccountInfoFallback"
    )
    public EmployeeAccountInfoDto getAccountInfo(UUID employeeId) {
        try {
            return employeeClient.getAccountInfo(employeeId);
        } catch (FeignException.NotFound ex) {
            throw new EmployeeNotFoundException(employeeId);
        }
    }

    private EmployeeAccountInfoDto getAccountInfoFallback(
            UUID employeeId,
            Throwable exception
    ) {
        if (exception instanceof EmployeeNotFoundException notFound) {
            throw notFound;
        }

        if (exception instanceof FeignException feignException
                && feignException.status() >= 400
                && feignException.status() < 500) {
            throw new EmployeeIntegrationException(
                    ErrorMessages.EMPLOYEE_SERVICE_REJECTED_REQUEST,
                    feignException
            );
        }

        throw new EmployeeServiceUnavailableException();
    }
}