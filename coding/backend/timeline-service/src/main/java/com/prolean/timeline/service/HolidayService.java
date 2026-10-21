package com.prolean.timeline.service;

import com.prolean.common.api.PageQuery;
import com.prolean.common.api.PageResponse;
import com.prolean.common.error.ApiException;
import com.prolean.common.error.ErrorCode;
import com.prolean.common.security.CurrentUser;
import com.prolean.timeline.domain.Holiday;
import com.prolean.timeline.dto.HolidayDtos.HolidayRequest;
import com.prolean.timeline.dto.HolidayDtos.HolidayResponse;
import com.prolean.timeline.repository.HolidayRepository;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class HolidayService {

    private static final Set<String> SORT_FIELDS = Set.of("holidayDate", "name", "type", "createdAt");

    private final HolidayRepository holidayRepository;

    public HolidayService(HolidayRepository holidayRepository) {
        this.holidayRepository = holidayRepository;
    }

    public Horizon horizon() {
        return calendarHorizon(holidayRepository.findMinimumHolidayDate(),
                holidayRepository.findMaximumHolidayDate());
    }

    /**
     * The supported horizon is whole calendar years, not the first/last seeded holiday date. This
     * keeps 2025-01-01..2027-12-31 valid even though the seed's last holiday is earlier.
     */
    public static Horizon calendarHorizon(LocalDate minimum, LocalDate maximum) {
        return new Horizon(
                minimum == null ? null : minimum.withDayOfYear(1),
                maximum == null ? null : maximum.withDayOfYear(maximum.lengthOfYear()));
    }

    public PageResponse<HolidayResponse> list(Integer year, PageQuery query) {
        // The API exposes the field as "date", while the entity property is holidayDate.
        String sort = query.sort() == null
                ? null
                : query.sort().replaceFirst("^date(?=,|$)", "holidayDate");
        Pageable pageable = new PageQuery(query.page(), query.size(), sort)
                .toPageable(SORT_FIELDS, "holidayDate,asc");
        int resolvedYear = year == null
                ? LocalDate.now(ZoneId.of("Asia/Jakarta")).getYear()
                : year;
        Page<Holiday> page = holidayRepository.findByHolidayDateBetween(
                LocalDate.of(resolvedYear, 1, 1), LocalDate.of(resolvedYear, 12, 31), pageable);
        return PageResponse.of(page.map(HolidayService::toResponse).getContent(), page);
    }

    @Transactional
    public HolidayResponse create(HolidayRequest request, CurrentUser actor) {
        if (holidayRepository.existsByHolidayDate(request.date())) {
            throw new ApiException(ErrorCode.HOLIDAY_DATE_TAKEN);
        }
        Holiday holiday = new Holiday();
        holiday.setHolidayDate(request.date());
        holiday.setName(request.name().trim());
        holiday.setType(request.type());
        holiday.setCreatedBy(actor.id());
        holiday.setUpdatedBy(actor.id());
        return toResponse(holidayRepository.save(holiday));
    }

    @Transactional
    public HolidayResponse update(UUID id, HolidayRequest request, CurrentUser actor) {
        Holiday holiday = holidayRepository.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.HOLIDAY_NOT_FOUND));
        if (!holiday.getHolidayDate().equals(request.date())
                && holidayRepository.existsByHolidayDateAndIdNot(request.date(), id)) {
            throw new ApiException(ErrorCode.HOLIDAY_DATE_TAKEN);
        }
        holiday.setHolidayDate(request.date());
        holiday.setName(request.name().trim());
        holiday.setType(request.type());
        holiday.setUpdatedBy(actor.id());
        return toResponse(holidayRepository.save(holiday));
    }

    @Transactional
    public void delete(UUID id) {
        Holiday holiday = holidayRepository.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.HOLIDAY_NOT_FOUND));
        holidayRepository.delete(holiday);
    }

    static HolidayResponse toResponse(Holiday holiday) {
        return new HolidayResponse(
                holiday.getId(),
                holiday.getHolidayDate(),
                holiday.getName(),
                holiday.getType(),
                holiday.getCreatedBy(),
                holiday.getUpdatedBy(),
                holiday.getCreatedAt(),
                holiday.getUpdatedAt());
    }

    public record Horizon(LocalDate startDate, LocalDate endDate) {
    }
}
