package com.prolean.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.prolean.common.error.ApiException;
import com.prolean.common.error.ErrorCode;
import com.prolean.user.domain.AppUser;
import com.prolean.user.domain.Project;
import com.prolean.user.domain.ProjectStatus;
import com.prolean.user.domain.Role;
import com.prolean.user.dto.InternalDtos.BootstrapRequest;
import com.prolean.user.dto.InternalDtos.BootstrapResponse;
import com.prolean.user.repository.ProjectMemberRepository;
import com.prolean.user.repository.ProjectRepository;
import com.prolean.user.repository.RoleRepository;
import com.prolean.user.repository.UserRepository;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InternalUserServiceTest {

    @Mock
    private UserRepository users;
    @Mock
    private RoleRepository roles;
    @Mock
    private ProjectRepository projects;
    @Mock
    private ProjectMemberRepository members;

    private InternalUserService service;
    private final Role adminRole = role("ADMIN");
    private final Role userRole = role("USER");

    @BeforeEach
    void setUp() {
        service = new InternalUserService(users, roles, projects, members);
    }

    private static Role role(String name) {
        Role role = new Role();
        role.setId(UUID.randomUUID());
        role.setName(name);
        return role;
    }

    private static AppUser user(UUID id, boolean enabled, Role... assigned) {
        AppUser user = new AppUser();
        user.setId(id);
        user.setUsername("alice");
        user.setFullName("Alice");
        user.setEmail("alice@example.com");
        user.setEnabled(enabled);
        user.setRoles(Set.of(assigned));
        return user;
    }

    @Test
    void profileAndAuthContextReadUser() {
        UUID id = UUID.randomUUID();
        AppUser user = user(id, true, userRole, adminRole);
        when(users.findById(id)).thenReturn(Optional.of(user));

        assertThat(service.profile(id).id()).isEqualTo(id);
        var context = service.authContext(id);
        assertThat(context.roles()).containsExactly("ADMIN", "USER");
        assertThat(context.enabled()).isTrue();
    }

    @Test
    void profileRejectsMissingUser() {
        UUID id = UUID.randomUUID();
        when(users.findById(id)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.profile(id))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.USER_NOT_FOUND));
    }

    @Test
    void bootstrapPromotesExistingAdminWithoutSaving() {
        AppUser existing = user(UUID.randomUUID(), true, adminRole);
        when(users.findByUsername("admin")).thenReturn(Optional.of(existing));

        BootstrapResponse result = service.bootstrap(
                new BootstrapRequest(" Admin ", "admin@example.com", "Admin"));

        assertThat(result.created()).isFalse();
        assertThat(result.roles()).containsExactly("ADMIN");
        verify(users, never()).save(any());
    }

    @Test
    void bootstrapRepairsExistingNonAdminAndDisabledAccount() {
        UUID id = UUID.randomUUID();
        AppUser existing = user(id, false, userRole);
        when(users.findByUsername("admin")).thenReturn(Optional.of(existing));
        when(roles.findByName("ADMIN")).thenReturn(Optional.of(adminRole));

        BootstrapResponse result = service.bootstrap(
                new BootstrapRequest("admin", "admin@example.com", "Admin"));

        assertThat(result.created()).isFalse();
        assertThat(existing.isEnabled()).isTrue();
        assertThat(existing.hasRole("ADMIN")).isTrue();
        verify(users).save(existing);
    }

    @Test
    void bootstrapRepairsEnabledNonAdmin() {
        AppUser existing = user(UUID.randomUUID(), true, userRole);
        when(users.findByUsername("admin")).thenReturn(Optional.of(existing));
        when(roles.findByName("ADMIN")).thenReturn(Optional.of(adminRole));

        service.bootstrap(new BootstrapRequest("admin", "admin@example.com", "Admin"));

        assertThat(existing.hasRole("ADMIN")).isTrue();
        verify(users).save(existing);
    }

    @Test
    void bootstrapCreatesNewAdmin() {
        when(users.findByUsername("admin")).thenReturn(Optional.empty());
        when(users.existsByEmail("admin@example.com")).thenReturn(false);
        when(roles.findByName("ADMIN")).thenReturn(Optional.of(adminRole));
        when(users.save(any())).thenAnswer(invocation -> {
            AppUser user = invocation.getArgument(0);
            user.setId(UUID.randomUUID());
            return user;
        });

        BootstrapResponse result = service.bootstrap(
                new BootstrapRequest(" Admin ", " ADMIN@example.com ", " Admin "));

        assertThat(result.created()).isTrue();
        assertThat(result.roles()).containsExactly("ADMIN");
        assertThat(result.enabled()).isTrue();
        verify(users).save(any());
    }

    @Test
    void bootstrapRejectsEmailCollisionAndMissingRole() {
        when(users.findByUsername("admin")).thenReturn(Optional.empty());
        when(users.existsByEmail("admin@example.com")).thenReturn(true);
        assertThatThrownBy(() -> service.bootstrap(
                new BootstrapRequest("admin", "admin@example.com", "Admin")))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.EMAIL_TAKEN));

        when(users.existsByEmail("admin@example.com")).thenReturn(false);
        when(roles.findByName("ADMIN")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.bootstrap(
                new BootstrapRequest("admin", "admin@example.com", "Admin")))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.ROLE_NOT_FOUND));
    }

    @Test
    void accessAllowsAdminsAndMembers() {
        UUID projectId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        Project project = new Project();
        project.setId(projectId);
        project.setCode("ALPHA");
        project.setStatus(ProjectStatus.ACTIVE);
        when(projects.findById(projectId)).thenReturn(Optional.of(project));
        when(users.findById(adminId)).thenReturn(Optional.of(user(adminId, true, adminRole)));
        when(users.findById(memberId)).thenReturn(Optional.of(user(memberId, true, userRole)));
        when(members.existsMembership(projectId, adminId)).thenReturn(false);
        when(members.existsMembership(projectId, memberId)).thenReturn(true);

        var adminAccess = service.access(projectId, adminId);
        assertThat(adminAccess.allowed()).isTrue();
        assertThat(adminAccess.admin()).isTrue();
        assertThat(adminAccess.member()).isFalse();

        var memberAccess = service.access(projectId, memberId);
        assertThat(memberAccess.allowed()).isTrue();
        assertThat(memberAccess.member()).isTrue();
        assertThat(memberAccess.admin()).isFalse();
    }

    @Test
    void accessDeniesDisabledUsersEvenWhenTheyWereAdminsOrMembers() {
        UUID projectId = UUID.randomUUID();
        UUID disabledAdminId = UUID.randomUUID();
        UUID disabledMemberId = UUID.randomUUID();
        Project project = new Project();
        project.setId(projectId);
        project.setCode("ALPHA");
        project.setStatus(ProjectStatus.ACTIVE);
        when(projects.findById(projectId)).thenReturn(Optional.of(project));
        when(users.findById(disabledAdminId))
                .thenReturn(Optional.of(user(disabledAdminId, false, adminRole)));
        when(users.findById(disabledMemberId))
                .thenReturn(Optional.of(user(disabledMemberId, false, userRole)));

        var adminAccess = service.access(projectId, disabledAdminId);
        assertThat(adminAccess.allowed()).isFalse();
        assertThat(adminAccess.admin()).isFalse();
        assertThat(adminAccess.member()).isFalse();

        var memberAccess = service.access(projectId, disabledMemberId);
        assertThat(memberAccess.allowed()).isFalse();
        assertThat(memberAccess.admin()).isFalse();
        assertThat(memberAccess.member()).isFalse();
    }

    @Test
    void accessRejectsUnknownProjectOrUser() {
        UUID projectId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(projects.findById(projectId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.access(projectId, userId))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.PROJECT_NOT_FOUND));

        when(projects.findById(projectId)).thenReturn(Optional.of(new Project()));
        when(users.findById(userId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.access(projectId, userId))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.USER_NOT_FOUND));
    }
}
