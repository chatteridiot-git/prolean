package com.prolean.user.mapper;

import com.prolean.user.domain.AppUser;
import com.prolean.user.domain.Role;
import com.prolean.user.dto.UserDtos.RoleResponse;
import com.prolean.user.dto.UserDtos.UserResponse;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

public final class UserMapper {

    private UserMapper() {
    }

    public static UserResponse toResponse(AppUser user) {
        Set<String> roles = user.getRoles().stream()
                .map(Role::getName)
                .sorted()
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getFullName(),
                user.isEnabled(),
                roles,
                user.getCreatedAt(),
                user.getUpdatedAt());
    }

    public static RoleResponse toResponse(Role role) {
        return new RoleResponse(role.getId(), role.getName(), role.getDescription());
    }
}
