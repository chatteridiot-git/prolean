package com.prolean.auth.bootstrap;

import com.prolean.auth.config.AuthProperties;
import com.prolean.auth.service.AuthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Creates the initial ADMIN profile and credential on first startup.
 *
 * <p>The auth service and user service start in parallel in Compose, so the runner retries for up
 * to one minute. A failure is logged and does not prevent the service from starting; an operator
 * can restart auth-service once user-service is reachable.
 */
@Component
public class BootstrapAdminRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BootstrapAdminRunner.class);
    private static final int MAX_ATTEMPTS = 30;

    private final AuthService authService;
    private final AuthProperties properties;

    public BootstrapAdminRunner(AuthService authService, AuthProperties properties) {
        this.authService = authService;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (properties.getBootstrapPassword() == null || properties.getBootstrapPassword().isBlank()) {
            return;
        }
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                authService.bootstrapAdmin();
                return;
            } catch (RuntimeException exception) {
                log.warn("Bootstrap attempt {}/{} failed: {}", attempt, MAX_ATTEMPTS,
                        exception.getMessage());
                if (attempt == MAX_ATTEMPTS) {
                    log.error("Bootstrap ADMIN profile was not created; restart auth-service after "
                            + "user-service is available", exception);
                    return;
                }
                try {
                    Thread.sleep(2000L);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }
}
