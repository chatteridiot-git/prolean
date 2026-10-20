package com.prolean.timeline.dto;

import com.prolean.timeline.domain.HolidayType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public final class HolidayDtos {

    private HolidayDtos() {
    }

    public record HolidayRequest(
            @NotNull LocalDate date,
            @NotBlank @Size(max = 150) String name,
            @NotNull HolidayType type) {
    }

    public record HolidayResponse(
            UUID id,
            LocalDate date,
            String name,
            HolidayType type,
            UUID createdBy,
            UUID updatedBy,
            Instant createdAt,
            Instant updatedAt) {
    }
}
