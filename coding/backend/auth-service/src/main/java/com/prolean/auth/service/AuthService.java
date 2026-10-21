package com.prolean.auth.service;

import com.prolean.auth.client.UserServiceClient;
import com.prolean.auth.config.AuthProperties;
import com.prolean.auth.domain.AuthCredential;
import com.prolean.auth.domain.RefreshToken;
import com.prolean.auth.dto.AuthDtos.LoginRequest;
import com.prolean.auth.dto.AuthDtos.LoginResponse;
import com.prolean.auth.dto.AuthDtos.MeResponse;
import com.prolean.auth.dto.AuthDtos.PasswordChangeRequest;
import com.prolean.auth.dto.AuthDtos.RefreshResponse;
import com.prolean.auth.dto.AuthDtos.UserSummary;
import com.prolean.auth.repository.AuthCredentialRepository;
import com.prolean.auth.repository.RefreshTokenRepository;
import com.prolean.common.error.ApiException;
import com.prolean.common.error.ErrorCode;
import com.prolean.common.security.CurrentUser;
import io.micrometer.core.instrument.Metrics;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final int MAX_FAILED_LOGINS = 5;
    private static final Duration LOCK_DURATION = Duration.ofMinutes(15);
    private static final Pattern PASSWORD_PATTERN = Pattern.compile("^(?=.*[A-Za-z])(?=.*\\d).{8,64}$");
    private static final Set<String> REVOKE_REASONS = Set.of(
            "ROTATED", "LOGOUT", "REUSE", "PASSWORD_CHANGE", "ROLE_CHANGE", "DEACTIVATED");

    private final AuthCredentialRepository credentialRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final UserServiceClient userServiceClient;
    private final TokenService tokenService;
    private final RefreshCookieService cookieService;
    private final AuthProperties properties;
    private final PasswordEncoder passwordEncoder;
    private final String dummyHash;

    public AuthService(AuthCredentialRepository credentialRepository,
            RefreshTokenRepository refreshTokenRepository, UserServiceClient userServiceClient,
            TokenService tokenService, RefreshCookieService cookieService, AuthProperties properties,
            PasswordEncoder passwordEncoder) {
        this.credentialRepository = credentialRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.userServiceClient = userServiceClient;
        this.tokenService = tokenService;
        this.cookieService = cookieService;
        this.properties = properties;
        this.passwordEncoder = passwordEncoder;
        this.dummyHash = passwordEncoder.encode("not-a-real-password");
    }

    @Transactional(noRollbackFor = ApiException.class)
    public LoginResponse login(LoginRequest request, HttpServletResponse response) {
        String username = normalize(request.username());
        Instant now = Instant.now();
        AuthCredential credential = credentialRepository.findByUsernameForUpdate(username).orElse(null);
        if (credential == null) {
            passwordEncoder.matches(request.password(), dummyHash);
            throw new ApiException(ErrorCode.AUTH_INVALID_CREDENTIALS);
        }
        if (credential.getLockedUntil() != null && credential.getLockedUntil().isAfter(now)) {
            throw new ApiException(ErrorCode.AUTH_ACCOUNT_LOCKED);
        }
        if (credential.getLockedUntil() != null) {
            credential.setLockedUntil(null);
            credential.setFailedLoginCount(0);
        }
        if (!passwordEncoder.matches(request.password(), credential.getPasswordHash())) {
            int failures = credential.getFailedLoginCount() + 1;
            credential.setFailedLoginCount(failures);
            if (failures >= MAX_FAILED_LOGINS) {
                credential.setLockedUntil(now.plus(LOCK_DURATION));
                credentialRepository.save(credential);
                throw new ApiException(ErrorCode.AUTH_ACCOUNT_LOCKED);
            }
            credentialRepository.save(credential);
            throw new ApiException(ErrorCode.AUTH_INVALID_CREDENTIALS);
        }

        UserServiceClient.AuthContext context = authContextForLogin(credential.getUserId());
        if (!context.enabled()) {
            throw new ApiException(ErrorCode.AUTH_ACCOUNT_DISABLED);
        }
        credential.setFailedLoginCount(0);
        credential.setLockedUntil(null);
        credentialRepository.save(credential);

        TokenService.IssuedRefreshToken refresh = tokenService.issueRefreshToken(context.userId(), null, null);
        cookieService.set(response, refresh.rawToken());
        String accessToken = tokenService.issueAccessToken(context.userId(), context.username(),
                context.fullName(), context.roles());
        return new LoginResponse(accessToken, "Bearer", tokenService.accessTokenTtlSeconds(),
                new UserSummary(context.userId(), context.username(), context.fullName(), context.roles()));
    }

    @Transactional(noRollbackFor = ApiException.class)
    public RefreshResponse refresh(String rawRefreshToken, HttpServletResponse response) {
        try {
            return refreshInternal(rawRefreshToken, response);
        } catch (RuntimeException exception) {
            // Never leave a token the server has rejected in the browser cookie jar.
            cookieService.clear(response);
            throw exception;
        }
    }

    private RefreshResponse refreshInternal(String rawRefreshToken, HttpServletResponse response) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new ApiException(ErrorCode.AUTH_REFRESH_INVALID);
        }
        Instant now = Instant.now();
        RefreshToken token = refreshTokenRepository.findByTokenHash(tokenService.hash(rawRefreshToken))
                .orElseThrow(() -> new ApiException(ErrorCode.AUTH_REFRESH_INVALID));
        if (token.getRevokedAt() != null) {
            Metrics.counter("prolean.auth.refresh.reuse", "service", "auth-service").increment();
            refreshTokenRepository.revokeFamily(token.getFamilyId(), now, "REUSE");
            throw new ApiException(ErrorCode.AUTH_REFRESH_REUSED);
        }
        if (!token.getExpiresAt().isAfter(now)) {
            throw new ApiException(ErrorCode.AUTH_REFRESH_INVALID);
        }

        UserServiceClient.AuthContext context;
        try {
            context = userServiceClient.authContext(token.getUserId());
        } catch (ApiException exception) {
            if (exception.code() == ErrorCode.USER_NOT_FOUND) {
                refreshTokenRepository.revokeFamily(token.getFamilyId(), now, "DEACTIVATED");
                throw new ApiException(ErrorCode.AUTH_REFRESH_INVALID);
            }
            throw exception;
        }
        if (!context.enabled()) {
            refreshTokenRepository.revokeFamily(token.getFamilyId(), now, "DEACTIVATED");
            throw new ApiException(ErrorCode.AUTH_ACCOUNT_DISABLED);
        }

        UUID replacementId = UUID.randomUUID();
        int rotated = refreshTokenRepository.markRotated(token.getId(), now, replacementId);
        if (rotated == 0) {
            Metrics.counter("prolean.auth.refresh.reuse", "service", "auth-service").increment();
            refreshTokenRepository.revokeFamily(token.getFamilyId(), now, "REUSE");
            throw new ApiException(ErrorCode.AUTH_REFRESH_REUSED);
        }
        TokenService.IssuedRefreshToken replacement = tokenService.issueRefreshToken(
                replacementId, token.getUserId(), token.getFamilyId(), token.getId());
        cookieService.set(response, replacement.rawToken());
        String accessToken = tokenService.issueAccessToken(context.userId(), context.username(),
                context.fullName(), context.roles());
        return new RefreshResponse(accessToken, "Bearer", tokenService.accessTokenTtlSeconds());
    }

    @Transactional
    public void logout(String rawRefreshToken, HttpServletResponse response) {
        if (rawRefreshToken != null && !rawRefreshToken.isBlank()) {
            refreshTokenRepository.findByTokenHash(tokenService.hash(rawRefreshToken)).ifPresent(token -> {
                if (token.getRevokedAt() == null) {
                    token.setRevokedAt(Instant.now());
                    token.setRevokedReason("LOGOUT");
                    refreshTokenRepository.save(token);
                }
            });
        }
        cookieService.clear(response);
    }

    public MeResponse me(CurrentUser currentUser) {
        return new MeResponse(currentUser.id(), currentUser.username(), currentUser.fullName(),
                currentUser.roles());
    }

    @Transactional
    public void setPassword(UUID userId, String newPassword) {
        validatePassword(newPassword);
        UserServiceClient.Profile profile = userServiceClient.profile(userId);
        AuthCredential credential = credentialRepository.findById(userId).orElseGet(() -> {
            AuthCredential created = new AuthCredential();
            created.setUserId(profile.id());
            created.setUsername(profile.username());
            created.setPasswordHash("");
            return created;
        });
        credential.setPasswordHash(passwordEncoder.encode(newPassword));
        credential.setPasswordChangedAt(Instant.now());
        credential.setFailedLoginCount(0);
        credential.setLockedUntil(null);
        credentialRepository.save(credential);
        revokeAll(userId, "PASSWORD_CHANGE");
    }

    @Transactional
    public void changePassword(CurrentUser currentUser, PasswordChangeRequest request) {
        AuthCredential credential = credentialRepository.findById(currentUser.id())
                .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND));
        if (!passwordEncoder.matches(request.currentPassword(), credential.getPasswordHash())) {
            throw ApiException.validation("currentPassword", "does not match the current password");
        }
        if (passwordEncoder.matches(request.newPassword(), credential.getPasswordHash())) {
            throw ApiException.validation("newPassword", "must differ from the current password");
        }
        validatePassword(request.newPassword());
        credential.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        credential.setPasswordChangedAt(Instant.now());
        credential.setFailedLoginCount(0);
        credential.setLockedUntil(null);
        credentialRepository.save(credential);
        revokeAll(currentUser.id(), "PASSWORD_CHANGE");
    }

    @Transactional
    public void revokeAll(UUID userId, String reason) {
        int revoked = refreshTokenRepository.revokeAllForUser(userId, Instant.now(), revokeReason(reason));
        log.debug("Revoked {} refresh token(s) for user {} ({})", revoked, userId, reason);
    }

    @Transactional
    public void bootstrapAdmin() {
        if (properties.getBootstrapPassword() == null || properties.getBootstrapPassword().isBlank()) {
            return;
        }
        String username = normalize(properties.getBootstrapUsername());
        if (credentialRepository.existsByUsername(username)) {
            return;
        }
        UserServiceClient.BootstrapResult profile = userServiceClient.bootstrap(username,
                properties.getBootstrapEmail(), properties.getBootstrapFullName());
        AuthCredential credential = new AuthCredential();
        credential.setUserId(profile.userId());
        credential.setUsername(profile.username());
        credential.setPasswordHash(passwordEncoder.encode(properties.getBootstrapPassword()));
        credential.setPasswordChangedAt(Instant.now());
        credentialRepository.save(credential);
        log.info("Bootstrap ADMIN credential created for '{}'", profile.username());
    }

    private UserServiceClient.AuthContext authContextForLogin(UUID userId) {
        try {
            return userServiceClient.authContext(userId);
        } catch (ApiException exception) {
            if (exception.code() == ErrorCode.USER_NOT_FOUND) {
                throw new ApiException(ErrorCode.AUTH_INVALID_CREDENTIALS);
            }
            throw exception;
        }
    }

    private void validatePassword(String password) {
        if (password == null || !PASSWORD_PATTERN.matcher(password).matches()) {
            throw ApiException.validation("newPassword",
                    "must be 8-64 characters and contain at least one letter and one digit");
        }
    }

    private String revokeReason(String reason) {
        String value = reason == null || reason.isBlank()
                ? "DEACTIVATED"
                : reason.trim().toUpperCase(Locale.ROOT);
        return REVOKE_REASONS.contains(value) ? value : "DEACTIVATED";
    }

    private String normalize(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
