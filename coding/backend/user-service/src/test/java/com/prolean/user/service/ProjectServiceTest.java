package com.prolean.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.prolean.common.api.PageQuery;
import com.prolean.common.error.ApiException;
import com.prolean.common.error.ErrorCode;
import com.prolean.common.security.CurrentUser;
import com.prolean.user.domain.AppUser;
import com.prolean.user.domain.Project;
import com.prolean.user.domain.ProjectMember;
import com.prolean.user.domain.ProjectStatus;
import com.prolean.user.domain.Role;
import com.prolean.user.dto.ProjectDtos.AddMembersRequest;
import com.prolean.user.dto.ProjectDtos.CreateProjectRequest;
import com.prolean.user.dto.ProjectDtos.MembersAddedResponse;
import com.prolean.user.dto.ProjectDtos.ProjectResponse;
import com.prolean.user.dto.ProjectDtos.ProjectStatusRequest;
import com.prolean.user.dto.ProjectDtos.ProjectSummary;
import com.prolean.user.dto.ProjectDtos.UpdateProjectRequest;
import com.prolean.common.api.PageResponse;
import com.prolean.user.repository.ProjectMemberRepository;
import com.prolean.user.repository.ProjectRepository;
import com.prolean.user.repository.UserRepository;
import java.time.LocalDate;
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
class ProjectServiceTest {

    @Mock
    private ProjectRepository projects;
    @Mock
    private ProjectMemberRepository members;
    @Mock
    private UserRepository users;

    private ProjectService service;
    private final UUID actorId = UUID.randomUUID();
    private final CurrentUser admin = new CurrentUser(actorId, "admin", "Admin", Set.of("ADMIN"));
    private final CurrentUser member = new CurrentUser(UUID.randomUUID(), "member", "Member", Set.of("USER"));

    @BeforeEach
    void setUp() {
        service = new ProjectService(projects, members, users);
    }

    private static Project project(UUID id, String code, ProjectStatus status) {
        Project project = new Project();
        project.setId(id);
        project.setCode(code);
        project.setName("Project " + code);
        project.setStartDate(LocalDate.of(2026, 1, 1));
        project.setEndDate(LocalDate.of(2026, 12, 31));
        project.setStatus(status);
        return project;
    }

    private static AppUser user(UUID id, boolean enabled) {
        AppUser user = new AppUser();
        user.setId(id);
        user.setUsername("user-" + id.toString().substring(0, 8));
        user.setEmail(user.getUsername() + "@example.com");
        user.setFullName("User " + id);
        user.setEnabled(enabled);
        user.setRoles(Set.of(new Role()));
        return user;
    }

