package com.assessly.infrastructure.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "assessly.rate-limit")
public class RateLimitProperties {
    private boolean enabled = true;
    private int loginLimit = 5;
    private long loginWindowSeconds = 900;
    private int signupLimit = 5;
    private long signupWindowSeconds = 900;
    private int refreshLimit = 30;
    private long refreshWindowSeconds = 900;
    private int candidateStartLimit = 10;
    private long candidateStartWindowSeconds = 60;
    private int candidateActionLimit = 100;
    private long candidateActionWindowSeconds = 60;
    private int candidateSubmitLimit = 5;
    private long candidateSubmitWindowSeconds = 60;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public int getLoginLimit() { return loginLimit; }
    public void setLoginLimit(int value) { loginLimit = positive(value, 5); }
    public long getLoginWindowSeconds() { return loginWindowSeconds; }
    public void setLoginWindowSeconds(long value) { loginWindowSeconds = positive(value, 900); }
    public int getSignupLimit() { return signupLimit; }
    public void setSignupLimit(int value) { signupLimit = positive(value, 5); }
    public long getSignupWindowSeconds() { return signupWindowSeconds; }
    public void setSignupWindowSeconds(long value) { signupWindowSeconds = positive(value, 900); }
    public int getRefreshLimit() { return refreshLimit; }
    public void setRefreshLimit(int value) { refreshLimit = positive(value, 30); }
    public long getRefreshWindowSeconds() { return refreshWindowSeconds; }
    public void setRefreshWindowSeconds(long value) { refreshWindowSeconds = positive(value, 900); }
    public int getCandidateStartLimit() { return candidateStartLimit; }
    public void setCandidateStartLimit(int value) { candidateStartLimit = positive(value, 10); }
    public long getCandidateStartWindowSeconds() { return candidateStartWindowSeconds; }
    public void setCandidateStartWindowSeconds(long value) { candidateStartWindowSeconds = positive(value, 60); }
    public int getCandidateActionLimit() { return candidateActionLimit; }
    public void setCandidateActionLimit(int value) { candidateActionLimit = positive(value, 100); }
    public long getCandidateActionWindowSeconds() { return candidateActionWindowSeconds; }
    public void setCandidateActionWindowSeconds(long value) { candidateActionWindowSeconds = positive(value, 60); }
    public int getCandidateSubmitLimit() { return candidateSubmitLimit; }
    public void setCandidateSubmitLimit(int value) { candidateSubmitLimit = positive(value, 5); }
    public long getCandidateSubmitWindowSeconds() { return candidateSubmitWindowSeconds; }
    public void setCandidateSubmitWindowSeconds(long value) { candidateSubmitWindowSeconds = positive(value, 60); }

    private static int positive(int value, int fallback) { return value > 0 ? value : fallback; }
    private static long positive(long value, long fallback) { return value > 0 ? value : fallback; }
}
