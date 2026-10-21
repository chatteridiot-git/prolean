package com.prolean.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.prolean.common.error.ApiException;
import com.prolean.common.error.ErrorCode;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;

class CurrentUserTest {

    @Test
    void mapsJwtClaimsAndAdminRole() {
        UUID id = UUID.randomUUID();
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject(id.toString())
                .claim("username", "alice")
                .claim("name", "Alice")
                .claim("roles", List.of("ADMIN", "USER"))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .build();

        CurrentUser user = CurrentUser.from(jwt);

        assertThat(user.id()).isEqualTo(id);
        assertThat(user.username()).isEqualTo("alice");
        assertThat(user.fullName()).isEqualTo("Alice");
        assertThat(user.roles()).containsExactlyInAnyOrder("ADMIN", "USER");
        assertThat(user.isAdmin()).isTrue();
    }

    @Test
    void handlesMissingRolesAndRejectsInvalidSubject() {
        Jwt noRoles = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject(UUID.randomUUID().toString())
                .build();
        assertThat(CurrentUser.from(noRoles).roles()).isEmpty();
        assertThat(new CurrentUser(UUID.randomUUID(), "u", "U", null).roles()).isEmpty();

        Jwt invalid = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject("not-a-uuid")
                .build();
        assertThatThrownBy(() -> CurrentUser.from(invalid))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.UNAUTHORIZED));
        assertThatThrownBy(() -> CurrentUser.from(null))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.UNAUTHORIZED));
    }

    @Test
    void rolesConverterMapsRolesToAuthorityPrefix() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject(UUID.randomUUID().toString())
                .claim("roles", List.of("ADMIN", "USER"))
                .build();

        var converter = JwtSupport.rolesConverter();
        AbstractAuthenticationToken authentication = converter.convert(jwt);

        assertThat(authentication.getAuthorities()).extracting("authority")
                .containsExactlyInAnyOrder("ROLE_ADMIN", "ROLE_USER");
    }

    @Test
    void validatorAcceptsExpectedIssuerAudienceAndRejectsMissingAudience() {
        var validator = JwtSupport.validator();
        Instant now = Instant.now();
        Jwt valid = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject("user")
                .issuer("prolean-auth")
                .audience(List.of("prolean-api"))
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300))
                .build();
        Jwt wrongAudience = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject("user")
                .issuer("prolean-auth")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300))
                .build();

        assertThat(validator.validate(valid).hasErrors()).isFalse();
        assertThat(validator.validate(wrongAudience).hasErrors()).isTrue();
    }
}
