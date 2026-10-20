package com.prolean.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.prolean.common.api.PageQuery;
import com.prolean.common.api.PageResponse;
import com.prolean.common.error.ApiException;
import com.prolean.common.error.ErrorCode;
import com.prolean.common.security.CurrentUser;
import com.prolean.user.client.AuthServiceClient;
import com.prolean.user.domain.AppUser;
import com.prolean.user.domain.Role;
import com.prolean.user.dto.UserDtos.CreateUserRequest;
import com.prolean.user.dto.UserDtos.RolesRequest;
import com.prolean.user.dto.UserDtos.UpdateUserRequest;
import com.prolean.user.dto.UserDtos.UserResponse;
import com.prolean.user.repository.RoleRepository;
import com.prolean.user.repository.UserRepository;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository users;
    @Mock
    private RoleRepository roles;
    @Mock
    private AuthServiceClient authClient;
    @Mock
    private AdminGuard adminGuard;

    private UserService service;
    private final UUID actorId = UUID.randomUUID();
    private final CurrentUser admin = new CurrentUser(actorId, "admin", "Admin", Set.of("ADMIN"));
    private final Role adminRole = role("ADMIN");
    private final Role userRole = role("USER");

    @BeforeEach
    void setUp() {
        service = new UserService(users, roles, authClient, adminGuard);
    }

    private static Role role(String name) {
        Role role = new Role();
        role.setId(UUID.randomUUID());
        role.setName(name);
        return role;
    }

    private static AppUser user(UUID id, String username, boolean enabled, Role... assigned) {
        AppUser user = new AppUser();
        user.setId(id);
        user.setUsername(username);
        user.setEmail(username + "@example.com");
        user.setFullName("Name " + username);
        user.setEnabled(enabled);
        user.setRoles(Set.of(assigned));
        return user;
    }

    @Test
    void listAppliesFiltersAndPagination() {
        AppUser user = user(UUID.randomUUID(), "alice", true, userRole);
        when(users.search(any(), any(), any(), any())).thenReturn(new PageImpl<>(List.of(user)));

        PageResponse<UserResponse> result = service.list(PageQuery.of(0, 20, null), " ali ", true, "user");

        assertThat(result.content()).hasSize(1);
        assertThat(result.content().getFirst().username()).isEqualTo("alice");
        verify(users).search(any(), any(), any(), any());
    }

    @Test
    void listRejectsUnknownRoleFilter() {
        assertThatThrownBy(() -> service.list(PageQuery.of(0, 20, null), null, null, "OWNER"))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.VALIDATION_ERROR));
        verifyNoInteractions(users);
    }

    @Test
    void listIgnoresBlankRoleFilter() {
        when(users.search(any(), any(), any(), any())).thenReturn(new PageImpl<>(List.of()));
        assertThat(service.list(PageQuery.of(0, 20, null), " ", null, " ").content()).isEmpty();
    }

    @Test
    void getReturnsUserOrThrows() {
        UUID id = UUID.randomUUID();
        when(users.findById(id)).thenReturn(Optional.of(user(id, "alice", true, userRole)));
        assertThat(service.get(id).id()).isEqualTo(id);

        when(users.findById(id)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.get(id))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.USER_NOT_FOUND));
    }

    @Test
    void createRejectsDuplicateUsernameAndEmail() {
        when(users.existsByUsername("alice")).thenReturn(true);
        assertThatThrownBy(() -> service.create(createRequest("Alice", "alice@example.com", null), admin))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.USERNAME_TAKEN));

        when(users.existsByUsername("bob")).thenReturn(false);
        when(users.existsByEmail("bob@example.com")).thenReturn(true);
        assertThatThrownBy(() -> service.create(createRequest("bob", "BOB@example.com", null), admin))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.EMAIL_TAKEN));
    }

    @Test
    void createDefaultsToUserRoleAndNormalizesFields() {
        when(users.existsByUsername("alice")).thenReturn(false);
        when(users.existsByEmail("alice@example.com")).thenReturn(false);
        when(roles.findByName("USER")).thenReturn(Optional.of(userRole));
        when(users.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        UserResponse result = service.create(
                createRequest(" Alice ", " ALICE@example.com ", null), admin);

        assertThat(result.username()).isEqualTo("alice");
        assertThat(result.email()).isEqualTo("alice@example.com");
        assertThat(result.fullName()).isEqualTo("Alice Person");
        assertThat(result.roles()).containsExactly("USER");
        ArgumentCaptor<AppUser> captor = ArgumentCaptor.forClass(AppUser.class);
        verify(users).save(captor.capture());
        assertThat(captor.getValue().getCreatedBy()).isEqualTo(actorId);
        assertThat(captor.getValue().isEnabled()).isTrue();
    }

    @Test
    void createSupportsExplicitRoles() {
        when(users.existsByUsername("alice")).thenReturn(false);
        when(users.existsByEmail("alice@example.com")).thenReturn(false);
        when(roles.findByName("ADMIN")).thenReturn(Optional.of(adminRole));
        when(roles.findByName("USER")).thenReturn(Optional.of(userRole));
        when(users.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        UserResponse result = service.create(createRequest("alice", "alice@example.com",
                Set.of(" admin ", "USER")), admin);

        assertThat(result.roles()).containsExactly("ADMIN", "USER");
    }

    @Test
    void createRejectsUnknownRole() {
        when(users.existsByUsername("alice")).thenReturn(false);
        when(users.existsByEmail("alice@example.com")).thenReturn(false);
        assertThatThrownBy(() -> service.create(createRequest("alice", "alice@example.com",
                Set.of("OWNER")), admin))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.ROLE_NOT_FOUND));
    }

    @Test
    void createFailsWhenRoleSeedIsMissing() {
        when(users.existsByUsername("alice")).thenReturn(false);
        when(users.existsByEmail("alice@example.com")).thenReturn(false);
        when(roles.findByName("USER")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.create(createRequest("alice", "alice@example.com", null), admin))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.ROLE_NOT_FOUND));
    }

    @Test
    void updateRejectsDuplicateEmailAndUpdatesFields() {
        UUID id = UUID.randomUUID();
        AppUser user = user(id, "alice", true, userRole);
        when(users.findById(id)).thenReturn(Optional.of(user));
        when(users.existsByEmailAndIdNot("new@example.com", id)).thenReturn(true);

        assertThatThrownBy(() -> service.update(id,
                new UpdateUserRequest("NEW@example.com", " New Name "), admin))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.EMAIL_TAKEN));

        when(users.existsByEmailAndIdNot("new@example.com", id)).thenReturn(false);
        when(users.save(user)).thenReturn(user);
        UserResponse result = service.update(id,
                new UpdateUserRequest("NEW@example.com", " New Name "), admin);
        assertThat(result.email()).isEqualTo("new@example.com");
        assertThat(result.fullName()).isEqualTo("New Name");
        verify(users).save(user);
    }

    @Test
    void changeStatusProtectsSelfAndLastAdmin() {
        UUID own = actorId;
        when(users.findById(own)).thenReturn(Optional.of(user(own, "admin", true, adminRole)));
        assertThatThrownBy(() -> service.changeStatus(own, false, admin))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.SELF_DEACTIVATION));

        UUID other = UUID.randomUUID();
        AppUser otherAdmin = user(other, "other", true, adminRole);
        when(users.findById(other)).thenReturn(Optional.of(otherAdmin));
        when(users.countActiveAdmins()).thenReturn(1L);
        assertThatThrownBy(() -> service.changeStatus(other, false, admin))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.LAST_ADMIN));
        verify(adminGuard).lockAdminChanges();
    }

    @Test
    void changeStatusDeactivatesNonLastAdminAndRevokesTokens() {
        UUID other = UUID.randomUUID();
        AppUser otherAdmin = user(other, "other", true, adminRole);
        when(users.findById(other)).thenReturn(Optional.of(otherAdmin));
        when(users.countActiveAdmins()).thenReturn(2L);
        when(users.save(otherAdmin)).thenReturn(otherAdmin);

        UserResponse result = service.changeStatus(other, false, admin);

        assertThat(result.enabled()).isFalse();
        verify(authClient).revokeTokens(other, "DEACTIVATED");
    }

    @Test
    void changeStatusOfRegularUserDoesNotTakeAdminLock() {
        UUID id = UUID.randomUUID();
        AppUser regular = user(id, "alice", true, userRole);
        when(users.findById(id)).thenReturn(Optional.of(regular));
        when(users.save(regular)).thenReturn(regular);

        service.changeStatus(id, false, admin);

        verifyNoInteractions(adminGuard);
        verify(authClient).revokeTokens(id, "DEACTIVATED");
    }

    @Test
    void reEnablingDoesNotRevokeTokens() {
        UUID id = UUID.randomUUID();
        AppUser disabled = user(id, "alice", false, userRole);
        when(users.findById(id)).thenReturn(Optional.of(disabled));
        when(users.save(disabled)).thenReturn(disabled);

        service.changeStatus(id, true, admin);

        verifyNoInteractions(authClient);
    }

    @Test
    void replaceRolesPreventsRemovingLastAdmin() {
        UUID id = UUID.randomUUID();
        AppUser user = user(id, "other", true, adminRole);
        when(users.findById(id)).thenReturn(Optional.of(user));
        when(roles.findByName("USER")).thenReturn(Optional.of(userRole));
        when(users.countActiveAdmins()).thenReturn(1L);

        assertThatThrownBy(() -> service.replaceRoles(id, new RolesRequest(Set.of("USER")), admin))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.LAST_ADMIN));
        verify(adminGuard).lockAdminChanges();
    }

    @Test
    void replaceRolesAllowsRemovingAdminWhenAnotherRemains() {
        UUID id = UUID.randomUUID();
        AppUser user = user(id, "other", true, adminRole);
        when(users.findById(id)).thenReturn(Optional.of(user));
        when(roles.findByName("USER")).thenReturn(Optional.of(userRole));
        when(users.countActiveAdmins()).thenReturn(2L);
        when(users.save(user)).thenReturn(user);

        UserResponse result = service.replaceRoles(id, new RolesRequest(Set.of("USER")), admin);

        assertThat(result.roles()).containsExactly("USER");
        verify(authClient).revokeTokens(id, "ROLE_CHANGE");
    }

    @Test
    void replaceRolesDoesNotLockWhenUserIsDisabledOrAdminIsKept() {
        UUID id = UUID.randomUUID();
        AppUser disabledAdmin = user(id, "other", false, adminRole);
        when(users.findById(id)).thenReturn(Optional.of(disabledAdmin));
        when(roles.findByName("USER")).thenReturn(Optional.of(userRole));
        when(users.save(disabledAdmin)).thenReturn(disabledAdmin);
        service.replaceRoles(id, new RolesRequest(Set.of("USER")), admin);
        verifyNoInteractions(adminGuard);

        UUID id2 = UUID.randomUUID();
        AppUser active = user(id2, "active", true, adminRole);
        when(users.findById(id2)).thenReturn(Optional.of(active));
        when(roles.findByName("ADMIN")).thenReturn(Optional.of(adminRole));
        when(users.save(active)).thenReturn(active);
        service.replaceRoles(id2, new RolesRequest(Set.of("ADMIN")), admin);
        verify(authClient, never()).revokeTokens(id2, "ROLE_CHANGE");
    }

    @Test
    void replaceRolesRejectsMissingUserAndUnknownRole() {
        UUID id = UUID.randomUUID();
        when(users.findById(id)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.replaceRoles(id, new RolesRequest(Set.of("USER")), admin))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.USER_NOT_FOUND));

        UUID id2 = UUID.randomUUID();
        when(users.findById(id2)).thenReturn(Optional.of(user(id2, "alice", true, userRole)));
        assertThatThrownBy(() -> service.replaceRoles(id2, new RolesRequest(Set.of("OWNER")), admin))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.ROLE_NOT_FOUND));
    }

    @Test
    void meAndRolesAreExposed() {
        UUID id = UUID.randomUUID();
        when(users.findById(id)).thenReturn(Optional.of(user(id, "alice", true, userRole)));
        when(roles.findAllByOrderByNameAsc()).thenReturn(List.of(adminRole, userRole));

        assertThat(service.me(new CurrentUser(id, "alice", "Alice", Set.of("USER"))).username())
                .isEqualTo("alice");
        assertThat(service.listRoles()).extracting(r -> r.name())
                .containsExactly("ADMIN", "USER");
    }

    private static CreateUserRequest createRequest(String username, String email, Set<String> roles) {
        return new CreateUserRequest(username, email, "Alice Person", roles);
    }
}
