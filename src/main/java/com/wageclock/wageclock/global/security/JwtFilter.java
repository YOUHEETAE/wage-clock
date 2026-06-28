package com.wageclock.wageclock.global.security;

import com.wageclock.wageclock.domain.auth.UserRole;
import jakarta.annotation.Nonnull;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

public class JwtFilter extends OncePerRequestFilter {

    private final JwtProvider jwtProvider;
    private final RedisTemplate<String, String> redisTemplate;

    public JwtFilter(JwtProvider jwtProvider,  RedisTemplate<String, String> redisTemplate) {
        this.jwtProvider = jwtProvider;
        this.redisTemplate = redisTemplate;
    }

    @Override
    protected void  doFilterInternal(@Nonnull HttpServletRequest request,
                                     @Nonnull HttpServletResponse response,
                                     @Nonnull FilterChain filterChain) throws ServletException, IOException {
        String token = resolveToken(request);
        if (token != null && jwtProvider.validateToken(token)
                && !redisTemplate.hasKey("blacklist:" + token)) {
            Long id = jwtProvider.getIdFromToken(token);
            UserRole role = jwtProvider.getRoleFromToken(token);

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(id, null,
                            List.of(new SimpleGrantedAuthority(role.name())));

            SecurityContextHolder.getContext().setAuthentication(authentication);
        }
        filterChain.doFilter(request, response);
    }

    private String resolveToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
