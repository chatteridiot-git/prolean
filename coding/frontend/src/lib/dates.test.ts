import { describe, expect, it, vi } from 'vitest';
import dayjs from 'dayjs';
import {
  API_DATE,
  JAKARTA_ZONE,
  dateRange,
  daysBetweenInclusive,
  formatDate,
  formatDateTime,
  isCoveredDate,
  jakartaToday,
  toApiDate,
} from '@/lib/dates';

describe('dates', () => {
  it('formats dates and missing values', () => {
    expect(formatDate('2026-01-05')).toBe('05 Jan 2026');
    expect(formatDate(null)).toBe('—');
    expect(dateRange('2026-01-05', '2026-01-09')).toBe('05 Jan 2026 – 09 Jan 2026');
    expect(dateRange()).toBe('—');
    expect(formatDateTime('2026-01-05T03:04:00Z')).toBe('05 Jan 2026 10:04');
    expect(formatDateTime(undefined)).toBe('—');
  });

  it('converts date inputs to API dates', () => {
    expect(API_DATE).toBe('YYYY-MM-DD');
    expect(toApiDate(dayjs('2026-02-03'))).toBe('2026-02-03');
    expect(toApiDate('2026-02-04T12:00:00Z')).toBe('2026-02-04');
    expect(toApiDate(null)).toBeUndefined();
    expect(toApiDate(undefined)).toBeUndefined();
  });

  it('computes inclusive durations, coverage and Jakarta today', () => {
    expect(daysBetweenInclusive('2026-01-05', '2026-01-05')).toBe(1);
    expect(daysBetweenInclusive('2026-01-05', '2026-01-09')).toBe(5);
    expect(isCoveredDate('2025-01-01')).toBe(true);
    expect(isCoveredDate('2027-12-31')).toBe(true);
    expect(isCoveredDate('2024-12-31')).toBe(false);
    expect(isCoveredDate('2028-01-01')).toBe(false);
    expect(isCoveredDate()).toBe(false);
    expect(jakartaToday()).toMatch(/^\d{4}-\d{2}-\d{2}$/);
    expect(JAKARTA_ZONE).toBe('Asia/Jakarta');
    vi.useRealTimers();
  });
});
