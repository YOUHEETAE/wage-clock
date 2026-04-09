package com.wageclock.wageclock.domain.auth;

public record SignupRequest (String name, String email, String password, UserRole role) {
}
