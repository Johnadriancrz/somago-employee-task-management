import { isExemptFromPersonalTimeClock } from "./roles";
import type { Person } from "./types";

/**
 * The employee population shown in the Time Clock attendance table: the
 * caller's own row (or every row when `canViewAll`, per CEO/HR access)
 * minus anyone exempt from personal time tracking (spec section 9) — an
 * exempt person (currently only CEO) never clocks in, so they never belong
 * in the attendance table even though they remain a real employee account
 * everywhere else (e.g. Admin's Employee Accounts page, which must keep
 * showing them and is unaffected by this filter).
 */
export function getTimeClockVisiblePeople(
  employeeDirectory: Person[],
  canViewAll: boolean,
  userId: string | undefined,
): Person[] {
  const nonExempt = employeeDirectory.filter((p) => !isExemptFromPersonalTimeClock(p.accessRole));
  if (canViewAll) return nonExempt;
  return nonExempt.filter((p) => p.id === userId);
}
