import dayjs from 'dayjs';
import utc from 'dayjs/plugin/utc';
import timezone from 'dayjs/plugin/timezone';
import 'dayjs/locale/id';

dayjs.extend(utc);
dayjs.extend(timezone);

export const JAKARTA_ZONE = 'Asia/Jakarta';
export const API_DATE = 'YYYY-MM-DD';

export function jakartaToday(): string {
  return dayjs().tz(JAKARTA_ZONE).format(API_DATE);
}

export function formatDate(value?: string | null): string {
  return value ? dayjs(value).format('DD MMM YYYY') : '—';
}

export function formatDateTime(value?: string | null): string {
  return value ? dayjs(value).tz(JAKARTA_ZONE).format('DD MMM YYYY HH:mm') : '—';
}

export function dateRange(start?: string | null, end?: string | null): string {
  if (!start && !end) return '—';
  return `${formatDate(start)} – ${formatDate(end)}`;
}

export function toApiDate(value: dayjs.Dayjs | string | null | undefined): string | undefined {
  if (!value) return undefined;
  return dayjs.isDayjs(value) ? value.format(API_DATE) : dayjs(value).format(API_DATE);
}

export function daysBetweenInclusive(start: string, end: string): number {
  return dayjs(end).diff(dayjs(start), 'day') + 1;
}

export function isCoveredDate(value?: string | null): boolean {
  if (!value) return false;
  return value >= '2025-01-01' && value <= '2027-12-31';
}
