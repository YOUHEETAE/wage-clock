package com.wageclock.wageclock.global.security;


import com.wageclock.wageclock.domain.auth.UserRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;

@Component
public class JwtProvider {
    private final SecretKey key;
    private final long expiration;

    public JwtProvider(@Value("${jwt.secret}")String secret, @Value("${jwt.expiration}") long expiration) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes());
        this.expiration = expiration;
    }

    public String generateToken(Long id, UserRole role) {
        return Jwts.builder()
                .subject(String.valueOf(id))
                .claim("role", role.name())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(key)
                .compact();
    }

    public Claims getClaims(String token){
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
    public boolean validateToken(String token) {
        try {
            getClaims(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    public Long getIdFromToken(String token) {
        return Long.parseLong(getClaims(token).getSubject());
    }
    public UserRole getRoleFromToken(String token) {
        return UserRole.valueOf(getClaims(token).get("role", String.class));
    }
}
