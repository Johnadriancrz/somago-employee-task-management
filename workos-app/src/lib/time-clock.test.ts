import { describe, expect, it } from "vitest";
import { getTimeClockVisiblePeople } from "./time-clock";
import type { Person } from "./types";

function person(id: string, accessRole: string | null): Person {
  return {
    id,
    name: id,
    email: `${id}@example.com`,
    initials: id.slice(0, 2).toUpperCase(),
    role: accessRole ?? "Employee",
    chipClass: "",
    accessRole,
  };
}

const ceo = person("ceo-1", "CEO");
const hr = person("hr-1", "HR");
const it_ = person("it-1", "IT");
const directory = [ceo, hr, it_];

describe("getTimeClockVisiblePeople", () => {
  it("excludes the CEO from the all-employee (canViewAll) Time Clock table", () => {
    const visible = getTimeClockVisiblePeople(directory, true, ceo.id);

    expect(visible.map((p) => p.id)).toEqual([hr.id, it_.id]);
  });

  it("keeps every other non-exempt role visible under canViewAll", () => {
    const visible = getTimeClockVisiblePeople(directory, true, hr.id);

    expect(visible).toContainEqual(hr);
    expect(visible).toContainEqual(it_);
  });

  it("never returns the CEO even when the CEO is the signed-in user without canViewAll", () => {
    // Defensive: CEO always has canViewAll=true in practice (canViewAllTimeEntries),
    // but the exemption filter must not depend on that to hold.
    const visible = getTimeClockVisiblePeople(directory, false, ceo.id);

    expect(visible).toEqual([]);
  });

  it("scopes a non-privileged employee to only their own row", () => {
    const visible = getTimeClockVisiblePeople(directory, false, it_.id);

    expect(visible).toEqual([it_]);
  });

  it("does not mutate or drop non-exempt employees from the directory", () => {
    const visible = getTimeClockVisiblePeople(directory, true, hr.id);

    expect(visible).toHaveLength(2);
    expect(directory).toHaveLength(3);
  });
});
