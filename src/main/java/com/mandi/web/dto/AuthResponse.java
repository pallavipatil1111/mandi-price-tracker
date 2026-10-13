package com.mandi.web.dto;

public record AuthResponse(String token, long expiresIn, UserView user) {
}
