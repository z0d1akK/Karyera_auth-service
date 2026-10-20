package com.zodiakk.authservice.auth.security.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

@RequiredArgsConstructor
public class InternalApiKeyFilter extends OncePerRequestFilter {

    public static final String INTERNAL_HEADER = "X-Internal-Key";

    public static final String INTERNAL_SERVICE_ROLE = "ROLE_INTERNAL_SERVICE";

    private static final String INTERNAL_SERVICE_PRINCIPAL = "internal-service";

    private final String validInternalApiKey;

    private final AuthenticationEntryPoint authenticationEntryPoint;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {

        String providedKey = request.getHeader(INTERNAL_HEADER);

        if (!isValidKey(providedKey)) {
            SecurityContextHolder.clearContext();

            authenticationEntryPoint.commence(
                    request,
                    response,
                    new BadCredentialsException("Invalid internal API key")
            );
            return;
        }

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        INTERNAL_SERVICE_PRINCIPAL,
                        null,
                        List.of(new SimpleGrantedAuthority(INTERNAL_SERVICE_ROLE))
                );

        SecurityContextHolder.getContext().setAuthentication(authentication);

        filterChain.doFilter(request, response);
    }

    private boolean isValidKey(String providedKey) {
        if (providedKey == null || validInternalApiKey == null
                || validInternalApiKey.isBlank()) {
            return false;
        }

        return MessageDigest.isEqual(
                validInternalApiKey.getBytes(StandardCharsets.UTF_8),
                providedKey.getBytes(StandardCharsets.UTF_8)
        );
    }
}
