package com.prolean.auth;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.prolean.auth.service.AuthService;
import com.prolean.common.web.RequestHeaders;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class AuthFlowIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    static final WireMockServer WIREMOCK = new WireMockServer(WireMockConfiguration.options().dynamicPort());

    static {
        WIREMOCK.start();
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("prolean.services.user.base-url", WIREMOCK::baseUrl);
    }

    @BeforeAll
    static void start() {
        if (!WIREMOCK.isRunning()) WIREMOCK.start();
    }

    @AfterAll
    static void stop() {
        WIREMOCK.stop();
    }

    @AfterEach
    void resetStubs() {
        WIREMOCK.resetAll();
    }

    @Autowired
    TestRestTemplate rest;
    @Autowired
    AuthService authService;
    @Autowired
    ObjectMapper objectMapper;

    private void stubContext(UUID userId, String username, Set<String> roles, boolean enabled) {
        WIREMOCK.stubFor(get(urlPathMatching("/internal/users/" + userId + "/auth-context"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"userId":"%s","username":"%s","fullName":"%s User","roles":%s,"enabled":%s}
                                """.formatted(userId, username, username, toJson(roles), enabled))));
    }

    private void stubProfile(UUID userId, String username, boolean enabled) {
        WIREMOCK.stubFor(get(urlPathEqualTo("/internal/users/" + userId))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"id":"%s","username":"%s","fullName":"%s User","enabled":%s}
                                """.formatted(userId, username, username, enabled))));
    }

    private static String toJson(Set<String> values) {
        return "[" + values.stream().map(value -> "\"" + value + "\"").reduce((a, b) -> a + "," + b)
                .orElse("") + "]";
    }

    private UUID register(String username, String password, Set<String> roles, boolean enabled) {
        UUID userId = UUID.randomUUID();
        stubProfile(userId, username, enabled);
        stubContext(userId, username, roles, enabled);
        authService.setPassword(userId, password);
        return userId;
    }

    private HttpHeaders jsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private ResponseEntity<String> login(String username, String password) {
        return rest.postForEntity("/api/v1/auth/login",
                new HttpEntity<>(Map.of("username", username, "password", password), jsonHeaders()),
                String.class);
    }

    private static String cookieFrom(ResponseEntity<String> response) {
        String setCookie = response.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
        assertThat(setCookie).isNotNull();
        return setCookie.split(";", 2)[0];
    }

    private ResponseEntity<String> withCookie(String path, String cookie) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, cookie);
        return rest.exchange(path, HttpMethod.POST, new HttpEntity<>(headers), String.class);
    }

    private JsonNode body(ResponseEntity<String> response) {
        try {
            return objectMapper.readTree(response.getBody().getBytes(StandardCharsets.UTF_8));
        } catch (Exception exception) {
            throw new AssertionError("Response was not JSON: " + response.getBody(), exception);
        }
    }

    @Test
    void loginMeRefreshRotationAndReuseDetection() {
        register("rotating.user", "Passw0rd1", Set.of("USER"), true);

        ResponseEntity<String> login = login("rotating.user", "Passw0rd1");
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
        String accessToken = body(login).get("accessToken").asText();
        String originalCookie = cookieFrom(login);

        HttpHeaders bearer = jsonHeaders();
        bearer.setBearerAuth(accessToken);
        ResponseEntity<String> me = rest.exchange("/api/v1/auth/me", HttpMethod.GET,
                new HttpEntity<>(bearer), String.class);
        assertThat(me.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(body(me).get("username").asText()).isEqualTo("rotating.user");

        ResponseEntity<String> refresh = withCookie("/api/v1/auth/refresh", originalCookie);
        assertThat(refresh.getStatusCode()).isEqualTo(HttpStatus.OK);
        String rotatedCookie = cookieFrom(refresh);

        ResponseEntity<String> reused = withCookie("/api/v1/auth/refresh", originalCookie);
        assertThat(reused.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(body(reused).get("code").asText()).isEqualTo("AUTH_REFRESH_REUSED");

        ResponseEntity<String> familyRevoked = withCookie("/api/v1/auth/refresh", rotatedCookie);
        assertThat(familyRevoked.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void repeatedFailuresLockAccountForFifteenMinutes() {
        register("locking.user", "Passw0rd1", Set.of("USER"), true);

        for (int attempt = 1; attempt <= 4; attempt++) {
            ResponseEntity<String> failed = login("locking.user", "wrong-password");
            assertThat(failed.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
            assertThat(body(failed).get("code").asText()).isEqualTo("AUTH_INVALID_CREDENTIALS");
        }
        ResponseEntity<String> fifth = login("locking.user", "wrong-password");
        assertThat(fifth.getStatusCode()).isEqualTo(HttpStatus.LOCKED);
        assertThat(body(fifth).get("code").asText()).isEqualTo("AUTH_ACCOUNT_LOCKED");

        ResponseEntity<String> evenCorrect = login("locking.user", "Passw0rd1");
        assertThat(evenCorrect.getStatusCode()).isEqualTo(HttpStatus.LOCKED);

        ResponseEntity<String> unknown = login("no.such.user", "Passw0rd1");
        assertThat(unknown.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void logoutRevokesRefreshToken() {
        register("logout.user", "Passw0rd1", Set.of("USER"), true);
        ResponseEntity<String> login = login("logout.user", "Passw0rd1");
        String cookie = cookieFrom(login);

        ResponseEntity<String> logout = withCookie("/api/v1/auth/logout", cookie);
        assertThat(logout.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<String> refresh = withCookie("/api/v1/auth/refresh", cookie);
        assertThat(refresh.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(body(refresh).get("code").asText()).isEqualTo("AUTH_REFRESH_REUSED");
    }

    @Test
    void adminSetPasswordAndSelfChangePassword() {
        UUID targetId = register("target.user", "Passw0rd1", Set.of("USER"), true);
        register("admin.user", "AdminPass1", Set.of("ADMIN", "USER"), true);
        ResponseEntity<String> adminLogin = login("admin.user", "AdminPass1");
        String adminToken = body(adminLogin).get("accessToken").asText();

        HttpHeaders adminHeaders = jsonHeaders();
        adminHeaders.setBearerAuth(adminToken);
        ResponseEntity<String> setPassword = rest.exchange(
                "/api/v1/auth/users/" + targetId + "/password", HttpMethod.PUT,
                new HttpEntity<>(Map.of("newPassword", "ResetPass2"), adminHeaders), String.class);
        assertThat(setPassword.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<String> targetLogin = login("target.user", "ResetPass2");
        assertThat(targetLogin.getStatusCode()).isEqualTo(HttpStatus.OK);
        String targetToken = body(targetLogin).get("accessToken").asText();

        HttpHeaders targetHeaders = jsonHeaders();
        targetHeaders.setBearerAuth(targetToken);
        ResponseEntity<String> change = rest.exchange("/api/v1/auth/me/password", HttpMethod.PUT,
                new HttpEntity<>(Map.of("currentPassword", "ResetPass2", "newPassword", "FinalPass3"),
                        targetHeaders), String.class);
        assertThat(change.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        assertThat(login("target.user", "ResetPass2").getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(login("target.user", "FinalPass3").getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<String> forbidden = rest.exchange(
                "/api/v1/auth/users/" + targetId + "/password", HttpMethod.PUT,
                new HttpEntity<>(Map.of("newPassword", "OtherPass9"), jsonHeaders()), String.class);
        assertThat(forbidden.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void disabledAccountCannotLoginAndInternalRevokeEndsSessions() {
        UUID disabledId = register("disabled.user", "Passw0rd1", Set.of("USER"), false);
        ResponseEntity<String> disabled = login("disabled.user", "Passw0rd1");
        assertThat(disabled.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(body(disabled).get("code").asText()).isEqualTo("AUTH_ACCOUNT_DISABLED");

        stubContext(disabledId, "disabled.user", Set.of("USER"), true);
        ResponseEntity<String> login = login("disabled.user", "Passw0rd1");
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
        String cookie = cookieFrom(login);

        HttpHeaders internal = jsonHeaders();
        internal.set(RequestHeaders.INTERNAL_KEY_ID, "user-service-2026-10");
        internal.set(RequestHeaders.INTERNAL_API_KEY, "dev-user-service-key");
        ResponseEntity<String> revoke = rest.exchange(
                "/internal/users/" + disabledId + "/revoke-tokens", HttpMethod.POST,
                new HttpEntity<>(Map.of("reason", "DEACTIVATED"), internal), String.class);
        assertThat(revoke.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        assertThat(withCookie("/api/v1/auth/refresh", cookie).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void jwksEndpointPublishesUsableKey() {
        ResponseEntity<String> jwks = rest.getForEntity("/.well-known/jwks.json", String.class);
        assertThat(jwks.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(jwks.getBody()).contains("\"kty\":\"RSA\"").contains("\"kid\"");
    }

    @Test
    void validationErrorsUseProblemDetails() {
        ResponseEntity<String> blank = login("", "");
        assertThat(blank.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(blank.getHeaders().getContentType().toString())
                .isEqualTo(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        assertThat(body(blank).get("code").asText()).isEqualTo("VALIDATION_ERROR");
        assertThat(blank.getBody()).contains("errors");

        List<String> fields = body(blank).get("errors").findValuesAsText("field");
        assertThat(fields).contains("username", "password");
    }
}
