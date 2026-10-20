package com.prolean.auth.client;

import com.prolean.common.error.ApiException;
import com.prolean.common.error.ErrorCode;
import com.prolean.common.web.RequestHeaders;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/** Internal client for the user-service profile and auth-context contracts (I1, I2, I3). */
@Component
public class UserServiceClient {

    private final RestClient restClient;
    private final UserServiceProperties properties;

    public UserServiceClient(RestClient.Builder builder, UserServiceProperties properties) {
        this.properties = properties;
        this.restClient = builder.baseUrl(properties.getBaseUrl()).build();
    }

    public AuthContext authContext(UUID userId) {
        try {
            return restClient.get()
                    .uri("/internal/users/{userId}/auth-context", userId)
                    .headers(headers -> internalHeaders(headers))
                    .retrieve()
                    .body(AuthContext.class);
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() == 404) {
                throw new ApiException(ErrorCode.USER_NOT_FOUND);
            }
            throw new ApiException(ErrorCode.INTERNAL_DEPENDENCY_UNAVAILABLE);
        } catch (RestClientException exception) {
            throw new ApiException(ErrorCode.INTERNAL_DEPENDENCY_UNAVAILABLE);
        }
    }

    public Profile profile(UUID userId) {
        try {
            return restClient.get()
                    .uri("/internal/users/{userId}", userId)
                    .headers(headers -> internalHeaders(headers))
                    .retrieve()
                    .body(Profile.class);
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() == 404) {
                throw new ApiException(ErrorCode.USER_NOT_FOUND);
            }
            throw new ApiException(ErrorCode.INTERNAL_DEPENDENCY_UNAVAILABLE);
        } catch (RestClientException exception) {
            throw new ApiException(ErrorCode.INTERNAL_DEPENDENCY_UNAVAILABLE);
        }
    }

    public BootstrapResult bootstrap(String username, String email, String fullName) {
        try {
            return restClient.post()
                    .uri("/internal/users/bootstrap")
                    .headers(headers -> internalHeaders(headers))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new BootstrapRequest(username, email, fullName))
                    .retrieve()
                    .body(BootstrapResult.class);
        } catch (RestClientResponseException exception) {
            throw new ApiException(ErrorCode.INTERNAL_DEPENDENCY_UNAVAILABLE,
                    "Bootstrap profile call failed with status " + exception.getStatusCode().value());
        } catch (RestClientException exception) {
            throw new ApiException(ErrorCode.INTERNAL_DEPENDENCY_UNAVAILABLE);
        }
    }

    private void internalHeaders(org.springframework.http.HttpHeaders headers) {
        headers.set(RequestHeaders.INTERNAL_KEY_ID, properties.getInternalKeyId());
        headers.set(RequestHeaders.INTERNAL_API_KEY, properties.getInternalApiKey());
    }

    public record AuthContext(UUID userId, String username, String fullName, Set<String> roles,
            boolean enabled) {
    }

    public record Profile(UUID id, String username, String fullName, boolean enabled) {
    }

    record BootstrapRequest(String username, String email, String fullName) {
    }

    public record BootstrapResult(UUID userId, String username, Set<String> roles, boolean enabled,
            boolean created) {
    }
}
