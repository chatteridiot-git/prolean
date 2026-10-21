package com.prolean.user.service;

import com.prolean.common.error.ApiException;
import com.prolean.common.error.ErrorCode;
import com.prolean.user.domain.AppUser;
import com.prolean.user.domain.Project;
import com.prolean.user.domain.Role;
import com.prolean.user.dto.InternalDtos.AccessResponse;
import com.prolean.user.dto.InternalDtos.AuthContextResponse;
import com.prolean.user.dto.InternalDtos.BootstrapRequest;
import com.prolean.user.dto.InternalDtos.BootstrapResponse;
import com.prolean.user.dto.InternalDtos.InternalUserResponse;
import com.prolean.user.repository.ProjectMemberRepository;
import com.prolean.user.repository.ProjectRepository;
import com.prolean.user.repository.RoleRepository;
import com.prolean.user.repository.UserRepository;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class InternalUserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository memberRepository;

    public InternalUserService(UserRepository userRepository, RoleRepository roleRepository,
            ProjectRepository projectRepository, ProjectMemberRepository memberRepository) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.projectRepository = projectRepository;
        this.memberRepository = memberRepository;
    }

    public InternalUserResponse profile(UUID userId) {
        return toInternal(requireUser(userId));
    }

    public AuthContextResponse authContext(UUID userId) {
        AppUser user = requireUser(userId);
        return new AuthContextResponse(user.getId(), user.getUsername(), user.getFullName(),
                roleNames(user), user.isEnabled());
    }

    @Transactional
    public BootstrapResponse bootstrap(BootstrapRequest request) {
        String username = normalize(request.username());
        String email = normalize(request.email());
        var existing = userRepository.findByUsername(username);
        if (existing.isPresent()) {
            AppUser user = existing.get();
            boolean changed = false;
            if (!user.hasRole("ADMIN")) {
                Set<Role> roles = new LinkedHashSet<>(user.getRoles());
                roles.add(requireRole("ADMIN"));
                user.setRoles(roles);
                changed = true;
            }
            if (!user.isEnabled()) {
                user.setEnabled(true);
                changed = true;
            }
            if (changed) {
                userRepository.save(user);
            }
            return new BootstrapResponse(user.getId(), user.getUsername(), roleNames(user), true, false);
        }
        if (userRepository.existsByEmail(email)) {
            throw new ApiException(ErrorCode.EMAIL_TAKEN);
        }
        AppUser user = new AppUser();
        user.setUsername(username);
        user.setEmail(email);
        user.setFullName(request.fullName().trim());
        user.setEnabled(true);
        user.setRoles(Set.of(requireRole("ADMIN")));
        user = userRepository.save(user);
        return new BootstrapResponse(user.getId(), user.getUsername(), roleNames(user), true, true);
    }

    public AccessResponse access(UUID projectId, UUID userId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ApiException(ErrorCode.PROJECT_NOT_FOUND));
        AppUser user = requireUser(userId);
        boolean active = user.isEnabled();
        boolean admin = active && user.hasRole("ADMIN");
        boolean member = active && memberRepository.existsMembership(projectId, userId);
        return new AccessResponse(project.getId(), userId, active && (admin || member), member, admin,
                project.getStatus(), project.getCode());
    }

    private AppUser requireUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND));
    }

    private Role requireRole(String name) {
        return roleRepository.findByName(name)
                .orElseThrow(() -> new ApiException(ErrorCode.ROLE_NOT_FOUND, "Unknown role: " + name));
    }

    private Set<String> roleNames(AppUser user) {
        return user.getRoles().stream()
                .map(Role::getName)
                .sorted()
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private InternalUserResponse toInternal(AppUser user) {
        return new InternalUserResponse(user.getId(), user.getUsername(), user.getFullName(), user.isEnabled());
    }

    private String normalize(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
