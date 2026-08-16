package com.wageclock.wageclock.domain.auth;

import io.swagger.v3.oas.annotations.Operation;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final String BEARER_PREFIX = "Bearer ";

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }
    @Operation(summary = "로그인")
    @PostMapping("/login")
    public LoginResponse login(@RequestBody LoginRequest loginRequest) {
        return authService.login(loginRequest);
    }
    @Operation(summary = "회원가입")
    @PostMapping("/sign-up")
    @ResponseStatus(HttpStatus.CREATED)
    public void signup(@RequestBody SignupRequest signupRequest) {
        authService.signup(signupRequest);
    }
    @Operation(summary = "로그아웃")
    @PostMapping("/logout")
    public ResponseEntity<Object> logout(
            @RequestHeader(value = "Authorization", required = false) String bearerToken) {
        // 헤더가 없거나 형식이 다르면 지울 토큰도 없다. 바로 자르면 문자열 예외로 500이 난다.
        if (bearerToken != null && bearerToken.startsWith(BEARER_PREFIX)) {
            authService.logout(bearerToken.substring(BEARER_PREFIX.length()));
        }
        return ResponseEntity.ok().build();
    }
}
