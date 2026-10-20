package com.prolean.timeline.client;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "prolean.services.user")
public class TimelineServiceProperties {

    private String baseUrl = "http://localhost:8082";
    private String internalKeyId = "timeline-service-2026-10";
    private String internalApiKey = "dev-timeline-service-key";

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
