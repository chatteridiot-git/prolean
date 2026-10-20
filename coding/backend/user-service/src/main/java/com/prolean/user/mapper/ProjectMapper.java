package com.prolean.user.mapper;

import com.prolean.user.domain.Project;
import com.prolean.user.domain.ProjectMember;
import com.prolean.user.dto.ProjectDtos.MemberResponse;
import com.prolean.user.dto.ProjectDtos.ProjectResponse;
import com.prolean.user.dto.ProjectDtos.ProjectSummary;

public final class ProjectMapper {

    private ProjectMapper() {
    }

    public static ProjectSummary toSummary(Project project, long memberCount) {
        return new ProjectSummary(
                project.getId(),
                project.getCode(),
                project.getName(),
                project.getStatus(),
                project.getStartDate(),
                project.getEndDate(),
                memberCount);
    }

    public static ProjectResponse toResponse(Project project, long memberCount) {
        return new ProjectResponse(
                project.getId(),
                project.getCode(),
                project.getName(),
                project.getDescription(),
                project.getStartDate(),
                project.getEndDate(),
                project.getStatus(),
                memberCount,
                project.getCreatedBy(),
                project.getCreatedAt(),
                project.getUpdatedAt());
    }

    public static MemberResponse toMember(ProjectMember member) {
        return new MemberResponse(
                member.getUser().getId(),
                member.getUser().getUsername(),
                member.getUser().getFullName(),
                member.getUser().getEmail(),
                member.getAddedAt());
    }
}
