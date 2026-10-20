package com.prolean.auth.api;

import com.prolean.auth.dto.AuthDtos.RevokeRequest;
import com.prolean.auth.service.AuthService;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal")
@PreAuthorize("hasRole('INTERNAL')")
public class InternalAuthController {

    private final AuthService authService;

    public InternalAuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/users/{userId}/revoke-tokens")
    public ResponseEntity<Void> revokeTokens(@PathVariable UUID userId,
            @RequestBody(required = false) RevokeRequest request) {
        authService.revokeAll(userId, request == null ? null : request.reason());
        return ResponseEntity.noContent().build();
    }
}
