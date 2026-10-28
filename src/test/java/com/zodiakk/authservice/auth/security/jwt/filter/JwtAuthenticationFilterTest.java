package com.zodiakk.authservice.auth.security.jwt.filter;

import com.zodiakk.authservice.auth.entity.Role;
import com.zodiakk.authservice.auth.exception.ExpiredJwtTokenException;
import com.zodiakk.authservice.auth.exception.InvalidJwtTokenException;
import com.zodiakk.authservice.auth.security.jwt.JwtTokenClaims;
import com.zodiakk.authservice.auth.security.jwt.JwtTokenType;
import com.zodiakk.authservice.auth.security.jwt.principal.AuthenticatedUser;
import com.zodiakk.authservice.auth.security.jwt.service.JwtService;
import com.zodiakk.authservice.support.AuthTestDataFactory;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.ModelAndView;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    private JwtService jwtService;

    @Mock
    private HandlerExceptionResolver handlerExceptionResolver;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("passes the request through when no Authorization header is present")
    void shouldPassThroughWithoutHeader() throws Exception {
        JwtAuthenticationFilter filter = filter();
        MockHttpServletRequest request = request("/api/v1/users");
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(chain.getRequest()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("authenticates a request with a valid access token")
    void shouldAuthenticateValidToken() throws Exception {
        JwtTokenClaims claims = AuthTestDataFactory.claims();
        when(jwtService.parseToken(AuthTestDataFactory.ACCESS_TOKEN, JwtTokenType.ACCESS)).thenReturn(claims);

        JwtAuthenticationFilter filter = filter();
        MockHttpServletRequest request = request("/api/v1/users");
        request.addHeader("Authorization", "Bearer " + AuthTestDataFactory.ACCESS_TOKEN);
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(chain.getRequest()).isNotNull();

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNotNull();
        assertThat(authentication.getPrincipal()).isInstanceOf(AuthenticatedUser.class);

        AuthenticatedUser principal = (AuthenticatedUser) authentication.getPrincipal();
        assertThat(principal.getUserId()).isEqualTo(AuthTestDataFactory.USER_ID);
        assertThat(principal.getEmail()).isEqualTo(AuthTestDataFactory.EMAIL);
        assertThat(principal.getRole()).isEqualTo(Role.EMPLOYEE);
        assertThat(authentication.getAuthorities())
                .extracting(Object::toString)
                .containsExactly("ROLE_EMPLOYEE");
    }

    @Test
    @DisplayName("delegates non-bearer Authorization headers to the exception resolver")
    void shouldRejectNonBearerHeader() throws Exception {
        when(handlerExceptionResolver.resolveException(any(), any(), any(), any(InvalidJwtTokenException.class)))
                .thenReturn(new ModelAndView());

        JwtAuthenticationFilter filter = filter();
        MockHttpServletRequest request = request("/api/v1/users");
        request.addHeader("Authorization", "Basic dXNlcjpwYXNz");
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(chain.getRequest()).isNull();
        verify(jwtService, never()).parseToken(any(), any());
    }

    @Test
    @DisplayName("delegates an invalid token to the exception resolver")
    void shouldResolveInvalidToken() throws Exception {
        when(jwtService.parseToken(AuthTestDataFactory.ACCESS_TOKEN, JwtTokenType.ACCESS))
                .thenThrow(new InvalidJwtTokenException());
        when(handlerExceptionResolver.resolveException(any(), any(), any(), any(InvalidJwtTokenException.class)))
                .thenReturn(new ModelAndView());

        JwtAuthenticationFilter filter = filter();
        MockHttpServletRequest request = request("/api/v1/users");
        request.addHeader("Authorization", "Bearer " + AuthTestDataFactory.ACCESS_TOKEN);
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(chain.getRequest()).isNull();
        verify(handlerExceptionResolver).resolveException(
                any(), any(), isNull(), any(InvalidJwtTokenException.class)
        );
    }

    @Test
    @DisplayName("delegates an expired token to the exception resolver")
    void shouldResolveExpiredToken() throws Exception {
        when(jwtService.parseToken(AuthTestDataFactory.ACCESS_TOKEN, JwtTokenType.ACCESS))
                .thenThrow(new ExpiredJwtTokenException());
        when(handlerExceptionResolver.resolveException(any(), any(), any(), any(ExpiredJwtTokenException.class)))
                .thenReturn(new ModelAndView());

        JwtAuthenticationFilter filter = filter();
        MockHttpServletRequest request = request("/api/v1/users");
        request.addHeader("Authorization", "Bearer " + AuthTestDataFactory.ACCESS_TOKEN);
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(chain.getRequest()).isNull();
    }

    @Test
    @DisplayName("fails when the exception resolver does not handle the exception")
    void shouldFailWhenResolverReturnsNull() {
        when(jwtService.parseToken(AuthTestDataFactory.ACCESS_TOKEN, JwtTokenType.ACCESS))
                .thenThrow(new InvalidJwtTokenException());
        when(handlerExceptionResolver.resolveException(any(), any(), any(), any(InvalidJwtTokenException.class)))
                .thenReturn(null);

        JwtAuthenticationFilter filter = filter();
        MockHttpServletRequest request = request("/api/v1/users");
        request.addHeader("Authorization", "Bearer " + AuthTestDataFactory.ACCESS_TOKEN);

        assertThatThrownBy(() -> filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain()))
                .isInstanceOf(ServletException.class);
    }

    @Test
    @DisplayName("does not filter public endpoints and OPTIONS requests")
    void shouldNotFilterPublicEndpoints() {
        JwtAuthenticationFilter filter = filter();

        assertThat(filter.shouldNotFilter(request("OPTIONS", "/api/v1/users"))).isTrue();
        assertThat(filter.shouldNotFilter(request("POST", "/api/v1/auth/login"))).isTrue();
        assertThat(filter.shouldNotFilter(request("POST", "/api/v1/auth/refresh"))).isTrue();
        assertThat(filter.shouldNotFilter(request("POST", "/api/v1/auth/change-initial-password"))).isTrue();
        assertThat(filter.shouldNotFilter(request("GET", "/actuator/health"))).isTrue();
        assertThat(filter.shouldNotFilter(request("GET", "/actuator/health/liveness"))).isTrue();
        assertThat(filter.shouldNotFilter(request("GET", "/actuator/info"))).isTrue();
        assertThat(filter.shouldNotFilter(request("GET", "/api-docs"))).isTrue();
        assertThat(filter.shouldNotFilter(request("GET", "/api-docs/swagger-config"))).isTrue();
        assertThat(filter.shouldNotFilter(request("GET", "/v3/api-docs"))).isTrue();
        assertThat(filter.shouldNotFilter(request("GET", "/v3/api-docs/swagger-config"))).isTrue();
        assertThat(filter.shouldNotFilter(request("GET", "/swagger-ui"))).isTrue();
        assertThat(filter.shouldNotFilter(request("GET", "/swagger-ui/index.html"))).isTrue();
        assertThat(filter.shouldNotFilter(request("GET", "/swagger-ui.html"))).isTrue();
    }

    @Test
    @DisplayName("filters protected endpoints")
    void shouldFilterProtectedEndpoints() {
        JwtAuthenticationFilter filter = filter();

        assertThat(filter.shouldNotFilter(request("GET", "/api/v1/users"))).isFalse();
        assertThat(filter.shouldNotFilter(request("POST", "/api/v1/users"))).isFalse();
        assertThat(filter.shouldNotFilter(request("GET", "/internal/v1/auth/validate"))).isFalse();
        assertThat(filter.shouldNotFilter(request("GET", "/actuator/metrics"))).isFalse();
    }

    private JwtAuthenticationFilter filter() {
        return new JwtAuthenticationFilter(jwtService, handlerExceptionResolver);
    }

    private MockHttpServletRequest request(String uri) {
        return request("GET", uri);
    }

    private MockHttpServletRequest request(String method, String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        request.setServletPath(uri);
        return request;
    }
}
