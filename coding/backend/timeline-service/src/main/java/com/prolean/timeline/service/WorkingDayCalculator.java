package com.prolean.timeline.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Set;

/**
 * Inclusive working-day counter and plan-completion calculator.
 *
 * <p>Weekends and every holiday (national or joint leave) are excluded. Plan percentage is capped
 * at 0 before the start and at 100 on/after the end, and rounded half-up to two decimals.
 */
public final class WorkingDayCalculator {

    public static final String NO_WORKING_DAYS = "NO_WORKING_DAYS";

    private WorkingDayCalculator() {
    }

    public static int countWorkingDays(LocalDate startDate, LocalDate endDate, Set<LocalDate> holidays) {
        if (endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("endDate must be on or after startDate");
        }
        int count = 0;
        LocalDate date = startDate;
        while (!date.isAfter(endDate)) {
            DayOfWeek day = date.getDayOfWeek();
            if (day != DayOfWeek.SATURDAY && day != DayOfWeek.SUNDAY && !holidays.contains(date)) {
                count++;
            }
            date = date.plusDays(1);
        }
        return count;
    }

    public static PlanResult calculatePlan(LocalDate startDate, LocalDate endDate, LocalDate asOfDate,
            Set<LocalDate> holidays) {
        int totalWorkingDays = countWorkingDays(startDate, endDate, holidays);
        if (totalWorkingDays == 0) {
            return new PlanResult(0, BigDecimal.ZERO.setScale(2), asOfDate, NO_WORKING_DAYS);
        }
        if (asOfDate.isBefore(startDate)) {
            return new PlanResult(totalWorkingDays, BigDecimal.ZERO.setScale(2), asOfDate, null);
        }
        if (!asOfDate.isBefore(endDate)) {
            return new PlanResult(totalWorkingDays, new BigDecimal("100.00"), asOfDate, null);
        }
        int elapsed = countWorkingDays(startDate, asOfDate, holidays);
        BigDecimal percent = BigDecimal.valueOf((long) elapsed * 100L)
                .divide(BigDecimal.valueOf(totalWorkingDays), 2, RoundingMode.HALF_UP);
        if (percent.compareTo(new BigDecimal("100.00")) > 0) {
            percent = new BigDecimal("100.00");
        }
        return new PlanResult(totalWorkingDays, percent.setScale(2, RoundingMode.HALF_UP), asOfDate, null);
    }

    public record PlanResult(int durationWorkdays, BigDecimal percentPlan, LocalDate asOfDate,
            String planWarning) {
    }
}
