package com.mandi.web.dto;

import com.mandi.domain.Role;
import com.mandi.domain.User;

public record UserView(Long id, String name, String email, Role role) {

    public static UserView of(User u) {
        return new UserView(u.getId(), u.getName(), u.getEmail(), u.getRole());
    }
}
