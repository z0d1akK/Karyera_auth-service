package com.zodiakk.authservice.auth.security.filter;

import com.zodiakk.authservice.common.constants.SecurityConstants;
import com.zodiakk.authservice.common.exception.ErrorMessages;
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

    private static final String INTERNAL_SERVICE_PRINCIPAL = "internal-service";

    private final String validInternalApiKey;

    private final AuthenticationEntryPoint authenticationEntryPoint;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {

        String providedKey = request.getHeader(SecurityConstants.INTERNAL_API_KEY_HEADER);

        if (!isValidKey(providedKey)) {
            SecurityContextHolder.clearContext();

            authenticationEntryPoint.commence(
                    request,
                    response,
                    new BadCredentialsException(ErrorMessages.INVALID_INTERNAL_API_KEY)
            );
            return;
        }

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        INTERNAL_SERVICE_PRINCIPAL,
                        null,
                        List.of(new SimpleGrantedAuthority(SecurityConstants.INTERNAL_SERVICE_AUTHORITY))
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
