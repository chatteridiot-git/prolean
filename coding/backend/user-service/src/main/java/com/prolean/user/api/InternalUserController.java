package com.prolean.user.api;

import com.prolean.user.dto.InternalDtos.AccessResponse;
import com.prolean.user.dto.InternalDtos.AuthContextResponse;
import com.prolean.user.dto.InternalDtos.BootstrapRequest;
import com.prolean.user.dto.InternalDtos.BootstrapResponse;
import com.prolean.user.dto.InternalDtos.InternalUserResponse;
import com.prolean.user.service.InternalUserService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal")
@PreAuthorize("hasRole('INTERNAL')")
public class InternalUserController {

    private final InternalUserService internalUserService;

    public InternalUserController(InternalUserService internalUserService) {
        this.internalUserService = internalUserService;
    }

    @GetMapping("/users/{userId}")
    public InternalUserResponse profile(@PathVariable UUID userId) {
        return internalUserService.profile(userId);
    }

    @GetMapping("/users/{userId}/auth-context")
    public AuthContextResponse authContext(@PathVariable UUID userId) {
        return internalUserService.authContext(userId);
    }

    @PostMapping("/users/bootstrap")
    public BootstrapResponse bootstrap(@Valid @RequestBody BootstrapRequest request) {
        return internalUserService.bootstrap(request);
    }

    @GetMapping("/projects/{projectId}/access")
    public AccessResponse access(@PathVariable UUID projectId, @RequestParam UUID userId) {
        return internalUserService.access(projectId, userId);
    }
}
