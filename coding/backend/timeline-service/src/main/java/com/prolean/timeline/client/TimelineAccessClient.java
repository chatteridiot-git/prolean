package com.prolean.timeline.client;

import com.prolean.common.error.ApiException;
import com.prolean.common.error.ErrorCode;
import com.prolean.common.web.RequestHeaders;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/** Client for the user-service project access internal contract (I4). */
@Component
public class TimelineAccessClient {

    private final RestClient restClient;
    private final TimelineServiceProperties properties;

    public TimelineAccessClient(RestClient.Builder builder, TimelineServiceProperties properties) {
        this.properties = properties;
        this.restClient = builder.baseUrl(properties.getBaseUrl()).build();
    }

    public Access access(UUID projectId, UUID userId) {
        try {
            return restClient.get()
                    .uri("/internal/projects/{projectId}/access?userId={userId}", projectId, userId)
                    .headers(headers -> {
                        headers.set(RequestHeaders.INTERNAL_KEY_ID, properties.getInternalKeyId());
                        headers.set(RequestHeaders.INTERNAL_API_KEY, properties.getInternalApiKey());
                    })
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

    public record Access(UUID projectId, UUID userId, boolean allowed, boolean member, boolean admin,
            String projectStatus, String projectCode) {
    }
}
