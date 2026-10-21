package com.assessly.auth;

import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AdminBootstrapRunnerTest {
    @Test
    void bootstrapRequiresExplicitEnvironmentValues() {
        AdminBootstrapRunner runner = new AdminBootstrapRunner(null, new MockEnvironment());
        assertThatThrownBy(() -> runner.run(new DefaultApplicationArguments("--bootstrap-admin")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ASSESSLY_BOOTSTRAP_ADMIN_EMAIL");
    }
}
