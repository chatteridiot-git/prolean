package com.prolean.common.security;

import java.util.List;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;

/** Shared JWT decoder and authority-mapping helpers. */
public final class JwtSupport {

    public static final String ISSUER = "prolean-auth";
    public static final String AUDIENCE = "prolean-api";
    public static final String ROLES_CLAIM = "roles";

    private JwtSupport() {
    }

    /** Builds a decoder that fetches and caches the auth-service JWKS endpoint. */
    public static JwtDecoder jwksDecoder(String jwksUri) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwksUri)
                .jwsAlgorithm(SignatureAlgorithm.RS256)
                .build();
        decoder.setJwtValidator(validator());
        return decoder;
    }

    /** Builds a decoder from a local public key (used by auth-service and tests). */
    public static JwtDecoder publicKeyDecoder(java.security.interfaces.RSAPublicKey publicKey) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(publicKey)
                .signatureAlgorithm(SignatureAlgorithm.RS256)
                .build();
        decoder.setJwtValidator(validator());
        return decoder;
    }

    /** Issuer, audience, and timestamp validation applied by every service. */
    public static OAuth2TokenValidator<Jwt> validator() {
        OAuth2TokenValidator<Jwt> audience = new JwtClaimValidator<List<String>>(
                JwtClaimNames.AUD, aud -> aud != null && aud.contains(AUDIENCE));
        return new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefaultWithIssuer(ISSUER), audience);
    }

    /** Maps the {@code roles} claim to Spring Security {@code ROLE_*} authorities. */
    public static Converter<Jwt, AbstractAuthenticationToken> rolesConverter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName(ROLES_CLAIM);
        authorities.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }
}
