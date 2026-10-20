package com.prolean.timeline.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.prolean.common.api.PageQuery;
import com.prolean.common.error.ApiException;
import com.prolean.common.error.ErrorCode;
import com.prolean.common.security.CurrentUser;
import com.prolean.timeline.domain.Holiday;
import com.prolean.timeline.domain.HolidayType;
import com.prolean.timeline.dto.HolidayDtos.HolidayRequest;
import com.prolean.timeline.repository.HolidayRepository;
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
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class HolidayServiceTest {

    @Mock
    private HolidayRepository repository;

    private HolidayService service;
    private final UUID actorId = UUID.randomUUID();
    private final CurrentUser actor = new CurrentUser(actorId, "admin", "Admin", Set.of("ADMIN"));

    @BeforeEach
    void setUp() {
        service = new HolidayService(repository);
    }

    private static Holiday holiday(LocalDate date, String name) {
        Holiday holiday = new Holiday();
        holiday.setId(UUID.randomUUID());
        holiday.setHolidayDate(date);
        holiday.setName(name);
        holiday.setType(HolidayType.NATIONAL);
        holiday.setCreatedBy(UUID.randomUUID());
        return holiday;
    }

    @Test
    void listsAllOrByYear() {
        Holiday holiday = holiday(LocalDate.of(2026, 1, 1), "New Year");
        when(repository.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(holiday)));
        when(repository.findByHolidayDateBetween(any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(holiday)));

        assertThat(service.list(null, PageQuery.of(0, 20, null)).content()).hasSize(1);
        assertThat(service.list(2026, PageQuery.of(0, 20, null)).content()).hasSize(1);
        assertThat(service.list(2026, PageQuery.of(0, 20, "name,asc")).content().getFirst().name())
                .isEqualTo("New Year");
    }

    @Test
    void createsHolidayWithAuditFields() {
        when(repository.existsByHolidayDate(LocalDate.of(2026, 1, 1))).thenReturn(false);
        when(repository.save(any())).thenAnswer(invocation -> {
            Holiday holiday = invocation.getArgument(0);
            holiday.setId(UUID.randomUUID());
            return holiday;
        });

        var response = service.create(new HolidayRequest(LocalDate.of(2026, 1, 1), " New Year ", HolidayType.NATIONAL), actor);

        assertThat(response.name()).isEqualTo("New Year");
        assertThat(response.type()).isEqualTo(HolidayType.NATIONAL);
        ArgumentCaptor<Holiday> captor = ArgumentCaptor.forClass(Holiday.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getCreatedBy()).isEqualTo(actorId);
        assertThat(captor.getValue().getUpdatedBy()).isEqualTo(actorId);
    }

    @Test
    void createRejectsDuplicateDate() {
        when(repository.existsByHolidayDate(LocalDate.of(2026, 1, 1))).thenReturn(true);
        assertThatThrownBy(() -> service.create(
                new HolidayRequest(LocalDate.of(2026, 1, 1), "New Year", HolidayType.NATIONAL), actor))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.HOLIDAY_DATE_TAKEN));
    }

    @Test
    void updatesHolidayAndRejectsDuplicateDateOrMissing() {
        Holiday holiday = holiday(LocalDate.of(2026, 1, 1), "Old");
        when(repository.findById(holiday.getId())).thenReturn(Optional.of(holiday));
        when(repository.existsByHolidayDateAndIdNot(LocalDate.of(2026, 1, 2), holiday.getId()))
                .thenReturn(true);
        assertThatThrownBy(() -> service.update(holiday.getId(),
                new HolidayRequest(LocalDate.of(2026, 1, 2), "New", HolidayType.JOINT_LEAVE), actor))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.HOLIDAY_DATE_TAKEN));

        when(repository.existsByHolidayDateAndIdNot(LocalDate.of(2026, 1, 2), holiday.getId()))
                .thenReturn(false);
        when(repository.save(holiday)).thenReturn(holiday);
        var response = service.update(holiday.getId(),
                new HolidayRequest(LocalDate.of(2026, 1, 2), " New ", HolidayType.JOINT_LEAVE), actor);
        assertThat(response.date()).isEqualTo(LocalDate.of(2026, 1, 2));
        assertThat(response.name()).isEqualTo("New");
        assertThat(response.type()).isEqualTo(HolidayType.JOINT_LEAVE);

        when(repository.findById(holiday.getId())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.update(holiday.getId(),
                new HolidayRequest(LocalDate.of(2026, 1, 3), "New", HolidayType.NATIONAL), actor))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.HOLIDAY_NOT_FOUND));
    }

    @Test
    void deletesExistingHolidayAndRejectsMissing() {
        Holiday holiday = holiday(LocalDate.of(2026, 1, 1), "New Year");
        when(repository.findById(holiday.getId())).thenReturn(Optional.of(holiday));
        service.delete(holiday.getId());
        verify(repository).delete(holiday);

        when(repository.findById(holiday.getId())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.delete(holiday.getId()))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.HOLIDAY_NOT_FOUND));
    }
}
