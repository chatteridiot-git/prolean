package com.prolean.user.api;

import com.prolean.common.api.PageQuery;
import com.prolean.common.api.PageResponse;
import com.prolean.common.security.CurrentUser;
import com.prolean.common.web.RequestHeaders;
import com.prolean.user.domain.ProjectStatus;
import com.prolean.user.dto.ProjectDtos.AddMembersRequest;
import com.prolean.user.dto.ProjectDtos.CreateProjectRequest;
import com.prolean.user.dto.ProjectDtos.MemberResponse;
import com.prolean.user.dto.ProjectDtos.MembersAddedResponse;
import com.prolean.user.dto.ProjectDtos.ProjectResponse;
import com.prolean.user.dto.ProjectDtos.ProjectStatusRequest;
import com.prolean.user.dto.ProjectDtos.ProjectSummary;
import com.prolean.user.dto.ProjectDtos.UpdateProjectRequest;
import com.prolean.user.service.ProjectService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
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
@RequestMapping("/api/v1/projects")
public class ProjectController {

    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @GetMapping
    public PageResponse<ProjectSummary> list(@RequestParam(required = false) String q,
            @RequestParam(required = false) ProjectStatus status,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort,
            @AuthenticationPrincipal Jwt jwt) {
        return projectService.list(PageQuery.of(page, size, sort), q, status, CurrentUser.from(jwt));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ProjectResponse> create(@Valid @RequestBody CreateProjectRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        ProjectService.ProjectResult result = projectService.create(request, CurrentUser.from(jwt));
        var builder = ResponseEntity.status(result.replay() ? 200 : 201);
        if (result.replay()) {
            builder.header(RequestHeaders.IDEMPOTENT_REPLAY, "true");
        } else {
            builder.location(URI.create("/api/v1/projects/" + result.project().id()));
        }
        return builder.body(result.project());
    }

    @GetMapping("/{id}")
    public ProjectResponse get(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return projectService.get(id, CurrentUser.from(jwt));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ProjectResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateProjectRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return projectService.update(id, request, CurrentUser.from(jwt));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ProjectResponse changeStatus(@PathVariable UUID id,
            @Valid @RequestBody ProjectStatusRequest request, @AuthenticationPrincipal Jwt jwt) {
        return projectService.changeStatus(id, request.status(), CurrentUser.from(jwt));
    }

    @GetMapping("/{id}/members")
    public PageResponse<MemberResponse> members(@PathVariable UUID id,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort,
            @AuthenticationPrincipal Jwt jwt) {
        return projectService.members(id, PageQuery.of(page, size, sort), CurrentUser.from(jwt));
    }

    @PostMapping("/{id}/members")
    @PreAuthorize("hasRole('ADMIN')")
    public MembersAddedResponse addMembers(@PathVariable UUID id,
            @Valid @RequestBody AddMembersRequest request, @AuthenticationPrincipal Jwt jwt) {
        return projectService.addMembers(id, request, CurrentUser.from(jwt));
    }

    @DeleteMapping("/{id}/members/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> removeMember(@PathVariable UUID id, @PathVariable UUID userId,
            @AuthenticationPrincipal Jwt jwt) {
        projectService.removeMember(id, userId, CurrentUser.from(jwt));
        return ResponseEntity.noContent().build();
    }
}
