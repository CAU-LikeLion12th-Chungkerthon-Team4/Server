package com.chungkathon.squirrel.jwt;

import com.chungkathon.squirrel.config.MemberAuthentication;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

import static com.chungkathon.squirrel.jwt.JwtValidationType.VALID_JWT;

@Component
@Slf4j
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String requestURI = request.getRequestURI();

        // 특정 경로는 필터를 건너뛰기
        if (requestURI.equals("/join") || requestURI.equals("/login") || requestURI.equals("/api/v1/check")) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            final String token = getJwtFromRequest(request);
            JwtValidationType jwtValidationType = jwtTokenProvider.validateToken(token);
            if (jwtValidationType == VALID_JWT) {
                String username = jwtTokenProvider.getUsernameFromAccessToken(token);
                MemberAuthentication authentication = MemberAuthentication.createMemberAuthentication(username);
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } else {
                log.error("현재 상태: " + jwtValidationType.toString());
            }
        } catch (Exception e) {
            log.error(e.getMessage());
        }
        filterChain.doFilter(request, response);
    }

    private String getJwtFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring("Bearer ".length());
        }
        return null;
    }
}
