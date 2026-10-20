package com.prolean.task.client;

import com.prolean.common.error.ApiException;
import com.prolean.common.error.ErrorCode;
import com.prolean.common.web.RequestHeaders;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/** Client for the user-service access and profile internal contracts (I1, I4). */
@Component
public class ProjectAccessClient {

    private final RestClient restClient;
    private final TaskServiceProperties properties;

    public ProjectAccessClient(RestClient.Builder builder, TaskServiceProperties properties) {
        this.properties = properties;
        this.restClient = builder.baseUrl(properties.getBaseUrl()).build();
    }

    public Access access(UUID projectId, UUID userId) {
        try {
            return restClient.get()
                    .uri("/internal/projects/{projectId}/access?userId={userId}", projectId, userId)
                    .headers(headers -> internalHeaders(headers))
                    .retrieve()
                    .body(Access.class);
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() == 404) {
                throw new ApiException(ErrorCode.PROJECT_NOT_FOUND);
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
                throw new ApiException(ErrorCode.PIC_NOT_MEMBER);
            }
            throw new ApiException(ErrorCode.INTERNAL_DEPENDENCY_UNAVAILABLE);
        } catch (RestClientException exception) {
            throw new ApiException(ErrorCode.INTERNAL_DEPENDENCY_UNAVAILABLE);
        }
    }

    private void internalHeaders(org.springframework.http.HttpHeaders headers) {
        headers.set(RequestHeaders.INTERNAL_KEY_ID, properties.getInternalKeyId());
        headers.set(RequestHeaders.INTERNAL_API_KEY, properties.getInternalApiKey());
    }

    public record Access(UUID projectId, UUID userId, boolean allowed, boolean member, boolean admin,
            String projectStatus, String projectCode) {
    }

    public record Profile(UUID id, String username, String fullName, boolean enabled) {
    }
}
