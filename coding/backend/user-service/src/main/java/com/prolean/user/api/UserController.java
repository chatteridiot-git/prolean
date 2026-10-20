package com.prolean.user.api;

import com.prolean.common.api.PageQuery;
import com.prolean.common.api.PageResponse;
import com.prolean.common.security.CurrentUser;
import com.prolean.user.dto.UserDtos.CreateUserRequest;
import com.prolean.user.dto.UserDtos.RolesRequest;
import com.prolean.user.dto.UserDtos.StatusRequest;
import com.prolean.user.dto.UserDtos.UpdateUserRequest;
import com.prolean.user.dto.UserDtos.UserResponse;
import com.prolean.user.service.UserService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
@PreAuthorize("hasRole('ADMIN')")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public PageResponse<UserResponse> list(@RequestParam(required = false) String q,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort) {
        return userService.list(PageQuery.of(page, size, sort), q, enabled, role);
    }

    @PostMapping
    public ResponseEntity<UserResponse> create(@Valid @RequestBody CreateUserRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        UserResponse created = userService.create(request, CurrentUser.from(jwt));
        return ResponseEntity.created(URI.create("/api/v1/users/" + created.id())).body(created);
    }

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public UserResponse me(@AuthenticationPrincipal Jwt jwt) {
        return userService.me(CurrentUser.from(jwt));
    }

    @GetMapping("/{id}")
    public UserResponse get(@PathVariable UUID id) {
        return userService.get(id);
    }

    @PutMapping("/{id}")
    public UserResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateUserRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return userService.update(id, request, CurrentUser.from(jwt));
    }

    @PatchMapping("/{id}/status")
    public UserResponse changeStatus(@PathVariable UUID id, @Valid @RequestBody StatusRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return userService.changeStatus(id, request.enabled(), CurrentUser.from(jwt));
    }

    @PutMapping("/{id}/roles")
    public UserResponse replaceRoles(@PathVariable UUID id, @Valid @RequestBody RolesRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return userService.replaceRoles(id, request, CurrentUser.from(jwt));
    }
}
