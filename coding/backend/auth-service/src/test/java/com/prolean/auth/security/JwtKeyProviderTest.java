package com.prolean.auth.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.prolean.auth.config.AuthProperties;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class JwtKeyProviderTest {

    private static String privatePem(RSAKey key) throws Exception {
        String body = Base64.getMimeEncoder(64, new byte[] {'\n'})
                .encodeToString(key.toPrivateKey().getEncoded());
        return "-----BEGIN PRIVATE KEY-----\n" + body + "\n-----END PRIVATE KEY-----";
    }

    private static String publicPem(RSAKey key) throws Exception {
        String body = Base64.getMimeEncoder(64, new byte[] {'\n'})
                .encodeToString(key.toPublicKey().getEncoded());
        return "-----BEGIN PUBLIC KEY-----\n" + body + "\n-----END PUBLIC KEY-----";
    }

    private static JWTClaimsSet claims() {
        Instant now = Instant.now();
        return new JWTClaimsSet.Builder()
                .subject("user-1")
                .issuer("prolean-auth")
                .audience("prolean-api")
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(300)))
                .claim("roles", List.of("USER"))
                .build();
    }

    @Test
    void generatesDevelopmentKeyWhenPemIsBlank() {
        AuthProperties properties = new AuthProperties();
        properties.setKeyId("dev-kid");
        JwtKeyProvider provider = new JwtKeyProvider(properties);

        assertThat(provider.keyId()).isEqualTo("dev-kid");
        assertThat(provider.publicJwkSet().getKeys()).hasSize(1);
        assertThat(provider.publicKey()).isNotNull();
        assertThat(provider.publicJwkSet().getKeyByKeyId("dev-kid")).isNotNull();
    }

    @Test
    void loadsConfiguredPrivateKeyAndRetiredPublicKey() throws Exception {
        RSAKey active = new RSAKeyGenerator(2048).keyID("active-kid").generate();
        RSAKey retired = new RSAKeyGenerator(2048).keyID("retired-kid").generate();
        AuthProperties properties = new AuthProperties();
        properties.setKeyId("active-kid");
        properties.setPrivateKeyPem(privatePem(active));
        properties.setRetiredPublicKeys(new ObjectMapper().writeValueAsString(
                List.of(Map.of("kid", "retired-kid", "publicKeyPem", publicPem(retired)))));

        JwtKeyProvider provider = new JwtKeyProvider(properties);
        JWKSet jwks = provider.publicJwkSet();

        assertThat(jwks.getKeys()).hasSize(2);
        assertThat(jwks.getKeyByKeyId("active-kid")).isNotNull();
        assertThat(jwks.getKeyByKeyId("retired-kid")).isNotNull();
        assertThat(jwks.getKeyByKeyId("active-kid").isPrivate()).isFalse();

        String activeToken = provider.sign(claims());
        Jwt decoded = provider.decoder().decode(activeToken);
        assertThat(decoded.getSubject()).isEqualTo("user-1");

        SignedJWT retiredToken = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("retired-kid").build(), claims());
        retiredToken.sign(new RSASSASigner(retired));
        Jwt retiredDecoded = provider.decoder().decode(retiredToken.serialize());
        assertThat(retiredDecoded.getSubject()).isEqualTo("user-1");
    }

    @Test
    void rejectsPemWithoutPrivateKey() throws Exception {
        RSAKey active = new RSAKeyGenerator(2048).keyID("active").generate();
        AuthProperties properties = new AuthProperties();
        properties.setPrivateKeyPem(publicPem(active));

        assertThatThrownBy(() -> new JwtKeyProvider(properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("does not contain a private key");
    }

    @Test
    void rejectsInvalidPemAndRetiredJson() {
        AuthProperties invalidKey = new AuthProperties();
        invalidKey.setPrivateKeyPem("not-a-pem");
        assertThatThrownBy(() -> new JwtKeyProvider(invalidKey))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_PRIVATE_KEY_PEM");

        AuthProperties invalidRetired = new AuthProperties();
        invalidRetired.setRetiredPublicKeys("{not-json}");
        assertThatThrownBy(() -> new JwtKeyProvider(invalidRetired))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_RETIRED_PUBLIC_KEYS");
    }

    @Test
    void ignoresBlankRetiredConfiguration() {
        AuthProperties properties = new AuthProperties();
        properties.setRetiredPublicKeys(" ");
        JwtKeyProvider provider = new JwtKeyProvider(properties);
        assertThat(provider.publicJwkSet().getKeys()).hasSize(1);
    }
}
