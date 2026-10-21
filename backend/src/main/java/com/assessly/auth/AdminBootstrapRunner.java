package com.assessly.auth;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class AdminBootstrapRunner implements ApplicationRunner {
    private final AuthService authService;
    private final Environment environment;

    public AdminBootstrapRunner(AuthService authService, Environment environment) {
        this.authService = authService;
        this.environment = environment;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!args.containsOption("bootstrap-admin")) return;
        String email = required("ASSESSLY_BOOTSTRAP_ADMIN_EMAIL");
        String name = required("ASSESSLY_BOOTSTRAP_ADMIN_NAME");
        String password = required("ASSESSLY_BOOTSTRAP_ADMIN_PASSWORD");
        if (password.length() < 12) throw new IllegalArgumentException("ASSESSLY_BOOTSTRAP_ADMIN_PASSWORD must contain at least 12 characters");
        authService.bootstrapAdmin(email, name, password);
    }

    private String required(String name) {
        String value = environment.getProperty(name);
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must be explicitly configured");
        return value;
    }
}
