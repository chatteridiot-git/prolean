package com.prolean.task.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

import com.prolean.common.error.ApiException;
import com.prolean.common.error.ErrorCode;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class ProjectAccessClientTest {

    private final TaskServiceProperties properties = new TaskServiceProperties();
    private MockRestServiceServer server;
    private ProjectAccessClient client;

    @BeforeEach
    void setUp() {
        properties.setBaseUrl("http://user-service.test");
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new ProjectAccessClient(builder, properties);
    }

    @Test
    void mapsDownstreamUserNotFoundToUserError() {
        UUID projectId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        server.expect(requestTo("http://user-service.test/internal/projects/" + projectId
                        + "/access?userId=" + userId))
                .andRespond(withStatus(HttpStatus.NOT_FOUND)
                        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                        .body("{\"code\":\"USER_NOT_FOUND\",\"status\":404}"));

        assertThatThrownBy(() -> client.access(projectId, userId))
                .isInstanceOfSatisfying(ApiException.class,
                        error -> assertThat(error.code()).isEqualTo(ErrorCode.USER_NOT_FOUND));
        server.verify();
    }

    @Test
    void mapsUnknownDownstream404ToProjectNotFound() {
        UUID projectId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        server.expect(requestTo("http://user-service.test/internal/projects/" + projectId
                        + "/access?userId=" + userId))
                .andRespond(withStatus(HttpStatus.NOT_FOUND)
                        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                        .body("{\"code\":\"PROJECT_NOT_FOUND\",\"status\":404}"));

        assertThatThrownBy(() -> client.access(projectId, userId))
                .isInstanceOfSatisfying(ApiException.class,
                        error -> assertThat(error.code()).isEqualTo(ErrorCode.PROJECT_NOT_FOUND));
    }

    @Test
    void mapsMalformed404BodyToProjectNotFound() {
        UUID projectId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        server.expect(requestTo("http://user-service.test/internal/projects/" + projectId
                        + "/access?userId=" + userId))
                .andRespond(withStatus(HttpStatus.NOT_FOUND).body("not-json"));

        assertThatThrownBy(() -> client.access(projectId, userId))
                .isInstanceOfSatisfying(ApiException.class,
                        error -> assertThat(error.code()).isEqualTo(ErrorCode.PROJECT_NOT_FOUND));
    }
}
