package com.assessly.auth;

import jakarta.validation.Valid;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthService authService;
    private final JwtProperties jwtProperties;

    public AuthController(AuthService authService, JwtProperties jwtProperties) {
        this.authService = authService;
        this.jwtProperties = jwtProperties;
    }

    @PostMapping("/signup")
    ResponseEntity<UserResponse> signup(@Valid @RequestBody SignupRequest request) {
        return ResponseEntity.status(201).body(authService.signup(request));
    }

    @PostMapping("/login")
    ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthService.AuthTokens tokens = authService.login(request);
        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, authService.refreshCookie(tokens.rawRefreshToken()).toString())
                .body(tokens.response());
    }

    @PostMapping("/refresh")
    ResponseEntity<AuthResponse> refresh(HttpServletRequest request) {
        AuthService.AuthTokens tokens = authService.refresh(refreshToken(request));
        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, authService.refreshCookie(tokens.rawRefreshToken()).toString())
                .body(tokens.response());
    }

    @PostMapping("/logout")
    ResponseEntity<Void> logout(HttpServletRequest request) {
        authService.logout(refreshToken(request));
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, authService.clearRefreshCookie().toString()).build();
    }

    private String refreshToken(HttpServletRequest request) {
        if (request.getCookies() == null) return null;
        for (Cookie cookie : request.getCookies()) {
            if (jwtProperties.getRefreshCookieName().equals(cookie.getName())) return cookie.getValue();
        }
        return null;
    }
}
