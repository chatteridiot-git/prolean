package com.prolean.user.service;

import com.prolean.common.api.PageQuery;
import com.prolean.common.api.PageResponse;
import com.prolean.common.error.ApiException;
import com.prolean.common.error.ErrorCode;
import com.prolean.common.security.CurrentUser;
import com.prolean.user.domain.AppUser;
import com.prolean.user.domain.Project;
import com.prolean.user.domain.ProjectMember;
import com.prolean.user.domain.ProjectStatus;
import com.prolean.user.dto.ProjectDtos.AddMembersRequest;
import com.prolean.user.dto.ProjectDtos.CreateProjectRequest;
import com.prolean.user.dto.ProjectDtos.MemberResponse;
import com.prolean.user.dto.ProjectDtos.MembersAddedResponse;
import com.prolean.user.dto.ProjectDtos.ProjectResponse;
import com.prolean.user.dto.ProjectDtos.ProjectSummary;
import com.prolean.user.dto.ProjectDtos.UpdateProjectRequest;
import com.prolean.user.mapper.ProjectMapper;
import com.prolean.user.repository.ProjectMemberRepository;
import com.prolean.user.repository.ProjectRepository;
import com.prolean.user.repository.UserRepository;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ProjectService {

    private static final Set<String> SORT_FIELDS = Set.of("code", "name", "startDate", "endDate", "status", "createdAt");
    private static final Set<String> MEMBER_SORT_FIELDS = Set.of("addedAt");

    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository memberRepository;
    private final UserRepository userRepository;

    public ProjectService(ProjectRepository projectRepository, ProjectMemberRepository memberRepository,
            UserRepository userRepository) {
        this.projectRepository = projectRepository;
        this.memberRepository = memberRepository;
        this.userRepository = userRepository;
    }

    public PageResponse<ProjectSummary> list(PageQuery query, String q, ProjectStatus status,
            CurrentUser actor) {
        Pageable pageable = query.toPageable(SORT_FIELDS, "code,asc");
        Page<Project> page = projectRepository.search(actor.id(), actor.isAdmin(), status,
                normalizeNullable(q), pageable);
        List<ProjectSummary> content = page.getContent().stream()
                .map(project -> ProjectMapper.toSummary(project, memberRepository.countByProject_Id(project.getId())))
                .toList();
        return PageResponse.of(content, page);
    }

    @Transactional
    public ProjectResult create(CreateProjectRequest request, CurrentUser actor) {
        var existing = projectRepository.findByRequestId(request.requestId());
        if (existing.isPresent()) {
            Project project = existing.get();
            return new ProjectResult(toResponse(project), true);
        }
        String code = request.code().trim().toUpperCase(Locale.ROOT);
        if (projectRepository.existsByCode(code)) {
            throw new ApiException(ErrorCode.PROJECT_CODE_TAKEN);
        }
        requireValidDates(request.startDate(), request.endDate());

        Project project = new Project();
        project.setCode(code);
        project.setName(request.name().trim());
        project.setDescription(request.description());
        project.setStartDate(request.startDate());
        project.setEndDate(request.endDate());
        project.setStatus(ProjectStatus.ACTIVE);
        project.setRequestId(request.requestId());
        project.setCreatedBy(actor.id());
        project.setUpdatedBy(actor.id());
        project = projectRepository.save(project);
        if (request.memberIds() != null && !request.memberIds().isEmpty()) {
            addMembersInternal(project, request.memberIds(), actor);
        }
        return new ProjectResult(toResponse(project), false);
    }

    public ProjectResponse get(UUID id, CurrentUser actor) {
        Project project = requireProject(id);
        ensureCanRead(project, actor);
        return toResponse(project);
    }

    @Transactional
    public ProjectResponse update(UUID id, UpdateProjectRequest request, CurrentUser actor) {
        Project project = requireProject(id);
        requireValidDates(request.startDate(), request.endDate());
        project.setName(request.name().trim());
        project.setDescription(request.description());
        project.setStartDate(request.startDate());
        project.setEndDate(request.endDate());
        project.setUpdatedBy(actor.id());
        return toResponse(projectRepository.save(project));
    }

    @Transactional
    public ProjectResponse changeStatus(UUID id, ProjectStatus status, CurrentUser actor) {
        Project project = requireProject(id);
        if (status == ProjectStatus.ARCHIVED) {
            project.archive(actor.id());
        } else {
            project.restore(actor.id());
        }
        return toResponse(projectRepository.save(project));
    }

    public PageResponse<MemberResponse> members(UUID projectId, PageQuery query, CurrentUser actor) {
        Project project = requireProject(projectId);
        ensureCanRead(project, actor);
        Pageable pageable = query.toPageable(MEMBER_SORT_FIELDS, "addedAt,asc");
        Page<ProjectMember> page = memberRepository.findByProject_Id(projectId, pageable);
        return PageResponse.of(page.map(ProjectMapper::toMember).getContent(), page);
    }

    @Transactional
    public MembersAddedResponse addMembers(UUID projectId, AddMembersRequest request, CurrentUser actor) {
        Project project = requireProject(projectId);
        return addMembersInternal(project, request.userIds(), actor);
    }

    @Transactional
    public void removeMember(UUID projectId, UUID userId, CurrentUser actor) {
        requireProject(projectId);
        int removed = memberRepository.deleteMembership(projectId, userId);
        if (removed == 0) {
            throw new ApiException(ErrorCode.MEMBER_NOT_FOUND);
        }
    }

    private MembersAddedResponse addMembersInternal(Project project, List<UUID> requestedUserIds,
            CurrentUser actor) {
        Set<UUID> userIds = new LinkedHashSet<>(requestedUserIds);
        Map<UUID, AppUser> users = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(AppUser::getId, Function.identity()));
        for (UUID userId : userIds) {
            AppUser user = users.get(userId);
            if (user == null || !user.isEnabled()) {
                throw new ApiException(ErrorCode.USER_NOT_FOUND);
            }
        }
        Set<UUID> existing = memberRepository.findUserIdsByProjectId(project.getId());
        List<ProjectMember> additions = userIds.stream()
                .filter(userId -> !existing.contains(userId))
                .map(userId -> {
                    ProjectMember member = new ProjectMember();
                    member.setProject(project);
                    member.setUser(users.get(userId));
                    member.setAddedBy(actor.id());
                    return member;
                })
                .toList();
        memberRepository.saveAll(additions);
        return new MembersAddedResponse(additions.size(), userIds.size() - additions.size());
    }

    private Project requireProject(UUID id) {
        return projectRepository.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.PROJECT_NOT_FOUND));
    }

    private void ensureCanRead(Project project, CurrentUser actor) {
        if (!actor.isAdmin() && !memberRepository.existsMembership(project.getId(), actor.id())) {
            throw new ApiException(ErrorCode.FORBIDDEN, "You are not a member of this project.");
        }
    }

    private void requireValidDates(java.time.LocalDate startDate, java.time.LocalDate endDate) {
        if (endDate.isBefore(startDate)) {
            throw new ApiException(ErrorCode.INVALID_DATE_RANGE);
        }
    }

    private ProjectResponse toResponse(Project project) {
        return ProjectMapper.toResponse(project, memberRepository.countByProject_Id(project.getId()));
    }

    private String normalizeNullable(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public record ProjectResult(ProjectResponse project, boolean replay) {
    }
}
