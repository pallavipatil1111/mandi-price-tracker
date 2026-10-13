package com.mandi.security;

import com.mandi.domain.Role;

/** The authenticated caller, resolved from the JWT and injected with {@code @AuthenticationPrincipal}. */
public record AuthUser(Long id, String email, Role role) {

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }
}
