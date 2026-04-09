package com.wageclock.wageclock.domain.auth;

public record LoginResponse (String name, String email, UserRole role, String token) {}
