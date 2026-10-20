export type ProgressTone = 'success' | 'normal' | 'exception' | 'active';

export function progressTone(status: string, percent: number): ProgressTone {
  if (status === 'CLOSED' || percent >= 100) return 'success';
  if (percent <= 0) return 'normal';
  return 'active';
}

export function percentGap(actual: number, plan: number): number {
  return Math.round((actual - plan) * 100) / 100;
}

export function formatPercent(value: number | string | null | undefined): string {
  if (value === null || value === undefined || Number.isNaN(Number(value))) return '0.00%';
  return `${Number(value).toFixed(2)}%`;
}

export function gapLabel(actual: number, plan: number): string {
  const gap = percentGap(actual, plan);
  if (gap > 0) return `+${gap.toFixed(2)} pp ahead`;
  if (gap < 0) return `${gap.toFixed(2)} pp behind`;
  return 'On plan';
}
