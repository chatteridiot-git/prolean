package com.prolean.auth.service;

import com.prolean.auth.config.AuthProperties;
import com.prolean.auth.domain.RefreshToken;
import com.prolean.auth.repository.RefreshTokenRepository;
import com.prolean.auth.security.JwtKeyProvider;
import com.prolean.common.security.JwtSupport;
import com.nimbusds.jwt.JWTClaimsSet;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.HexFormat;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class TokenService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder BASE64_URL = Base64.getUrlEncoder().withoutPadding();

    private final JwtKeyProvider keyProvider;
    private final AuthProperties properties;
    private final RefreshTokenRepository refreshTokenRepository;

    public TokenService(JwtKeyProvider keyProvider, AuthProperties properties,
            RefreshTokenRepository refreshTokenRepository) {
        this.keyProvider = keyProvider;
        this.properties = properties;
        this.refreshTokenRepository = refreshTokenRepository;
    }

    public String issueAccessToken(UUID userId, String username, String fullName, Set<String> roles) {
        Instant now = Instant.now();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(userId.toString())
                .issuer(JwtSupport.ISSUER)
                .audience(JwtSupport.AUDIENCE)
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(properties.getAccessTokenTtlSeconds())))
                .jwtID(UUID.randomUUID().toString())
                .claim("username", username)
                .claim("name", fullName)
                .claim("roles", roles)
                .build();
        return keyProvider.sign(claims);
    }

    public IssuedRefreshToken issueRefreshToken(UUID userId, UUID familyId, UUID parentTokenId) {
        return issueRefreshToken(UUID.randomUUID(), userId,
                familyId == null ? UUID.randomUUID() : familyId, parentTokenId);
    }

    public IssuedRefreshToken issueRefreshToken(UUID tokenId, UUID userId, UUID familyId, UUID parentTokenId) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String rawToken = BASE64_URL.encodeToString(bytes);
        RefreshToken token = new RefreshToken();
        token.setId(tokenId);
        token.setUserId(userId);
        token.setFamilyId(familyId);
        token.setParentTokenId(parentTokenId);
        token.setTokenHash(hash(rawToken));
        token.setExpiresAt(Instant.now().plusSeconds(properties.getRefreshTokenTtlDays() * 86400));
        refreshTokenRepository.save(token);
        return new IssuedRefreshToken(rawToken, token.getId(), familyId);
    }

    public String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    public long accessTokenTtlSeconds() {
        return properties.getAccessTokenTtlSeconds();
    }

    public record IssuedRefreshToken(String rawToken, UUID tokenId, UUID familyId) {
    }
}
