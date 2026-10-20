package com.prolean.common.security;

import com.prolean.common.error.ApiException;
import com.prolean.common.error.ErrorCode;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * The authenticated caller extracted from an access token.
 *
 * @param id user UUID (JWT subject)
 * @param username login name
 * @param fullName display name
 * @param roles role names without the {@code ROLE_} prefix
 */
public record CurrentUser(UUID id, String username, String fullName, Set<String> roles) {

    public CurrentUser {
        roles = roles == null ? Set.of() : Set.copyOf(roles);
    }

    public static CurrentUser from(Jwt jwt) {
        if (jwt == null) {
            throw new ApiException(ErrorCode.UNAUTHORIZED);
        }
        UUID id;
        try {
            id = UUID.fromString(jwt.getSubject());
        } catch (RuntimeException exception) {
            throw new ApiException(ErrorCode.UNAUTHORIZED, "The access token has no valid subject.");
        }
        List<String> roleValues = jwt.getClaimAsStringList(JwtSupport.ROLES_CLAIM);
        Set<String> roles = roleValues == null ? Set.of() : new LinkedHashSet<>(roleValues);
        return new CurrentUser(id, jwt.getClaimAsString("username"), jwt.getClaimAsString("fullName"), roles);
    }

    public boolean isAdmin() {
        return roles.contains("ADMIN");
    }
}
