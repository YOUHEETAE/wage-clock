package com.wageclock.wageclock.domain.auth;

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
    @PostMapping("/login")
    public LoginResponse login(@RequestBody LoginRequest loginRequest) {
        return authService.login(loginRequest);
    }
    @PostMapping("/sign-up")
    @ResponseStatus(HttpStatus.CREATED)
    public void signup(@RequestBody SignupRequest signupRequest) {
        authService.signup(signupRequest);
    }
    @PostMapping("/logout")
    public ResponseEntity<Object> logout(@RequestHeader("Authorization") String bearerToken) {
        String token = bearerToken.substring(7);
        authService.logout(token);
        return ResponseEntity.ok().build();
    }
}
