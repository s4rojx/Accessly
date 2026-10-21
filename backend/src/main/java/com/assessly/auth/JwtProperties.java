package com.assessly.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "assessly.jwt")
public class JwtProperties {
    private String privateKeyBase64;
    private String publicKeyBase64;
    private Duration accessTokenTtl = Duration.ofMinutes(15);
    private Duration refreshTokenTtl = Duration.ofDays(30);
    private String refreshCookieName = "assessly_refresh";
    private boolean refreshCookieSecure;

    public String getPrivateKeyBase64() { return privateKeyBase64; }
    public void setPrivateKeyBase64(String value) { privateKeyBase64 = value; }
    public String getPublicKeyBase64() { return publicKeyBase64; }
    public void setPublicKeyBase64(String value) { publicKeyBase64 = value; }
    public Duration getAccessTokenTtl() { return accessTokenTtl; }
    public void setAccessTokenTtl(Duration value) { accessTokenTtl = value; }
    public Duration getRefreshTokenTtl() { return refreshTokenTtl; }
    public void setRefreshTokenTtl(Duration value) { refreshTokenTtl = value; }
    public String getRefreshCookieName() { return refreshCookieName; }
    public void setRefreshCookieName(String value) { refreshCookieName = value; }
    public boolean isRefreshCookieSecure() { return refreshCookieSecure; }
    public void setRefreshCookieSecure(boolean value) { refreshCookieSecure = value; }
}