    private static CreateProjectRequest createRequest(String code, List<UUID> memberIds) {
        return new CreateProjectRequest(code, "Project", "Description",
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), UUID.randomUUID(), memberIds);
    }

    @Test
    void listScopesQueryForAdminAndMembers() {
        Project p = project(UUID.randomUUID(), "ALPHA", ProjectStatus.ACTIVE);
        when(projects.search(any(), anyBoolean(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(p)));
        when(members.countByProject_Id(p.getId())).thenReturn(3L);

        PageResponse<ProjectSummary> asAdmin = service.list(PageQuery.of(0, 20, null), " a ", null, admin);
        assertThat(asAdmin.content()).hasSize(1);
        assertThat(asAdmin.content().getFirst().memberCount()).isEqualTo(3);

        PageResponse<ProjectSummary> asMember = service.list(PageQuery.of(0, 20, null), null,
                ProjectStatus.ACTIVE, member);
        assertThat(asMember.content()).hasSize(1);
    }

    @Test
    void createReplaysByRequestId() {
        UUID requestId = UUID.randomUUID();
        Project existing = project(UUID.randomUUID(), "ALPHA", ProjectStatus.ACTIVE);
        when(projects.findByRequestId(requestId)).thenReturn(Optional.of(existing));
        when(members.countByProject_Id(existing.getId())).thenReturn(0L);

        var result = service.create(new CreateProjectRequest("IGNORED", "Name", null,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), requestId, null), admin);

        assertThat(result.replay()).isTrue();
        assertThat(result.project().code()).isEqualTo("ALPHA");
        verify(projects, never()).save(any());
    }

    @Test
    void createRejectsDuplicateCodeAndInvalidDates() {
        when(projects.findByRequestId(any())).thenReturn(Optional.empty());
        when(projects.existsByCode("ALPHA")).thenReturn(true);
        assertThatThrownBy(() -> service.create(createRequest("alpha", null), admin))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.PROJECT_CODE_TAKEN));

        when(projects.existsByCode("BETA")).thenReturn(false);
        assertThatThrownBy(() -> service.create(new CreateProjectRequest("BETA", "Name", null,
                LocalDate.of(2026, 2, 1), LocalDate.of(2026, 1, 1), UUID.randomUUID(), null), admin))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.INVALID_DATE_RANGE));
    }

    @Test
    void createPersistsProjectAndAddsValidMembers() {
        UUID userId = UUID.randomUUID();
        AppUser memberUser = user(userId, true);
        when(projects.findByRequestId(any())).thenReturn(Optional.empty());
        when(projects.existsByCode("ALPHA")).thenReturn(false);
        when(projects.save(any())).thenAnswer(invocation -> {
            Project p = invocation.getArgument(0);
            p.setId(UUID.randomUUID());
            return p;
        });
        when(users.findAllById(any())).thenReturn(List.of(memberUser));
        when(members.findUserIdsByProjectId(any())).thenReturn(Set.of());

        var result = service.create(createRequest("alpha", List.of(userId, userId)), admin);

        assertThat(result.replay()).isFalse();
        assertThat(result.project().code()).isEqualTo("ALPHA");
        ArgumentCaptor<List<ProjectMember>> captor = ArgumentCaptor.forClass(List.class);
        verify(members).saveAll(captor.capture());
        assertThat(captor.getValue()).hasSize(1);
        assertThat(captor.getValue().getFirst().getUser()).isEqualTo(memberUser);
    }

    @Test
    void createRejectsMissingOrDisabledMembers() {
        UUID missing = UUID.randomUUID();
        UUID disabled = UUID.randomUUID();
        when(projects.findByRequestId(any())).thenReturn(Optional.empty());
        when(projects.existsByCode("ALPHA")).thenReturn(false);
        when(projects.save(any())).thenAnswer(invocation -> {
            Project p = invocation.getArgument(0);
            p.setId(UUID.randomUUID());
            return p;
        });
        when(users.findAllById(any())).thenReturn(List.of(user(disabled, false)));

        assertThatThrownBy(() -> service.create(createRequest("ALPHA", List.of(missing)), admin))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.USER_NOT_FOUND));
        assertThatThrownBy(() -> service.create(createRequest("ALPHA", List.of(disabled)), admin))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.USER_NOT_FOUND));
    }

    @Test
    void getEnforcesMembershipAndAdminReadsAll() {
        UUID id = UUID.randomUUID();
        Project p = project(id, "ALPHA", ProjectStatus.ACTIVE);
        when(projects.findById(id)).thenReturn(Optional.of(p));
        when(members.countByProject_Id(id)).thenReturn(1L);

        assertThat(service.get(id, admin).code()).isEqualTo("ALPHA");

        when(members.existsMembership(id, member.id())).thenReturn(false);
        assertThatThrownBy(() -> service.get(id, member))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.FORBIDDEN));

        when(members.existsMembership(id, member.id())).thenReturn(true);
        assertThat(service.get(id, member).id()).isEqualTo(id);
    }

    @Test
    void getRejectsMissingProject() {
        UUID id = UUID.randomUUID();
        when(projects.findById(id)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.get(id, admin))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.PROJECT_NOT_FOUND));
    }

    @Test
    void updateValidatesDatesAndPersists() {
        UUID id = UUID.randomUUID();
        Project p = project(id, "ALPHA", ProjectStatus.ACTIVE);
        when(projects.findById(id)).thenReturn(Optional.of(p));
        when(projects.save(p)).thenReturn(p);
        when(members.countByProject_Id(id)).thenReturn(0L);

        ProjectResponse response = service.update(id,
                new UpdateProjectRequest(" New Name ", "Desc",
                        LocalDate.of(2026, 2, 1), LocalDate.of(2026, 3, 1)), admin);

        assertThat(response.name()).isEqualTo("New Name");
        assertThat(response.startDate()).isEqualTo(LocalDate.of(2026, 2, 1));

        assertThatThrownBy(() -> service.update(id, new UpdateProjectRequest("Name", null,
                LocalDate.of(2026, 3, 1), LocalDate.of(2026, 2, 1)), admin))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.INVALID_DATE_RANGE));
    }

    @Test
    void changeStatusArchivesAndRestores() {
        UUID id = UUID.randomUUID();
        Project p = project(id, "ALPHA", ProjectStatus.ACTIVE);
        when(projects.findById(id)).thenReturn(Optional.of(p));
        when(projects.save(p)).thenReturn(p);
        when(members.countByProject_Id(id)).thenReturn(0L);

        assertThat(service.changeStatus(id, ProjectStatus.ARCHIVED, admin).status())
                .isEqualTo(ProjectStatus.ARCHIVED);
        assertThat(p.getArchivedBy()).isEqualTo(actorId);
        assertThat(p.getArchivedAt()).isNotNull();

        assertThat(service.changeStatus(id, ProjectStatus.ACTIVE, admin).status())
                .isEqualTo(ProjectStatus.ACTIVE);
        assertThat(p.getArchivedAt()).isNull();
    }

    @Test
    void membersListsOnlyReadableProject() {
        UUID id = UUID.randomUUID();
        Project p = project(id, "ALPHA", ProjectStatus.ACTIVE);
        when(projects.findById(id)).thenReturn(Optional.of(p));
        when(members.existsMembership(id, member.id())).thenReturn(true);
        AppUser user = user(UUID.randomUUID(), true);
        ProjectMember membership = new ProjectMember();
        membership.setProject(p);
        membership.setUser(user);
        when(members.findByProject_Id(eq(id), any()))
                .thenReturn(new PageImpl<>(List.of(membership)));

        var page = service.members(id, PageQuery.of(0, 20, null), member);

        assertThat(page.content()).hasSize(1);
        assertThat(page.content().getFirst().userId()).isEqualTo(user.getId());
    }

    @Test
    void addMembersReportsAlreadyPresentUsers() {
        UUID projectId = UUID.randomUUID();
        UUID existingUser = UUID.randomUUID();
        UUID newUser = UUID.randomUUID();
        Project p = project(projectId, "ALPHA", ProjectStatus.ACTIVE);
        when(projects.findById(projectId)).thenReturn(Optional.of(p));
        when(users.findAllById(any())).thenReturn(List.of(
                user(existingUser, true), user(newUser, true)));
        when(members.findUserIdsByProjectId(projectId)).thenReturn(Set.of(existingUser));

        MembersAddedResponse result = service.addMembers(projectId,
                new AddMembersRequest(List.of(existingUser, newUser, newUser)), admin);

        assertThat(result.added()).isOne();
        assertThat(result.alreadyMembers()).isOne();
        verify(members).saveAll(any());
    }

    @Test
    void removeMemberRejectsMissingMembership() {
        UUID projectId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(projects.findById(projectId))
                .thenReturn(Optional.of(project(projectId, "ALPHA", ProjectStatus.ACTIVE)));
        when(members.deleteMembership(projectId, userId)).thenReturn(0);

        assertThatThrownBy(() -> service.removeMember(projectId, userId, admin))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.MEMBER_NOT_FOUND));
    }

    @Test
    void removeMemberDeletesExistingMembership() {
        UUID projectId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(projects.findById(projectId))
                .thenReturn(Optional.of(project(projectId, "ALPHA", ProjectStatus.ACTIVE)));
        when(members.deleteMembership(projectId, userId)).thenReturn(1);

        service.removeMember(projectId, userId, admin);

        verify(members).deleteMembership(projectId, userId);
    }
}
