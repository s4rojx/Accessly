package com.assessly.auth;

import jakarta.validation.Valid;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.assessly.infrastructure.security.RateLimitService;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthService authService;
    private final JwtProperties jwtProperties;
    private final RateLimitService rateLimitService;

    public AuthController(AuthService authService, JwtProperties jwtProperties,
                          RateLimitService rateLimitService) {
        this.authService = authService;
        this.jwtProperties = jwtProperties;
        this.rateLimitService = rateLimitService;
    }

    @GetMapping("/csrf")
    ResponseEntity<java.util.Map<String, String>> csrf(CsrfToken token, HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from("XSRF-TOKEN", token.getToken())
                .httpOnly(false).sameSite("Lax").path("/").build().toString());
        return ResponseEntity.ok(java.util.Map.of("token", token.getToken()));
    }

    @PostMapping("/signup")
    ResponseEntity<UserResponse> signup(@Valid @RequestBody SignupRequest request, HttpServletRequest httpRequest) {
        rateLimitService.checkSignup(httpRequest.getRemoteAddr());
        return ResponseEntity.status(201).body(authService.signup(request));
    }

    @PostMapping("/login")
    ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        rateLimitService.checkLogin(httpRequest.getRemoteAddr(), request.email());
        boolean adminPortal = "admin".equalsIgnoreCase(httpRequest.getHeader("X-Assessly-Portal"));
        AuthService.AuthTokens tokens = authService.login(request, adminPortal);
        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, authService.refreshCookie(tokens.rawRefreshToken()).toString())
                .body(tokens.response());
    }

    @PostMapping("/oauth2/google")
    ResponseEntity<AuthResponse> google(@Valid @RequestBody GoogleLoginRequest request, HttpServletRequest httpRequest) {
        rateLimitService.checkLogin(httpRequest.getRemoteAddr(), "google");
        AuthService.AuthTokens tokens = authService.loginWithGoogle(request);
        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, authService.refreshCookie(tokens.rawRefreshToken()).toString())
                .body(tokens.response());
    }

    @PostMapping("/refresh")
    ResponseEntity<AuthResponse> refresh(HttpServletRequest request) {
        rateLimitService.checkRefresh(request.getRemoteAddr());
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
