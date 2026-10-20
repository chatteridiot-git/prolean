package com.prolean.auth.security;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.JWSKeySelector;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import com.prolean.auth.config.AuthProperties;
import com.prolean.common.security.JwtSupport;
import java.security.interfaces.RSAPublicKey;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;

/**
 * Owns the active RS256 signing key and publishes its public half through JWKS.
 *
 * <p>A production deployment supplies {@code JWT_PRIVATE_KEY_PEM}. Retired public keys are supplied
 * as a JSON array through {@code JWT_RETIRED_PUBLIC_KEYS} and remain in JWKS so access tokens issued
 * just before a manual rotation keep validating until they expire. Without a configured key the
 * service generates a development key and logs a warning.
 */
@Component
public class JwtKeyProvider {

    private static final Logger log = LoggerFactory.getLogger(JwtKeyProvider.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final RSAKey rsaKey;
    private final RSASSASigner signer;
    private final JWKSet jwkSet;

    public JwtKeyProvider(AuthProperties properties) {
        String keyId = properties.getKeyId();
        this.rsaKey = loadOrGenerate(properties.getPrivateKeyPem(), keyId);
        try {
            this.signer = new RSASSASigner(rsaKey);
        } catch (JOSEException exception) {
            throw new IllegalStateException("Unable to initialize the JWT signer", exception);
        }
        List<RSAKey> publicKeys = new ArrayList<>();
        publicKeys.add(rsaKey.toPublicJWK());
        publicKeys.addAll(parseRetiredKeys(properties.getRetiredPublicKeys()));
        this.jwkSet = new JWKSet(new ArrayList<JWK>(publicKeys));
    }

    private RSAKey loadOrGenerate(String privateKeyPem, String keyId) {
        if (privateKeyPem == null || privateKeyPem.isBlank()) {
            log.warn("JWT_PRIVATE_KEY_PEM is not configured; generating an ephemeral development key. "
                    + "Access tokens will not survive a restart.");
            try {
                return new RSAKeyGenerator(2048)
                        .keyID(keyId)
                        .keyUse(KeyUse.SIGNATURE)
                        .algorithm(JWSAlgorithm.RS256)
                        .generate();
            } catch (JOSEException exception) {
                throw new IllegalStateException("Unable to generate the JWT signing key", exception);
            }
        }
        try {
            RSAKey parsed = (RSAKey) RSAKey.parseFromPEMEncodedObjects(privateKeyPem);
            if (!parsed.isPrivate()) {
                throw new IllegalStateException("JWT_PRIVATE_KEY_PEM does not contain a private key");
            }
            return new RSAKey.Builder(parsed)
                    .keyID(keyId)
                    .keyUse(KeyUse.SIGNATURE)
                    .algorithm(JWSAlgorithm.RS256)
                    .build();
        } catch (JOSEException exception) {
            throw new IllegalStateException("Unable to parse JWT_PRIVATE_KEY_PEM", exception);
        }
    }

    private List<RSAKey> parseRetiredKeys(String retiredJson) {
        if (retiredJson == null || retiredJson.isBlank()) {
            return List.of();
        }
        try {
            List<RetiredKey> parsed = MAPPER.readValue(retiredJson, new TypeReference<>() {
            });
            List<RSAKey> keys = new ArrayList<>();
            for (RetiredKey retired : parsed) {
                RSAKey key = (RSAKey) RSAKey.parseFromPEMEncodedObjects(retired.publicKeyPem());
                keys.add(new RSAKey.Builder(key.toPublicJWK()).keyID(retired.kid())
                        .keyUse(KeyUse.SIGNATURE)
                        .algorithm(JWSAlgorithm.RS256)
                        .build());
            }
            return keys;
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to parse JWT_RETIRED_PUBLIC_KEYS", exception);
        }
    }

    public String keyId() {
        return rsaKey.getKeyID();
    }

    public JWKSet publicJwkSet() {
        return jwkSet;
    }

    public RSAPublicKey publicKey() {
        try {
            return rsaKey.toRSAPublicKey();
        } catch (JOSEException exception) {
            throw new IllegalStateException("Unable to expose the JWT public key", exception);
        }
    }

    public String sign(JWTClaimsSet claims) {
        try {
            SignedJWT jwt = new SignedJWT(
                    new JWSHeader.Builder(JWSAlgorithm.RS256)
                            .keyID(rsaKey.getKeyID())
                            .type(com.nimbusds.jose.JOSEObjectType.JWT)
                            .build(),
                    claims);
            jwt.sign(signer);
            return jwt.serialize();
        } catch (JOSEException exception) {
            throw new IllegalStateException("Unable to sign the access token", exception);
        }
    }

    public JwtDecoder decoder() {
        ImmutableJWKSet<SecurityContext> source = new ImmutableJWKSet<>(jwkSet);
        JWSKeySelector<SecurityContext> selector =
                new JWSVerificationKeySelector<>(JWSAlgorithm.RS256, source);
        DefaultJWTProcessor<SecurityContext> processor = new DefaultJWTProcessor<>();
        processor.setJWSKeySelector(selector);
        NimbusJwtDecoder decoder = new NimbusJwtDecoder(processor);
        decoder.setJwtValidator(JwtSupport.validator());
        return decoder;
    }

    private record RetiredKey(String kid, String publicKeyPem) {
    }
}
