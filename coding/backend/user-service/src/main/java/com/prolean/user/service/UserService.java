package com.prolean.user.service;

import com.prolean.common.api.PageQuery;
import com.prolean.common.api.PageResponse;
import com.prolean.common.error.ApiException;
import com.prolean.common.error.ErrorCode;
import com.prolean.common.security.CurrentUser;
import com.prolean.user.client.AuthServiceClient;
import com.prolean.user.domain.AppUser;
import com.prolean.user.domain.Role;
import com.prolean.user.dto.UserDtos.CreateUserRequest;
import com.prolean.user.dto.UserDtos.RoleResponse;
import com.prolean.user.dto.UserDtos.RolesRequest;
import com.prolean.user.dto.UserDtos.UpdateUserRequest;
import com.prolean.user.dto.UserDtos.UserResponse;
import com.prolean.user.mapper.UserMapper;
import com.prolean.user.repository.RoleRepository;
import com.prolean.user.repository.UserRepository;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class UserService {

    private static final Set<String> ALLOWED_ROLES = Set.of("ADMIN", "USER");
    private static final Set<String> SORT_FIELDS = Set.of("username", "fullName", "email", "enabled", "createdAt");

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final AuthServiceClient authServiceClient;
    private final AdminGuard adminGuard;

    public UserService(UserRepository userRepository, RoleRepository roleRepository,
            AuthServiceClient authServiceClient, AdminGuard adminGuard) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.authServiceClient = authServiceClient;
        this.adminGuard = adminGuard;
    }

    public PageResponse<UserResponse> list(PageQuery query, String q, Boolean enabled, String role) {
        String roleFilter = role == null || role.isBlank() ? null : role.trim().toUpperCase(Locale.ROOT);
        if (roleFilter != null && !ALLOWED_ROLES.contains(roleFilter)) {
            throw ApiException.validation("role", "must be ADMIN or USER");
        }
        Pageable pageable = query.toPageable(SORT_FIELDS, "username,asc");
        Page<AppUser> page = userRepository.search(normalizeNullable(q), enabled, roleFilter, pageable);
        return PageResponse.of(page.map(UserMapper::toResponse).getContent(), page);
    }

    public UserResponse get(UUID id) {
        return UserMapper.toResponse(requireUser(id));
    }

    @Transactional
    public UserResponse create(CreateUserRequest request, CurrentUser actor) {
        String username = normalize(request.username());
        String email = normalize(request.email());
        if (userRepository.existsByUsername(username)) {
            throw new ApiException(ErrorCode.USERNAME_TAKEN);
        }
        if (userRepository.existsByEmail(email)) {
            throw new ApiException(ErrorCode.EMAIL_TAKEN);
        }
        AppUser user = new AppUser();
        user.setUsername(username);
        user.setEmail(email);
        user.setFullName(request.fullName().trim());
        user.setEnabled(true);
        user.setCreatedBy(actor.id());
        user.setUpdatedBy(actor.id());
        user.setRoles(resolveRoles(request.roles()));
        return UserMapper.toResponse(userRepository.save(user));
    }

    @Transactional
    public UserResponse update(UUID id, UpdateUserRequest request, CurrentUser actor) {
        AppUser user = requireUser(id);
        String email = normalize(request.email());
        if (userRepository.existsByEmailAndIdNot(email, id)) {
            throw new ApiException(ErrorCode.EMAIL_TAKEN);
        }
        user.setEmail(email);
        user.setFullName(request.fullName().trim());
        user.setUpdatedBy(actor.id());
        return UserMapper.toResponse(userRepository.save(user));
    }

    @Transactional
    public UserResponse changeStatus(UUID id, boolean enabled, CurrentUser actor) {
        AppUser user = requireUser(id);
        if (!enabled && user.getId().equals(actor.id())) {
            throw new ApiException(ErrorCode.SELF_DEACTIVATION);
        }
        boolean wasEnabled = user.isEnabled();
        if (!enabled && wasEnabled && user.hasRole("ADMIN")) {
            adminGuard.lockAdminChanges();
            if (userRepository.countActiveAdmins() <= 1) {
                throw new ApiException(ErrorCode.LAST_ADMIN);
            }
        }
        user.setEnabled(enabled);
        user.setUpdatedBy(actor.id());
        UserResponse response = UserMapper.toResponse(userRepository.save(user));
        if (wasEnabled && !enabled) {
            authServiceClient.revokeTokens(id, "DEACTIVATED");
        }
        return response;
    }

    @Transactional
    public UserResponse replaceRoles(UUID id, RolesRequest request, CurrentUser actor) {
        AppUser user = requireUser(id);
        Set<String> requested = request.roles().stream()
                .map(value -> value.trim().toUpperCase(Locale.ROOT))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<Role> resolved = resolveRoles(requested);
        Set<String> before = roleNames(user.getRoles());
        boolean removesAdmin = before.contains("ADMIN") && !requested.contains("ADMIN");
        if (removesAdmin && user.isEnabled()) {
            adminGuard.lockAdminChanges();
            if (userRepository.countActiveAdmins() <= 1) {
                throw new ApiException(ErrorCode.LAST_ADMIN);
            }
        }
        user.setRoles(resolved);
        user.setUpdatedBy(actor.id());
        UserResponse response = UserMapper.toResponse(userRepository.save(user));
        if (!before.equals(roleNames(resolved))) {
            authServiceClient.revokeTokens(id, "ROLE_CHANGE");
        }
        return response;
    }

    public UserResponse me(CurrentUser currentUser) {
        return get(currentUser.id());
    }

    public List<RoleResponse> listRoles() {
        return roleRepository.findAllByOrderByNameAsc().stream().map(UserMapper::toResponse).toList();
    }

    public AppUser requireUser(UUID id) {
        return userRepository.findById(id).orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND));
    }

    private Set<Role> resolveRoles(Set<String> requestedRoles) {
        Set<String> names = requestedRoles == null || requestedRoles.isEmpty()
                ? Set.of("USER")
                : requestedRoles.stream()
                        .map(value -> value.trim().toUpperCase(Locale.ROOT))
                        .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<Role> roles = new LinkedHashSet<>();
        for (String name : names) {
            if (!ALLOWED_ROLES.contains(name)) {
                throw new ApiException(ErrorCode.ROLE_NOT_FOUND, "Unknown role: " + name);
            }
            roles.add(roleRepository.findByName(name)
                    .orElseThrow(() -> new ApiException(ErrorCode.ROLE_NOT_FOUND, "Unknown role: " + name)));
        }
        return roles;
    }

    private Set<String> roleNames(Set<Role> roles) {
        return roles.stream().map(Role::getName).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private String normalize(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeNullable(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
