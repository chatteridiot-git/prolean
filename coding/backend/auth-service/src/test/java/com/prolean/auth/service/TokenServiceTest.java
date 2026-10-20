package com.prolean.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

import com.prolean.auth.config.AuthProperties;
import com.prolean.auth.domain.RefreshToken;
import com.prolean.auth.repository.RefreshTokenRepository;
import com.prolean.auth.security.JwtKeyProvider;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;

@ExtendWith(MockitoExtension.class)
class TokenServiceTest {

    @Mock
    private RefreshTokenRepository repository;

    private AuthProperties properties;
    private JwtKeyProvider keyProvider;
    private TokenService tokenService;

    @BeforeEach
    void setUp() {
        properties = new AuthProperties();
        properties.setKeyId("unit-test-key");
        keyProvider = new JwtKeyProvider(properties);
        tokenService = new TokenService(keyProvider, properties, repository);
    }

    @Test
    void issuesVerifiableAccessToken() {
        UUID userId = UUID.randomUUID();
        String token = tokenService.issueAccessToken(userId, "alice", "Alice", Set.of("ADMIN", "USER"));

        Jwt jwt = keyProvider.decoder().decode(token);

        assertThat(jwt.getSubject()).isEqualTo(userId.toString());
        assertThat(jwt.getClaimAsString("username")).isEqualTo("alice");
        assertThat(jwt.<Set<String>>getClaim("roles")).containsExactlyInAnyOrder("ADMIN", "USER");
        assertThat(jwt.getAudience()).contains("prolean");
        assertThat(jwt.getClaimAsString("iss")).isEqualTo("prolean-auth");
        assertThat(jwt.getExpiresAt()).isAfter(jwt.getIssuedAt());
    }

    @Test
    void issuesRefreshTokenWithFamilyAndHash() {
        UUID userId = UUID.randomUUID();
        TokenService.IssuedRefreshToken issued = tokenService.issueRefreshToken(userId, null, null);

        assertThat(issued.rawToken()).isNotBlank();
        assertThat(issued.familyId()).isNotNull();
        assertThat(issued.tokenId()).isNotNull();

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(repository).save(captor.capture());
        RefreshToken stored = captor.getValue();
        assertThat(stored.getId()).isEqualTo(issued.tokenId());
        assertThat(stored.getFamilyId()).isEqualTo(issued.familyId());
        assertThat(stored.getUserId()).isEqualTo(userId);
        assertThat(stored.getTokenHash()).isEqualTo(tokenService.hash(issued.rawToken()));
        assertThat(stored.getTokenHash()).hasSize(64);
        assertThat(stored.getParentTokenId()).isNull();
        assertThat(stored.getExpiresAt()).isAfter(java.time.Instant.now());
    }

    @Test
    void issuesReplacementRefreshTokenWithSuppliedIds() {
        UUID tokenId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID familyId = UUID.randomUUID();
        UUID parentId = UUID.randomUUID();

        TokenService.IssuedRefreshToken issued = tokenService.issueRefreshToken(tokenId, userId,
                familyId, parentId);

        assertThat(issued.tokenId()).isEqualTo(tokenId);
        assertThat(issued.familyId()).isEqualTo(familyId);
        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getParentTokenId()).isEqualTo(parentId);
    }

    @Test
    void hashIsDeterministicAndSha256Sized() {
        assertThat(tokenService.hash("value")).hasSize(64)
                .isEqualTo(tokenService.hash("value"))
                .isNotEqualTo(tokenService.hash("other"));
    }

    @Test
    void exposesConfiguredAccessTokenTtl() {
        properties.setAccessTokenTtlSeconds(1234);
        assertThat(tokenService.accessTokenTtlSeconds()).isEqualTo(1234);
        assertThat(properties.getAccessTokenTtlSeconds()).isEqualTo(1234);
        verify(repository, org.mockito.Mockito.never()).save(any());
    }
}
