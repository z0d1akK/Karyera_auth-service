package com.zodiakk.authservice.integration.employee.service.impl;

import com.zodiakk.authservice.integration.employee.client.EmployeeClient;
import com.zodiakk.authservice.integration.employee.dto.EmployeeAccountInfoDto;
import com.zodiakk.authservice.integration.employee.exception.EmployeeIntegrationException;
import com.zodiakk.authservice.integration.employee.exception.EmployeeNotFoundException;
import com.zodiakk.authservice.integration.employee.exception.EmployeeServiceUnavailableException;
import com.zodiakk.authservice.support.AuthTestDataFactory;
import feign.FeignException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmployeeIntegrationServiceImplTest {

    @Mock
    private EmployeeClient employeeClient;

    private EmployeeIntegrationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new EmployeeIntegrationServiceImpl(employeeClient);
    }

    @Nested
    @DisplayName("getAccountInfo")
    class GetAccountInfo {

        @Test
        @DisplayName("returns the employee account information")
        void shouldReturnAccountInfo() {
            EmployeeAccountInfoDto dto = AuthTestDataFactory.activeEmployee();
            when(employeeClient.getAccountInfo(AuthTestDataFactory.EMPLOYEE_ID)).thenReturn(dto);

            assertThat(service.getAccountInfo(AuthTestDataFactory.EMPLOYEE_ID)).isSameAs(dto);
        }

        @Test
        @DisplayName("translates a 404 response into EmployeeNotFoundException")
        void shouldTranslateNotFound() {
            when(employeeClient.getAccountInfo(AuthTestDataFactory.EMPLOYEE_ID))
                    .thenThrow(mock(FeignException.NotFound.class));

            assertThatThrownBy(() -> service.getAccountInfo(AuthTestDataFactory.EMPLOYEE_ID))
                    .isInstanceOf(EmployeeNotFoundException.class)
                    .hasMessageContaining(AuthTestDataFactory.EMPLOYEE_ID.toString());
        }

        @Test
        @DisplayName("propagates unexpected failures to the circuit breaker")
        void shouldPropagateOtherFailures() {
            FeignException serverError = mock(FeignException.InternalServerError.class);
            when(employeeClient.getAccountInfo(AuthTestDataFactory.EMPLOYEE_ID)).thenThrow(serverError);

            assertThatThrownBy(() -> service.getAccountInfo(AuthTestDataFactory.EMPLOYEE_ID))
                    .isSameAs(serverError);
        }
    }

    @Nested
    @DisplayName("getAccountInfoFallback")
    class GetAccountInfoFallback {

        @Test
        @DisplayName("rethrows the original not-found exception")
        void shouldRethrowNotFound() {
            EmployeeNotFoundException notFound =
                    new EmployeeNotFoundException(AuthTestDataFactory.EMPLOYEE_ID);

            assertThatThrownBy(() -> invokeFallback(notFound))
                    .isSameAs(notFound);
        }

        @Test
        @DisplayName("translates 4xx responses into EmployeeIntegrationException")
        void shouldTranslateClientError() {
            FeignException badRequest = mock(FeignException.BadRequest.class);
            when(badRequest.status()).thenReturn(400);

            assertThatThrownBy(() -> invokeFallback(badRequest))
                    .isInstanceOf(EmployeeIntegrationException.class)
                    .hasCause(badRequest);
        }

        @Test
        @DisplayName("translates 5xx responses into EmployeeServiceUnavailableException")
        void shouldTranslateServerError() {
            FeignException serverError = mock(FeignException.InternalServerError.class);
            when(serverError.status()).thenReturn(503);

            assertThatThrownBy(() -> invokeFallback(serverError))
                    .isInstanceOf(EmployeeServiceUnavailableException.class);
        }

        @Test
        @DisplayName("translates timeouts and other failures into EmployeeServiceUnavailableException")
        void shouldTranslateUnknownFailure() {
            assertThatThrownBy(() -> invokeFallback(new RuntimeException("timeout")))
                    .isInstanceOf(EmployeeServiceUnavailableException.class);
        }
    }

    private Object invokeFallback(Throwable exception) {
        return ReflectionTestUtils.invokeMethod(
                service,
                "getAccountInfoFallback",
                AuthTestDataFactory.EMPLOYEE_ID,
                exception
        );
    }
}
