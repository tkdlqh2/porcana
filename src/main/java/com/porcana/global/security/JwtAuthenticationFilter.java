package com.porcana.global.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import io.jsonwebtoken.JwtException;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import org.springframework.security.core.authority.SimpleGrantedAuthority;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String token = getTokenFromRequest(request);

        // 토큰이 있는 경우
        if (token != null) {
            // 토큰이 유효하지 않으면 401 Unauthorized
            if (!jwtTokenProvider.validateToken(token)) {
                jwtAuthenticationEntryPoint.commence(request, response,
                        new AuthenticationCredentialsNotFoundException("Invalid JWT token"));
                return;
            }

            // 토큰이 유효하면 인증 설정
            UUID userId = jwtTokenProvider.getUserIdFromToken(token);

            String role;
            try {
                role = jwtTokenProvider.getRoleFromToken(token);
            } catch (JwtException e) {
                // Role claim이 없는 토큰 (refresh token 등) → 401 Unauthorized
                jwtAuthenticationEntryPoint.commence(request, response,
                        new AuthenticationCredentialsNotFoundException("Token missing role claim"));
                return;
            }

            // Set authorities based on role
            List<SimpleGrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_" + role));

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(userId, null, authorities);
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

            SecurityContextHolder.getContext().setAuthentication(authentication);
        }
        // 토큰이 없는 경우 → 인증 없이 진행 → 401 Unauthorized (AuthenticationEntryPoint에서 처리)

        filterChain.doFilter(request, response);
    }

    private String getTokenFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}