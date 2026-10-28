package com.zodiakk.authservice.auth.security.filter;

import com.zodiakk.authservice.auth.security.handler.CustomAuthenticationEntryPoint;
import com.zodiakk.authservice.common.constants.SecurityConstants;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;

class InternalApiKeyFilterTest {

    private static final String VALID_KEY = "internal-key";

    private final InternalApiKeyFilter filter =
            new InternalApiKeyFilter(VALID_KEY, new CustomAuthenticationEntryPoint());

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("authenticates a request with a valid internal key")
    void shouldAuthenticateValidKey() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/internal/v1/auth/validate");
        request.addHeader(SecurityConstants.INTERNAL_API_KEY_HEADER, VALID_KEY);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isNotNull();
        assertThat(response.getStatus()).isEqualTo(200);

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNotNull();
        assertThat(authentication.isAuthenticated()).isTrue();
        assertThat(authentication.getAuthorities())
                .extracting(Object::toString)
                .containsExactly(SecurityConstants.INTERNAL_SERVICE_AUTHORITY);
    }

    @Test
    @DisplayName("rejects a request without an internal key")
    void shouldRejectMissingKey() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/internal/v1/auth/validate");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isNull();
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("Unauthorized");
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("rejects a request with an invalid internal key")
    void shouldRejectInvalidKey() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/internal/v1/auth/validate");
        request.addHeader(SecurityConstants.INTERNAL_API_KEY_HEADER, "wrong-key");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isNull();
        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    @DisplayName("rejects every request when no key is configured")
    void shouldRejectWhenConfiguredKeyIsBlank() throws Exception {
        InternalApiKeyFilter filterWithoutKey =
                new InternalApiKeyFilter("  ", new CustomAuthenticationEntryPoint());
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/internal/v1/auth/validate");
        request.addHeader(SecurityConstants.INTERNAL_API_KEY_HEADER, "  ");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filterWithoutKey.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isNull();
        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    @DisplayName("rejects every request when configured key is null")
    void shouldRejectWhenConfiguredKeyIsNull() throws Exception {
        InternalApiKeyFilter filterWithoutKey =
                new InternalApiKeyFilter(null, new CustomAuthenticationEntryPoint());
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/internal/v1/auth/validate");
        request.addHeader(SecurityConstants.INTERNAL_API_KEY_HEADER, "internal-key");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filterWithoutKey.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isNull();
        assertThat(response.getStatus()).isEqualTo(401);
    }
}
