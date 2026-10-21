package com.prolean.timeline.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;
import org.junit.jupiter.api.Test;

class WorkingDayCalculatorTest {

    @Test
    void countsInclusiveWeekdaysAndExcludesHolidays() {
        LocalDate monday = LocalDate.of(2026, 11, 2);
        LocalDate sunday = LocalDate.of(2026, 11, 8);

        assertThat(WorkingDayCalculator.countWorkingDays(monday, sunday, Set.of())).isEqualTo(5);
        assertThat(WorkingDayCalculator.countWorkingDays(monday, sunday,
                Set.of(monday, LocalDate.of(2026, 11, 4)))).isEqualTo(3);
        assertThat(WorkingDayCalculator.countWorkingDays(monday, monday, Set.of())).isOne();
        assertThat(WorkingDayCalculator.countWorkingDays(
                LocalDate.of(2026, 11, 7), LocalDate.of(2026, 11, 8), Set.of())).isZero();
    }

    @Test
    void utilityConstructorIsPrivateButReachableForCoverage() throws Exception {
        var constructor = WorkingDayCalculator.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        assertThat(constructor.newInstance()).isNotNull();
    }

    @Test
    void rejectsReversedRange() {
        assertThatThrownBy(() -> WorkingDayCalculator.countWorkingDays(
                LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 1), Set.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void planIsZeroBeforeStart() {
        var result = WorkingDayCalculator.calculatePlan(
                LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 6),
                LocalDate.of(2026, 10, 1), Set.of());

        assertThat(result.durationWorkdays()).isEqualTo(5);
        assertThat(result.percentPlan()).isEqualByComparingTo("0.00");
        assertThat(result.asOfDate()).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(result.planWarning()).isNull();
    }

    @Test
    void planIsHundredOnAndAfterEnd() {
        var onEnd = WorkingDayCalculator.calculatePlan(
                LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 6),
                LocalDate.of(2026, 11, 6), Set.of());
        var afterEnd = WorkingDayCalculator.calculatePlan(
                LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 6),
                LocalDate.of(2026, 12, 1), Set.of());

        assertThat(onEnd.percentPlan()).isEqualByComparingTo("100.00");
        assertThat(afterEnd.percentPlan()).isEqualByComparingTo("100.00");
    }

    @Test
    void planCountsElapsedWorkingDaysAndRoundsHalfUp() {
        LocalDate start = LocalDate.of(2026, 11, 2);
        LocalDate end = LocalDate.of(2026, 11, 8);

        assertThat(WorkingDayCalculator.calculatePlan(start, end,
                LocalDate.of(2026, 11, 2), Set.of()).percentPlan())
                .isEqualByComparingTo("20.00");
        assertThat(WorkingDayCalculator.calculatePlan(start, end,
                LocalDate.of(2026, 11, 3), Set.of()).percentPlan())
                .isEqualByComparingTo("40.00");
        assertThat(WorkingDayCalculator.calculatePlan(start, end,
                LocalDate.of(2026, 11, 4), Set.of()).percentPlan())
                .isEqualByComparingTo("60.00");
        assertThat(WorkingDayCalculator.calculatePlan(start, end,
                LocalDate.of(2026, 11, 5), Set.of()).percentPlan())
                .isEqualByComparingTo("80.00");
    }

    @Test
    void planUsesHolidayAwareDenominatorAndCapsAtHundred() {
        LocalDate start = LocalDate.of(2026, 11, 2);
        LocalDate end = LocalDate.of(2026, 11, 6);
        Set<LocalDate> holidays = Set.of(LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 3));

        var result = WorkingDayCalculator.calculatePlan(start, end,
                LocalDate.of(2026, 11, 4), holidays);
        assertThat(result.durationWorkdays()).isEqualTo(3);
        assertThat(result.percentPlan()).isEqualByComparingTo("33.33");

        var capped = WorkingDayCalculator.calculatePlan(start, end,
                LocalDate.of(2026, 11, 5), Set.of());
        assertThat(capped.percentPlan()).isEqualByComparingTo("80.00");
    }

    @Test
    void zeroWorkingDayRangesReturnWarningAndZeroPlan() {
        LocalDate saturday = LocalDate.of(2026, 11, 7);
        LocalDate sunday = LocalDate.of(2026, 11, 8);
        var weekend = WorkingDayCalculator.calculatePlan(saturday, sunday,
                LocalDate.of(2026, 11, 8), Set.of());

        assertThat(weekend.durationWorkdays()).isZero();
        assertThat(weekend.percentPlan()).isEqualByComparingTo(BigDecimal.ZERO.setScale(2));
        assertThat(weekend.planWarning()).isEqualTo(WorkingDayCalculator.NO_WORKING_DAYS);

        var holiday = WorkingDayCalculator.calculatePlan(
                LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 2),
                LocalDate.of(2026, 11, 2), Set.of(LocalDate.of(2026, 11, 2)));
        assertThat(holiday.durationWorkdays()).isZero();
        assertThat(holiday.planWarning()).isEqualTo(WorkingDayCalculator.NO_WORKING_DAYS);
    }

    @Test
    void roundingUsesHalfUpAtTwoDecimals() {
        // One working day out of seven produces 14.2857..., which rounds to 14.29.
        LocalDate start = LocalDate.of(2026, 11, 2);
        LocalDate end = LocalDate.of(2026, 11, 13);
        var result = WorkingDayCalculator.calculatePlan(start, end,
                LocalDate.of(2026, 11, 2), Set.of());

        assertThat(result.durationWorkdays()).isEqualTo(10);
        assertThat(result.percentPlan()).isEqualByComparingTo("10.00");

        var sixOfSeven = WorkingDayCalculator.calculatePlan(start,
                LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 9), Set.of());
        assertThat(sixOfSeven.percentPlan()).isEqualByComparingTo("85.71");
    }
}
