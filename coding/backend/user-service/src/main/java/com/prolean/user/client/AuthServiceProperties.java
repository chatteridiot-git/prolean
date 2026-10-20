package com.prolean.user.client;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "prolean.services.auth")
public class AuthServiceProperties {

    private String baseUrl = "http://localhost:8081";
    private String internalKeyId = "user-service-2026-10";
    private String internalApiKey = "dev-user-service-key";

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getInternalKeyId() {
        return internalKeyId;
    }

    public void setInternalKeyId(String internalKeyId) {
        this.internalKeyId = internalKeyId;
    }

    public String getInternalApiKey() {
        return internalApiKey;
    }

    public void setInternalApiKey(String internalApiKey) {
        this.internalApiKey = internalApiKey;
    }
}
