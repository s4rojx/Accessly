package com.assessly.auth;

import com.assessly.shared.ApiException;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.security.GeneralSecurityException;
import java.util.List;

@Component
public class GoogleIdentityVerifier {
    private final GoogleIdTokenVerifier verifier;

    public GoogleIdentityVerifier(@Value("${assessly.google.client-id:}") String clientId) {
        this.verifier = createVerifier(clientId == null ? "" : clientId.trim());
    }

    public GoogleIdentity verify(String credential) {
        if (verifier == null) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "GOOGLE_AUTH_UNAVAILABLE",
                    "Google sign-in is not configured");
        }
        try {
            GoogleIdToken token = verifier.verify(credential);
            if (token == null || !Boolean.TRUE.equals(token.getPayload().getEmailVerified())) throw invalidCredential();
            GoogleIdToken.Payload payload = token.getPayload();
            String email = payload.getEmail();
            String subject = payload.getSubject();
            String name = payload.get("name") instanceof String value ? value : email;
            String picture = payload.get("picture") instanceof String value ? value : null;
            if (email == null || email.isBlank() || subject == null || subject.isBlank()) throw invalidCredential();
            return new GoogleIdentity(email, name, subject, picture);
        } catch (ApiException exception) {
            throw exception;
        } catch (Exception exception) {
            throw invalidCredential();
        }
    }

    private static GoogleIdTokenVerifier createVerifier(String clientId) {
        if (clientId.isBlank()) return null;
        try {
            return new GoogleIdTokenVerifier.Builder(GoogleNetHttpTransport.newTrustedTransport(), GsonFactory.getDefaultInstance())
                    .setAudience(List.of(clientId)).build();
        } catch (GeneralSecurityException | java.io.IOException exception) {
            throw new IllegalStateException("Unable to initialize Google token verification", exception);
        }
    }

    private static ApiException invalidCredential() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "GOOGLE_AUTH_FAILED", "Google authentication failed");
    }

    public record GoogleIdentity(String email, String fullName, String subject, String pictureUrl) { }
}
