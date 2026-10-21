import { useRef } from 'react';

/**
 * Generates one idempotency key per user submission.
 *
 * <p>The key survives failed requests so a manual retry replays the original operation instead of
 * creating a duplicate. It is cleared only after a successful mutation.
 */
export function useIdempotencyKey() {
  const current = useRef<string | null>(null);
  return {
    get(): string {
      current.current ??= crypto.randomUUID();
      return current.current;
    },
    reset(): void {
      current.current = null;
    },
  };
}
