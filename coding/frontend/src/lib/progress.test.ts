import { describe, expect, it } from 'vitest';
import { formatPercent, gapLabel, percentGap, progressTone } from '@/lib/progress';

describe('progress helpers', () => {
  it('chooses progress tones', () => {
    expect(progressTone('CLOSED', 20)).toBe('success');
    expect(progressTone('IN_PROGRESS', 100)).toBe('success');
    expect(progressTone('OPEN', 0)).toBe('normal');
    expect(progressTone('OPEN', -1)).toBe('normal');
    expect(progressTone('IN_PROGRESS', 1)).toBe('active');
  });

  it('formats percentages and gaps', () => {
    expect(percentGap(55.554, 50)).toBe(5.55);
    expect(percentGap(49.999, 50)).toBe(-0.0);
    expect(formatPercent(12.345)).toBe('12.35%');
    expect(formatPercent('20')).toBe('20.00%');
    expect(formatPercent(null)).toBe('0.00%');
    expect(formatPercent(undefined)).toBe('0.00%');
    expect(formatPercent('not-a-number')).toBe('0.00%');
    expect(gapLabel(60, 50)).toBe('+10.00 pp ahead');
    expect(gapLabel(45, 50)).toBe('-5.00 pp behind');
    expect(gapLabel(50, 50)).toBe('On plan');
  });
});
