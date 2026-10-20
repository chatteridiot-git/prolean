package com.prolean.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.prolean.auth.client.UserServiceClient;
import com.prolean.auth.config.AuthProperties;
import com.prolean.auth.domain.AuthCredential;
import com.prolean.auth.domain.RefreshToken;
import com.prolean.auth.dto.AuthDtos.LoginRequest;
import com.prolean.auth.dto.AuthDtos.LoginResponse;
import com.prolean.auth.dto.AuthDtos.PasswordChangeRequest;
import com.prolean.auth.dto.AuthDtos.RefreshResponse;
import com.prolean.auth.repository.AuthCredentialRepository;
import com.prolean.auth.repository.RefreshTokenRepository;
import com.prolean.common.error.ApiException;
import com.prolean.common.error.ErrorCode;
import com.prolean.common.security.CurrentUser;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private AuthCredentialRepository credentials;
    @Mock
    private RefreshTokenRepository refreshTokens;
    @Mock
    private UserServiceClient userClient;
    @Mock
    private TokenService tokenService;
    @Mock
    private RefreshCookieService cookieService;
    @Mock
    private PasswordEncoder passwordEncoder;

    private AuthProperties properties;
    private AuthService service;
    private HttpServletResponse response;
    private final UUID userId = UUID.randomUUID();
    private final UUID familyId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        properties = new AuthProperties();
        when(passwordEncoder.encode("not-a-real-password")).thenReturn("dummy-hash");
        service = new AuthService(credentials, refreshTokens, userClient, tokenService,
                cookieService, properties, passwordEncoder);
        response = mock(HttpServletResponse.class);
    }

    private AuthCredential credential() {
        AuthCredential credential = new AuthCredential();
        credential.setUserId(userId);
        credential.setUsername("alice");
        credential.setPasswordHash("hash");
        return credential;
    }

    private UserServiceClient.AuthContext context(boolean enabled) {
        return new UserServiceClient.AuthContext(userId, "alice", "Alice", Set.of("USER"), enabled);
    }

    private void successfulTokenIssue() {
        when(tokenService.issueRefreshToken(userId, null, null))
                .thenReturn(new TokenService.IssuedRefreshToken("raw-refresh", UUID.randomUUID(), familyId));
        when(tokenService.issueAccessToken(eq(userId), eq("alice"), eq("Alice"), any()))
                .thenReturn("access-token");
        when(tokenService.accessTokenTtlSeconds()).thenReturn(900L);
    }

    @Test
    void loginSucceedsAndResetsFailures() {
        AuthCredential credential = credential();
        credential.setFailedLoginCount(2);
        credential.setLockedUntil(Instant.now().minusSeconds(1));
        when(credentials.findByUsernameForUpdate("alice")).thenReturn(Optional.of(credential));
        when(passwordEncoder.matches("Secret123", "hash")).thenReturn(true);
        when(userClient.authContext(userId)).thenReturn(context(true));
        successfulTokenIssue();

        LoginResponse result = service.login(new LoginRequest(" Alice ", "Secret123"), response);

        assertThat(result.accessToken()).isEqualTo("access-token");
        assertThat(result.tokenType()).isEqualTo("Bearer");
        assertThat(result.expiresIn()).isEqualTo(900);
        assertThat(result.user().id()).isEqualTo(userId);
        assertThat(result.user().roles()).containsExactly("USER");
        assertThat(credential.getFailedLoginCount()).isZero();
        assertThat(credential.getLockedUntil()).isNull();
        verify(credentials).save(credential);
        verify(cookieService).set(response, "raw-refresh");
    }

    @Test
    void loginRejectsUnknownUsernameWithDummyHash() {
        when(credentials.findByUsernameForUpdate("nobody")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.login(new LoginRequest("nobody", "bad"), response))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.AUTH_INVALID_CREDENTIALS));
        verify(passwordEncoder).matches("bad", "dummy-hash");
        verifyNoInteractions(cookieService);
    }

    @Test
    void loginRejectsLockedAccount() {
        AuthCredential credential = credential();
        credential.setLockedUntil(Instant.now().plusSeconds(60));
        when(credentials.findByUsernameForUpdate("alice")).thenReturn(Optional.of(credential));

        assertThatThrownBy(() -> service.login(new LoginRequest("alice", "Secret123"), response))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.AUTH_ACCOUNT_LOCKED));
        verify(credentials, never()).save(any());
    }

    @Test
    void loginIncrementsFailureAndLocksOnFifthAttempt() {
        AuthCredential credential = credential();
        credential.setFailedLoginCount(4);
        when(credentials.findByUsernameForUpdate("alice")).thenReturn(Optional.of(credential));
        when(passwordEncoder.matches("bad", "hash")).thenReturn(false);

        assertThatThrownBy(() -> service.login(new LoginRequest("alice", "bad"), response))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.AUTH_ACCOUNT_LOCKED));
        assertThat(credential.getFailedLoginCount()).isEqualTo(5);
        assertThat(credential.getLockedUntil()).isAfter(Instant.now());
        verify(credentials).save(credential);
    }

    @Test
    void loginIncrementsFailureBelowThreshold() {
        AuthCredential credential = credential();
        when(credentials.findByUsernameForUpdate("alice")).thenReturn(Optional.of(credential));
        when(passwordEncoder.matches("bad", "hash")).thenReturn(false);

        assertThatThrownBy(() -> service.login(new LoginRequest("alice", "bad"), response))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.AUTH_INVALID_CREDENTIALS));
        assertThat(credential.getFailedLoginCount()).isOne();
        verify(credentials).save(credential);
    }

    @Test
    void loginRejectsDisabledAccount() {
        when(credentials.findByUsernameForUpdate("alice")).thenReturn(Optional.of(credential()));
        when(passwordEncoder.matches("Secret123", "hash")).thenReturn(true);
        when(userClient.authContext(userId)).thenReturn(context(false));

        assertThatThrownBy(() -> service.login(new LoginRequest("alice", "Secret123"), response))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.AUTH_ACCOUNT_DISABLED));
    }

    @Test
    void loginMapsMissingAuthProfileToInvalidCredentials() {
        when(credentials.findByUsernameForUpdate("alice")).thenReturn(Optional.of(credential()));
        when(passwordEncoder.matches("Secret123", "hash")).thenReturn(true);
        when(userClient.authContext(userId)).thenThrow(new ApiException(ErrorCode.USER_NOT_FOUND));

        assertThatThrownBy(() -> service.login(new LoginRequest("alice", "Secret123"), response))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.AUTH_INVALID_CREDENTIALS));
    }

    @Test
    void loginPropagatesDependencyFailure() {
        when(credentials.findByUsernameForUpdate("alice")).thenReturn(Optional.of(credential()));
        when(passwordEncoder.matches("Secret123", "hash")).thenReturn(true);
        when(userClient.authContext(userId))
                .thenThrow(new ApiException(ErrorCode.INTERNAL_DEPENDENCY_UNAVAILABLE));

        assertThatThrownBy(() -> service.login(new LoginRequest("alice", "Secret123"), response))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.INTERNAL_DEPENDENCY_UNAVAILABLE));
    }

    @Test
    void refreshRejectsMissingToken() {
        assertThatThrownBy(() -> service.refresh(" ", response))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.AUTH_REFRESH_INVALID));
        verifyNoInteractions(refreshTokens);
    }

    @Test
    void refreshRejectsUnknownToken() {
        when(tokenService.hash("raw")).thenReturn("hash");
        when(refreshTokens.findByTokenHash("hash")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.refresh("raw", response))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.AUTH_REFRESH_INVALID));
    }

    @Test
    void refreshDetectsFamilyReuseAndRevokesFamily() {
        RefreshToken token = new RefreshToken();
        token.setId(UUID.randomUUID());
        token.setFamilyId(familyId);
        token.setUserId(userId);
        token.setExpiresAt(Instant.now().plusSeconds(60));
        token.setRevokedAt(Instant.now());
        when(tokenService.hash("raw")).thenReturn("hash");
        when(refreshTokens.findByTokenHash("hash")).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> service.refresh("raw", response))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.AUTH_REFRESH_REUSED));
        verify(refreshTokens).revokeFamily(eq(familyId), any(), eq("REUSE"));
    }

    @Test
    void refreshRejectsExpiredToken() {
        RefreshToken token = new RefreshToken();
        token.setId(UUID.randomUUID());
        token.setFamilyId(familyId);
        token.setUserId(userId);
        token.setExpiresAt(Instant.now().minusSeconds(1));
        when(tokenService.hash("raw")).thenReturn("hash");
        when(refreshTokens.findByTokenHash("hash")).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> service.refresh("raw", response))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.AUTH_REFRESH_INVALID));
    }

    @Test
    void refreshRevokesFamilyWhenProfileIsGone() {
        RefreshToken token = new RefreshToken();
        token.setId(UUID.randomUUID());
        token.setFamilyId(familyId);
        token.setUserId(userId);
        token.setExpiresAt(Instant.now().plusSeconds(60));
        when(tokenService.hash("raw")).thenReturn("hash");
        when(refreshTokens.findByTokenHash("hash")).thenReturn(Optional.of(token));
        when(userClient.authContext(userId)).thenThrow(new ApiException(ErrorCode.USER_NOT_FOUND));

        assertThatThrownBy(() -> service.refresh("raw", response))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.AUTH_REFRESH_INVALID));
        verify(refreshTokens).revokeFamily(eq(familyId), any(), eq("DEACTIVATED"));
    }

    @Test
    void refreshRejectsDisabledAccountAndRevokesFamily() {
        RefreshToken token = new RefreshToken();
        token.setId(UUID.randomUUID());
        token.setFamilyId(familyId);
        token.setUserId(userId);
        token.setExpiresAt(Instant.now().plusSeconds(60));
        when(tokenService.hash("raw")).thenReturn("hash");
        when(refreshTokens.findByTokenHash("hash")).thenReturn(Optional.of(token));
        when(userClient.authContext(userId)).thenReturn(context(false));

        assertThatThrownBy(() -> service.refresh("raw", response))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.AUTH_ACCOUNT_DISABLED));
        verify(refreshTokens).revokeFamily(eq(familyId), any(), eq("DEACTIVATED"));
    }

    @Test
    void refreshPropagatesDependencyFailure() {
        RefreshToken token = new RefreshToken();
        token.setId(UUID.randomUUID());
        token.setFamilyId(familyId);
        token.setUserId(userId);
        token.setExpiresAt(Instant.now().plusSeconds(60));
        when(tokenService.hash("raw")).thenReturn("hash");
        when(refreshTokens.findByTokenHash("hash")).thenReturn(Optional.of(token));
        when(userClient.authContext(userId))
                .thenThrow(new ApiException(ErrorCode.INTERNAL_DEPENDENCY_UNAVAILABLE));

        assertThatThrownBy(() -> service.refresh("raw", response))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.INTERNAL_DEPENDENCY_UNAVAILABLE));
    }

    @Test
    void refreshRejectsConcurrentRotation() {
        RefreshToken token = new RefreshToken();
        token.setId(UUID.randomUUID());
        token.setFamilyId(familyId);
        token.setUserId(userId);
        token.setExpiresAt(Instant.now().plusSeconds(60));
        when(tokenService.hash("raw")).thenReturn("hash");
        when(refreshTokens.findByTokenHash("hash")).thenReturn(Optional.of(token));
        when(userClient.authContext(userId)).thenReturn(context(true));
        when(refreshTokens.markRotated(eq(token.getId()), any(), any())).thenReturn(0);

        assertThatThrownBy(() -> service.refresh("raw", response))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.AUTH_REFRESH_REUSED));
        verify(refreshTokens).revokeFamily(eq(familyId), any(), eq("REUSE"));
    }

    @Test
    void refreshRotatesAndReturnsNewAccessToken() {
        RefreshToken token = new RefreshToken();
        token.setId(UUID.randomUUID());
        token.setFamilyId(familyId);
        token.setUserId(userId);
        token.setExpiresAt(Instant.now().plusSeconds(60));
        when(tokenService.hash("raw")).thenReturn("hash");
        when(refreshTokens.findByTokenHash("hash")).thenReturn(Optional.of(token));
        when(userClient.authContext(userId)).thenReturn(context(true));
        when(refreshTokens.markRotated(eq(token.getId()), any(), any())).thenReturn(1);
        when(tokenService.issueRefreshToken(any(), eq(userId), eq(familyId), eq(token.getId())))
                .thenReturn(new TokenService.IssuedRefreshToken("new-raw", UUID.randomUUID(), familyId));
        when(tokenService.issueAccessToken(eq(userId), eq("alice"), eq("Alice"), any()))
                .thenReturn("new-access");
        when(tokenService.accessTokenTtlSeconds()).thenReturn(900L);

        RefreshResponse result = service.refresh("raw", response);

        assertThat(result.accessToken()).isEqualTo("new-access");
        verify(cookieService).set(response, "new-raw");
    }

    @Test
    void logoutClearsCookieAndRevokesKnownToken() {
        RefreshToken token = new RefreshToken();
        token.setId(UUID.randomUUID());
        when(tokenService.hash("raw")).thenReturn("hash");
        when(refreshTokens.findByTokenHash("hash")).thenReturn(Optional.of(token));

        service.logout("raw", response);

        assertThat(token.getRevokedReason()).isEqualTo("LOGOUT");
        assertThat(token.getRevokedAt()).isNotNull();
        verify(refreshTokens).save(token);
        verify(cookieService).clear(response);
    }

    @Test
    void logoutIsIdempotentForBlankUnknownAndAlreadyRevokedTokens() {
        service.logout(null, response);
        service.logout(" ", response);
        verify(cookieService, org.mockito.Mockito.times(2)).clear(response);

        when(tokenService.hash("unknown")).thenReturn("unknown-hash");
        when(refreshTokens.findByTokenHash("unknown-hash")).thenReturn(Optional.empty());
        service.logout("unknown", response);
        verify(cookieService, org.mockito.Mockito.times(3)).clear(response);

        RefreshToken revoked = new RefreshToken();
        revoked.setRevokedAt(Instant.now());
        when(tokenService.hash("revoked")).thenReturn("revoked-hash");
        when(refreshTokens.findByTokenHash("revoked-hash")).thenReturn(Optional.of(revoked));
        service.logout("revoked", response);
        verify(refreshTokens, never()).save(revoked);
    }

    @Test
    void meReturnsTokenClaims() {
        var result = service.me(new CurrentUser(userId, "alice", "Alice", Set.of("ADMIN")));
        assertThat(result.id()).isEqualTo(userId);
        assertThat(result.roles()).containsExactly("ADMIN");
    }

    @Test
    void setPasswordValidatesAndCreatesMissingCredential() {
        when(userClient.profile(userId))
                .thenReturn(new UserServiceClient.Profile(userId, "alice", "Alice", true));
        when(credentials.findById(userId)).thenReturn(Optional.empty());
        when(passwordEncoder.encode("NewPass123")).thenReturn("new-hash");

        service.setPassword(userId, "NewPass123");

        ArgumentCaptor<AuthCredential> captor = ArgumentCaptor.forClass(AuthCredential.class);
        verify(credentials).save(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(userId);
        assertThat(captor.getValue().getUsername()).isEqualTo("alice");
        assertThat(captor.getValue().getPasswordHash()).isEqualTo("new-hash");
        verify(refreshTokens).revokeAllForUser(eq(userId), any(), eq("PASSWORD_CHANGE"));
    }

    @Test
    void setPasswordUpdatesExistingCredential() {
        AuthCredential credential = credential();
        when(userClient.profile(userId))
                .thenReturn(new UserServiceClient.Profile(userId, "alice", "Alice", true));
        when(credentials.findById(userId)).thenReturn(Optional.of(credential));
        when(passwordEncoder.encode("NewPass123")).thenReturn("new-hash");

        service.setPassword(userId, "NewPass123");

        assertThat(credential.getPasswordHash()).isEqualTo("new-hash");
        verify(credentials).save(credential);
    }

    @Test
    void setPasswordRejectsWeakPassword() {
        assertThatThrownBy(() -> service.setPassword(userId, "short"))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.VALIDATION_ERROR));
        verifyNoInteractions(userClient);
    }

    @Test
    void changePasswordVerifiesCurrentAndNewPasswords() {
        AuthCredential credential = credential();
        when(credentials.findById(userId)).thenReturn(Optional.of(credential));
        when(passwordEncoder.matches("old", "hash")).thenReturn(false);

        assertThatThrownBy(() -> service.changePassword(
                new CurrentUser(userId, "alice", "Alice", Set.of()), new PasswordChangeRequest("old", "NewPass123")))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.VALIDATION_ERROR));
    }

    @Test
    void changePasswordRejectsReusedPassword() {
        AuthCredential credential = credential();
        when(credentials.findById(userId)).thenReturn(Optional.of(credential));
        when(passwordEncoder.matches("old", "hash")).thenReturn(true);
        when(passwordEncoder.matches("NewPass123", "hash")).thenReturn(true);

        assertThatThrownBy(() -> service.changePassword(
                new CurrentUser(userId, "alice", "Alice", Set.of()),
                new PasswordChangeRequest("old", "NewPass123")))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.VALIDATION_ERROR));
    }

    @Test
    void changePasswordRejectsInvalidNewPassword() {
        AuthCredential credential = credential();
        when(credentials.findById(userId)).thenReturn(Optional.of(credential));
        when(passwordEncoder.matches("old", "hash")).thenReturn(true);
        when(passwordEncoder.matches("weak", "hash")).thenReturn(false);

        assertThatThrownBy(() -> service.changePassword(
                new CurrentUser(userId, "alice", "Alice", Set.of()),
                new PasswordChangeRequest("old", "weak")))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.VALIDATION_ERROR));
    }

    @Test
    void changePasswordUpdatesAndRevokesTokens() {
        AuthCredential credential = credential();
        when(credentials.findById(userId)).thenReturn(Optional.of(credential));
        when(passwordEncoder.matches("old", "hash")).thenReturn(true);
        when(passwordEncoder.matches("NewPass123", "hash")).thenReturn(false);
        when(passwordEncoder.encode("NewPass123")).thenReturn("new-hash");

        service.changePassword(new CurrentUser(userId, "alice", "Alice", Set.of()),
                new PasswordChangeRequest("old", "NewPass123"));

        assertThat(credential.getPasswordHash()).isEqualTo("new-hash");
        verify(credentials).save(credential);
        verify(refreshTokens).revokeAllForUser(eq(userId), any(), eq("PASSWORD_CHANGE"));
    }

    @Test
    void changePasswordFailsWhenCredentialIsMissing() {
        when(credentials.findById(userId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.changePassword(
                new CurrentUser(userId, "alice", "Alice", Set.of()),
                new PasswordChangeRequest("old", "NewPass123")))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.USER_NOT_FOUND));
    }

    @Test
    void revokeAllNormalizesReasons() {
        when(refreshTokens.revokeAllForUser(eq(userId), any(), any())).thenReturn(2);
        service.revokeAll(userId, null);
        verify(refreshTokens).revokeAllForUser(eq(userId), any(), eq("DEACTIVATED"));
        service.revokeAll(userId, " logout ");
        verify(refreshTokens).revokeAllForUser(eq(userId), any(), eq("LOGOUT"));
        service.revokeAll(userId, "not-a-reason");
        verify(refreshTokens, org.mockito.Mockito.times(2))
                .revokeAllForUser(eq(userId), any(), eq("DEACTIVATED"));
    }

    @Test
    void bootstrapDoesNothingWithoutPassword() {
        service.bootstrapAdmin();
        verifyNoInteractions(userClient);
    }

    @Test
    void bootstrapDoesNothingWhenCredentialExists() {
        properties.setBootstrapPassword("ChangeMe123!");
        when(credentials.existsByUsername("admin")).thenReturn(true);
        service.bootstrapAdmin();
        verifyNoInteractions(userClient);
    }

    @Test
    void bootstrapCreatesAdminCredential() {
        properties.setBootstrapPassword("ChangeMe123!");
        properties.setBootstrapUsername(" Admin ");
        when(credentials.existsByUsername("admin")).thenReturn(false);
        when(userClient.bootstrap("admin", "admin@example.com", "System Administrator"))
                .thenReturn(new UserServiceClient.BootstrapResult(userId, "admin",
                        Set.of("ADMIN"), true, true));
        when(passwordEncoder.encode("ChangeMe123!")).thenReturn("hash");

        service.bootstrapAdmin();

        ArgumentCaptor<AuthCredential> captor = ArgumentCaptor.forClass(AuthCredential.class);
        verify(credentials).save(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(userId);
        assertThat(captor.getValue().getPasswordHash()).isEqualTo("hash");
    }
}
