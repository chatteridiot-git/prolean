package com.prolean.user.client;

import com.prolean.common.web.RequestHeaders;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Best-effort immediate cleanup of refresh-token families in auth-service.
 *
 * <p>Deactivation and role changes remain safe without this call: auth-service re-reads the enabled
 * state and roles from user-service on every refresh. A failed cleanup is logged and does not roll
 * back the profile change, avoiding a distributed transaction across the two databases.
 */
@Component
public class AuthServiceClient {

    private static final Logger log = LoggerFactory.getLogger(AuthServiceClient.class);

    private final RestClient restClient;
    private final AuthServiceProperties properties;

    public AuthServiceClient(RestClient.Builder builder, AuthServiceProperties properties) {
        this.properties = properties;
        this.restClient = builder.baseUrl(properties.getBaseUrl()).build();
    }

    public void revokeTokens(UUID userId, String reason) {
        try {
            restClient.post()
                    .uri("/internal/users/{userId}/revoke-tokens", userId)
                    .header(RequestHeaders.INTERNAL_KEY_ID, properties.getInternalKeyId())
                    .header(RequestHeaders.INTERNAL_API_KEY, properties.getInternalApiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new RevokeRequest(reason))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException exception) {
            log.warn("Refresh-token revocation failed for user {} ({}); refresh checks remain authoritative",
                    userId, reason, exception);
        }
    }

    record RevokeRequest(String reason) {
    }
}
