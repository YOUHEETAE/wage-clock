package com.wageclock.wageclock.domain.auth;


public record LoginRequest (String email, String password, UserRole role) {}
