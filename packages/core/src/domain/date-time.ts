/**
 * Date and time utilities with support for the 04:00 AM local learning day rollover.
 * Pure dependency-free TypeScript utilizing standard Intl and Date APIs.
 */

export const DAY_ROLLOVER_HOUR = 4;
export const MILLIS_PER_DAY = 86_400_000;

/**
 * Converts a 'YYYY-MM-DD' date string to an epoch day number (days since 1970-01-01 UTC).
 */
export function toEpochDay(dateStr: string): number {
  const [y, m, d] = dateStr.split('-').map(Number);
  return Math.floor(Date.UTC(y, m - 1, d) / MILLIS_PER_DAY);
}

/**
 * Converts an epoch day number to a 'YYYY-MM-DD' date string.
 */
export function fromEpochDay(epochDay: number): string {
  const d = new Date(epochDay * MILLIS_PER_DAY);
  return d.toISOString().slice(0, 10);
}

/**
 * Adds or subtracts days to/from a 'YYYY-MM-DD' date string.
 */
export function addDays(dateStr: string, days: number): string {
  return fromEpochDay(toEpochDay(dateStr) + days);
}

/**
 * Returns previous 'YYYY-MM-DD' date string.
 */
export function previousDay(dateStr: string): string {
  return addDays(dateStr, -1);
}

/**
 * Returns next 'YYYY-MM-DD' date string.
 */
export function nextDay(dateStr: string): string {
  return addDays(dateStr, 1);
}

/**
 * Evaluates the learner's effective day for `instant`: activity before 04:00 local time
 * in `timeZone` belongs to the previous calendar day.
 * Returns date formatted as 'YYYY-MM-DD'.
 */
export function effectiveLocalDate(
  instant: number | Date | string,
  timeZone: string
): string {
  const date = typeof instant === 'number' || typeof instant === 'string' ? new Date(instant) : instant;
  const formatter = new Intl.DateTimeFormat('en-US', {
    timeZone,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hour12: false,
  });

  const parts = formatter.formatToParts(date);
  const p: Record<string, string> = {};
  for (const part of parts) {
    p[part.type] = part.value;
  }

  let year = parseInt(p.year, 10);
  let month = parseInt(p.month, 10);
  let day = parseInt(p.day, 10);
  let hour = parseInt(p.hour, 10);
  if (hour === 24) hour = 0;

  if (hour < DAY_ROLLOVER_HOUR) {
    const prev = new Date(Date.UTC(year, month - 1, day - 1));
    year = prev.getUTCFullYear();
    month = prev.getUTCMonth() + 1;
    day = prev.getUTCDate();
  }

  const yStr = String(year).padStart(4, '0');
  const mStr = String(month).padStart(2, '0');
  const dStr = String(day).padStart(2, '0');
  return `${yStr}-${mStr}-${dStr}`;
}

/**
 * Evaluates the effective local date as an epoch day number.
 */
export function effectiveEpochDay(
  instant: number | Date | string,
  timeZone: string
): number {
  return toEpochDay(effectiveLocalDate(instant, timeZone));
}
