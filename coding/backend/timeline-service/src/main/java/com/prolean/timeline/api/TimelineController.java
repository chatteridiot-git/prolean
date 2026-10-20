package com.prolean.timeline.api;

import com.prolean.common.api.PageQuery;
import com.prolean.common.api.PageResponse;
import com.prolean.common.security.CurrentUser;
import com.prolean.common.web.RequestHeaders;
import com.prolean.timeline.dto.TimelineDtos.CreateRevisionRequest;
import com.prolean.timeline.dto.TimelineDtos.CreateTimelineRequest;
import com.prolean.timeline.dto.TimelineDtos.EditDraftRequest;
import com.prolean.timeline.dto.TimelineDtos.ProgressRequest;
import com.prolean.timeline.dto.TimelineDtos.PublishRequest;
import com.prolean.timeline.dto.TimelineDtos.StatusRequest;
import com.prolean.timeline.dto.TimelineDtos.TimelineAggregateResponse;
import com.prolean.timeline.dto.TimelineDtos.TimelineVersionResponse;
import com.prolean.timeline.dto.TimelineDtos.VersionResult;
import com.prolean.timeline.service.TimelineService;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
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
@RequestMapping("/api/v1/projects/{projectId}/timeline")
public class TimelineController {

    private final TimelineService timelineService;

    public TimelineController(TimelineService timelineService) {
        this.timelineService = timelineService;
    }

    @GetMapping
    public TimelineAggregateResponse aggregate(@PathVariable UUID projectId,
            @AuthenticationPrincipal Jwt jwt) {
        return timelineService.aggregate(projectId, CurrentUser.from(jwt));
    }

    @GetMapping("/current")
    public TimelineVersionResponse current(@PathVariable UUID projectId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf,
            @AuthenticationPrincipal Jwt jwt) {
        return timelineService.current(projectId, asOf, CurrentUser.from(jwt));
    }

    @GetMapping("/versions")
    public PageResponse<TimelineVersionResponse> versions(@PathVariable UUID projectId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort,
            @AuthenticationPrincipal Jwt jwt) {
        return timelineService.versions(projectId, PageQuery.of(page, size == null ? 50 : size, sort),
                CurrentUser.from(jwt));
    }

    @GetMapping("/versions/{versionId}")
    public TimelineVersionResponse version(@PathVariable UUID projectId, @PathVariable UUID versionId,
            @AuthenticationPrincipal Jwt jwt) {
        return timelineService.version(projectId, versionId, CurrentUser.from(jwt));
    }

    @PostMapping
    public ResponseEntity<TimelineVersionResponse> create(@PathVariable UUID projectId,
            @Valid @RequestBody CreateTimelineRequest request, @AuthenticationPrincipal Jwt jwt) {
        VersionResult result = timelineService.create(projectId, request, CurrentUser.from(jwt));
        return response(result, 201, URI.create("/api/v1/projects/" + projectId + "/timeline/versions/"
                + result.version().id()));
    }

    @PutMapping("/draft")
    public TimelineVersionResponse editDraft(@PathVariable UUID projectId,
            @Valid @RequestBody EditDraftRequest request, @AuthenticationPrincipal Jwt jwt) {
        return timelineService.editDraft(projectId, request, CurrentUser.from(jwt));
    }

    @DeleteMapping("/draft")
    public ResponseEntity<Void> cancelDraft(@PathVariable UUID projectId,
            @RequestParam(required = false) Instant expectedDraftUpdatedAt,
            @AuthenticationPrincipal Jwt jwt) {
        timelineService.cancelDraft(projectId, expectedDraftUpdatedAt, CurrentUser.from(jwt));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/draft/publish")
    public ResponseEntity<TimelineVersionResponse> publish(@PathVariable UUID projectId,
            @Valid @RequestBody PublishRequest request, @AuthenticationPrincipal Jwt jwt) {
        VersionResult result = timelineService.publish(projectId, request, CurrentUser.from(jwt));
        return response(result, 200, null);
    }

    @PostMapping("/revisions")
    public ResponseEntity<TimelineVersionResponse> createRevision(@PathVariable UUID projectId,
            @Valid @RequestBody CreateRevisionRequest request, @AuthenticationPrincipal Jwt jwt) {
        VersionResult result = timelineService.createRevision(projectId, request,
                CurrentUser.from(jwt));
        return response(result, 201, URI.create("/api/v1/projects/" + projectId + "/timeline/versions/"
                + result.version().id()));
    }

    @PatchMapping("/current/status")
    public TimelineVersionResponse changeStatus(@PathVariable UUID projectId,
            @Valid @RequestBody StatusRequest request, @AuthenticationPrincipal Jwt jwt) {
        return timelineService.changeStatus(projectId, request.status(), CurrentUser.from(jwt));
    }

    @PatchMapping("/current/progress")
    public TimelineVersionResponse changeProgress(@PathVariable UUID projectId,
            @Valid @RequestBody ProgressRequest request, @AuthenticationPrincipal Jwt jwt) {
        return timelineService.changeProgress(projectId, request.percentActual(),
                CurrentUser.from(jwt));
    }

    private ResponseEntity<TimelineVersionResponse> response(VersionResult result, int createdStatus,
            URI location) {
        var builder = ResponseEntity.status(result.replay() ? 200 : createdStatus);
        if (result.replay()) {
            builder.header(RequestHeaders.IDEMPOTENT_REPLAY, "true");
        }
        if (location != null && !result.replay()) {
            builder.location(location);
        }
        return builder.body(result.version());
    }
}
