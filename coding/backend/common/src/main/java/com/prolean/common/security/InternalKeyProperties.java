package com.prolean.common.security;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Per-caller internal API keys and endpoint scopes.
 *
 * <p>Secrets are supplied through environment variables, never committed. Each receiving service
 * configures only the callers it accepts, which limits the blast radius of a leaked key.
 */
@ConfigurationProperties(prefix = "prolean.internal")
public class InternalKeyProperties {

    private Map<String, Caller> callers = new LinkedHashMap<>();

    public Map<String, Caller> getCallers() {
        return callers;
    }

    public void setCallers(Map<String, Caller> callers) {
        this.callers = callers == null ? new LinkedHashMap<>() : callers;
    }

    @PostConstruct
    void validateSecrets() {
        callers.forEach((keyId, caller) -> {
            if (caller.getSecret() == null || caller.getSecret().length() < 32) {
                throw new IllegalStateException(
                        "Internal API key for caller " + keyId + " must be at least 256 bits (32 characters)");
            }
        });
    }

    public static class Caller {
        private String secret;
        private List<String> scopes = new ArrayList<>();

        public String getSecret() {
            return secret;
        }

        public void setSecret(String secret) {
            this.secret = secret;
        }

        public List<String> getScopes() {
            return scopes;
        }

        public void setScopes(List<String> scopes) {
            this.scopes = scopes == null ? new ArrayList<>() : scopes;
        }
    }
}
