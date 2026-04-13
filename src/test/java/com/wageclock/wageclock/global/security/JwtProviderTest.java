package com.wageclock.wageclock.global.security;

import com.wageclock.wageclock.domain.auth.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class JwtProviderTest {
    JwtProvider jwtProvider;

    @BeforeEach
    void setUp() {
        jwtProvider = new JwtProvider("testsecretkey_must_be_long_enough_32chars", 3600000L);
    }


    @Test
    void validateToken_통과_검증 (){
        String token = jwtProvider.generateToken(1L, UserRole.WORKER);
        assertTrue(jwtProvider.validateToken(token));
    }

    @Test
    void 토큰_아이디_일치(){
        String token = jwtProvider.generateToken(1L, UserRole.WORKER);
        assertEquals(1L, jwtProvider.getIdFromToken(token));
    }

    @Test
    void 토큰_role_일치(){
        String token = jwtProvider.generateToken(1L, UserRole.WORKER);
        assertEquals(UserRole.WORKER, jwtProvider.getRoleFromToken(token));
    }
    @Test
    void 위조_토큰_false_반환(){
        String fakeToken = "this.is.fake.token";
        assertFalse(jwtProvider.validateToken(fakeToken));
    }
}
