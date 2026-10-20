package com.prolean.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "prolean.auth")
public class AuthProperties {

    private String jwksUri = "http://localhost:8081/.well-known/jwks.json";
    private String keyId = "dev-2026-10";
    private String privateKeyPem = "";
    private String retiredPublicKeys = "[]";
    private long accessTokenTtlSeconds = 900;
    private long refreshTokenTtlDays = 7;
    private boolean cookieSecure = false;
    private String bootstrapUsername = "admin";
    private String bootstrapEmail = "admin@example.com";
    private String bootstrapFullName = "System Administrator";
    private String bootstrapPassword = "";

    public String getJwksUri() {
        return jwksUri;
    }

    public void setJwksUri(String jwksUri) {
        this.jwksUri = jwksUri;
    }

    public String getKeyId() {
        return keyId;
    }

    public void setKeyId(String keyId) {
        this.keyId = keyId;
    }

    public String getPrivateKeyPem() {
        return privateKeyPem;
    }

    public void setPrivateKeyPem(String privateKeyPem) {
        this.privateKeyPem = privateKeyPem;
    }

    public String getRetiredPublicKeys() {
        return retiredPublicKeys;
    }

    public void setRetiredPublicKeys(String retiredPublicKeys) {
        this.retiredPublicKeys = retiredPublicKeys;
    }

    public long getAccessTokenTtlSeconds() {
        return accessTokenTtlSeconds;
    }

    public void setAccessTokenTtlSeconds(long accessTokenTtlSeconds) {
        this.accessTokenTtlSeconds = accessTokenTtlSeconds;
    }

    public long getRefreshTokenTtlDays() {
        return refreshTokenTtlDays;
    }

    public void setRefreshTokenTtlDays(long refreshTokenTtlDays) {
        this.refreshTokenTtlDays = refreshTokenTtlDays;
    }

    public boolean isCookieSecure() {
        return cookieSecure;
    }

    public void setCookieSecure(boolean cookieSecure) {
        this.cookieSecure = cookieSecure;
    }

    public String getBootstrapUsername() {
        return bootstrapUsername;
    }

    public void setBootstrapUsername(String bootstrapUsername) {
        this.bootstrapUsername = bootstrapUsername;
    }

    public String getBootstrapEmail() {
        return bootstrapEmail;
    }

    public void setBootstrapEmail(String bootstrapEmail) {
        this.bootstrapEmail = bootstrapEmail;
    }

    public String getBootstrapFullName() {
        return bootstrapFullName;
    }

    public void setBootstrapFullName(String bootstrapFullName) {
        this.bootstrapFullName = bootstrapFullName;
    }

    public String getBootstrapPassword() {
        return bootstrapPassword;
    }

    public void setBootstrapPassword(String bootstrapPassword) {
        this.bootstrapPassword = bootstrapPassword;
    }
}
