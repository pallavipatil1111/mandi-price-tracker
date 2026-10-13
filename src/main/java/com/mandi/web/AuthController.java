package com.mandi.web;

import com.mandi.security.AuthUser;
import com.mandi.service.AuthService;
import com.mandi.web.dto.AuthResponse;
import com.mandi.web.dto.LoginRequest;
import com.mandi.web.dto.RegisterRequest;
import com.mandi.web.dto.UserView;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/api/auth/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @PostMapping("/api/auth/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @GetMapping("/api/me")
    public UserView me(@AuthenticationPrincipal AuthUser me) {
        return authService.me(me.id());
    }
}
