package com.prolean.auth.api;

import com.prolean.auth.dto.AuthDtos.LoginRequest;
import com.prolean.auth.dto.AuthDtos.LoginResponse;
import com.prolean.auth.dto.AuthDtos.MeResponse;
import com.prolean.auth.dto.AuthDtos.PasswordChangeRequest;
import com.prolean.auth.dto.AuthDtos.PasswordSetRequest;
import com.prolean.auth.dto.AuthDtos.RefreshResponse;
import com.prolean.auth.service.AuthService;
import com.prolean.auth.service.RefreshCookieService;
import com.prolean.common.security.CurrentUser;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request, HttpServletResponse response) {
        return authService.login(request, response);
    }

    @PostMapping("/refresh")
    public RefreshResponse refresh(
            @CookieValue(name = RefreshCookieService.COOKIE_NAME, required = false) String refreshToken,
            HttpServletResponse response) {
        return authService.refresh(refreshToken, response);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @CookieValue(name = RefreshCookieService.COOKIE_NAME, required = false) String refreshToken,
            HttpServletResponse response) {
        authService.logout(refreshToken, response);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public MeResponse me(@AuthenticationPrincipal Jwt jwt) {
        return authService.me(CurrentUser.from(jwt));
    }

    @PutMapping("/users/{userId}/password")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> setPassword(@PathVariable UUID userId,
            @Valid @RequestBody PasswordSetRequest request) {
        authService.setPassword(userId, request.newPassword());
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/me/password")
    public ResponseEntity<Void> changePassword(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody PasswordChangeRequest request) {
        authService.changePassword(CurrentUser.from(jwt), request);
        return ResponseEntity.noContent().build();
    }
}
