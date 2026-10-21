package com.assessly.auth;

import com.assessly.infrastructure.redis.SessionLockService;
import com.assessly.shared.ApiException;
import com.assessly.user.UserEntity;
import com.assessly.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class AuthService {
    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;
    private final SessionLockService sessionLock;
    private final SecureRandom secureRandom = new SecureRandom();
    private final Clock clock = Clock.systemUTC();

    public AuthService(UserRepository users, RefreshTokenRepository refreshTokens, PasswordEncoder passwordEncoder,
                       AuthenticationManager authenticationManager, JwtService jwtService, JwtProperties jwtProperties,
                       SessionLockService sessionLock) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.jwtProperties = jwtProperties;
        this.sessionLock = sessionLock;
    }

    @Transactional
    public UserResponse signup(SignupRequest request) {
        String email = normalizeEmail(request.email());
        String rollNumber = normalizeOptional(request.rollNumber());
        if (users.existsByEmail(email)) throw conflict("EMAIL_ALREADY_REGISTERED", "Email is already registered");
        if (rollNumber != null && users.existsByRollNumber(rollNumber)) {
            throw conflict("ROLL_NUMBER_ALREADY_REGISTERED", "Roll number is already registered");
        }
        UserEntity user = UserEntity.candidate(email, request.fullName().trim(), rollNumber,
                passwordEncoder.encode(request.password()));
        return UserResponse.from(users.save(user));
    }

    @Transactional
    public AuthTokens login(LoginRequest request) {
        String email = normalizeEmail(request.email());
        try {
            authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(email, request.password()));
        } catch (BadCredentialsException exception) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_FAILED", "Authentication failed");
        }
        UserEntity user = users.findByEmail(email)
                .filter(UserEntity::isActive)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_FAILED", "Authentication failed"));
        return issueTokens(user, UUID.randomUUID());
    }

    @Transactional(noRollbackFor = ApiException.class)
    public AuthTokens refresh(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) invalidRefresh();
        RefreshTokenEntity current = refreshTokens.findByTokenHash(hash(rawToken)).orElse(null);
        if (current == null) invalidRefresh();
        Instant now = clock.instant();
        if (current.isRevoked()) {
            revokeFamily(current.getTokenFamilyId(), now);
            throw new ApiException(HttpStatus.UNAUTHORIZED, "REFRESH_TOKEN_REUSE", "Refresh token is no longer valid");
        }
        if (current.isExpired(now) || !current.getUser().isActive()) invalidRefresh();
        current.markUsed(now);
        refreshTokens.save(current);
        return issueTokens(current.getUser(), current.getTokenFamilyId());
    }

    @Transactional
    public void logout(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) return;
        refreshTokens.findByTokenHash(hash(rawToken)).ifPresent(token -> {
            if (!token.isRevoked()) {
                token.revoke(clock.instant());
                refreshTokens.save(token);
            }
            // Phase 4: release single-session lock so the candidate can start a new session
            sessionLock.forceReleaseSession(token.getUser().getId());
        });
    }

    @Transactional
    public void bootstrapAdmin(String email, String fullName, String password) {
        String normalizedEmail = normalizeEmail(email);
        if (users.existsByRole(com.assessly.user.AuthRole.ROLE_ADMIN)) {
            throw new IllegalStateException("An administrator already exists");
        }
        if (users.existsByEmail(normalizedEmail)) throw new IllegalStateException("Bootstrap email is already registered");
        UserEntity admin = UserEntity.administrator(normalizedEmail, fullName.trim(), passwordEncoder.encode(password));
        users.save(admin);
    }

    public ResponseCookie refreshCookie(String rawToken) {
        return ResponseCookie.from(jwtProperties.getRefreshCookieName(), rawToken)
                .httpOnly(true).secure(jwtProperties.isRefreshCookieSecure()).sameSite("Strict")
                .path("/api/v1/auth").maxAge(jwtProperties.getRefreshTokenTtl()).build();
    }

    public ResponseCookie clearRefreshCookie() {
        return ResponseCookie.from(jwtProperties.getRefreshCookieName(), "")
                .httpOnly(true).secure(jwtProperties.isRefreshCookieSecure()).sameSite("Strict")
                .path("/api/v1/auth").maxAge(0).build();
    }

    private AuthTokens issueTokens(UserEntity user, UUID familyId) {
        String raw = randomToken();
        Instant now = clock.instant();
        refreshTokens.save(RefreshTokenEntity.issue(user, hash(raw), familyId, now,
                now.plus(jwtProperties.getRefreshTokenTtl())));
        AuthResponse response = new AuthResponse(jwtService.issueAccessToken(user), "Bearer",
                jwtService.accessTokenLifetimeSeconds(), UserResponse.from(user));
        return new AuthTokens(response, raw);
    }

    private void revokeFamily(UUID familyId, Instant now) {
        refreshTokens.revokeActiveFamily(familyId, now);
    }

    private String randomToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String raw) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to hash refresh token", exception);
        }
    }

    private static String normalizeEmail(String value) { return value.trim().toLowerCase(Locale.ROOT); }
    private static String normalizeOptional(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private static ApiException conflict(String code, String message) { return new ApiException(HttpStatus.CONFLICT, code, message); }
    private static void invalidRefresh() { throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_REFRESH_TOKEN", "Refresh token is invalid"); }

    public record AuthTokens(AuthResponse response, String rawRefreshToken) { }
}
