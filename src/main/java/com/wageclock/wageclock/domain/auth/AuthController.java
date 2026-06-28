package com.wageclock.wageclock.domain.auth;

import io.swagger.v3.oas.annotations.Operation;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
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
    public ResponseEntity<Object> logout(@RequestHeader("Authorization") String bearerToken) {
        String token = bearerToken.substring(7);
        authService.logout(token);
        return ResponseEntity.ok().build();
    }
}
