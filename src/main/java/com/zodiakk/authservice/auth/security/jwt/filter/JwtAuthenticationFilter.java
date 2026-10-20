
package com.zodiakk.authservice.auth.security.jwt.filter;

import com.zodiakk.authservice.auth.exception.ExpiredJwtTokenException;
import com.zodiakk.authservice.auth.exception.InvalidJwtTokenException;
import com.zodiakk.authservice.auth.security.jwt.JwtTokenClaims;
import com.zodiakk.authservice.auth.security.jwt.JwtTokenType;
import com.zodiakk.authservice.auth.security.jwt.principal.AuthenticatedUser;
import com.zodiakk.authservice.auth.security.jwt.service.JwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

import java.io.IOException;
import java.util.List;

@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;

    private final HandlerExceptionResolver handlerExceptionResolver;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {

        String authorizationHeader = request.getHeader(
                HttpHeaders.AUTHORIZATION
        );

        if (authorizationHeader == null) {
            filterChain.doFilter(request, response);
            return;
        }

        if (!authorizationHeader.startsWith(BEARER_PREFIX)) {
            resolveJwtException(
                    request,
                    response,
                    new InvalidJwtTokenException()
            );
            return;
        }

        JwtTokenClaims claims;

        try {
            String token = authorizationHeader.substring(
                    BEARER_PREFIX.length()
            );

            claims = jwtService.parseToken(
                    token,
                    JwtTokenType.ACCESS
            );

        } catch (ExpiredJwtTokenException | InvalidJwtTokenException ex) {
            resolveJwtException(request, response, ex);
            return;
        }

        AuthenticatedUser principal = AuthenticatedUser.builder()
                .userId(claims.userId())
                .email(claims.email())
                .role(claims.role())
                .build();

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        principal,
                        null,
                        List.of(
                                new SimpleGrantedAuthority(
                                        "ROLE_" + claims.role().name()
                                )
                        )
                );

        SecurityContextHolder.getContext()
                .setAuthentication(authentication);

        filterChain.doFilter(request, response);
    }

    private void resolveJwtException(
            HttpServletRequest request,
            HttpServletResponse response,
            RuntimeException exception
    ) throws ServletException {

        SecurityContextHolder.clearContext();

        var result = handlerExceptionResolver.resolveException(
                request,
                response,
                null,
                exception
        );

        if (result == null) {
            throw new ServletException(
                    "JWT exception was not handled",
                    exception
            );
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {

        String path = request.getServletPath();

        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        if ("POST".equalsIgnoreCase(request.getMethod())) {
            if (path.equals("/api/auth/login")
                    || path.equals("/api/auth/refresh")
                    || path.equals("/api/auth/change-initial-password")) {
                return true;
            }
        }

        return path.equals("/actuator/health")
                || path.startsWith("/actuator/health/")
                || path.equals("/actuator/info")
                || path.equals("/api-docs")
                || path.startsWith("/api-docs/")
                || path.equals("/v3/api-docs")
                || path.startsWith("/v3/api-docs/")
                || path.equals("/swagger-ui")
                || path.startsWith("/swagger-ui/")
                || path.equals("/swagger-ui.html");
    }
}
