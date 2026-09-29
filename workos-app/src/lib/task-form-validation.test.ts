import { describe, expect, it } from "vitest";
import { isNewTaskFormValid } from "./task-form-validation";

describe("isNewTaskFormValid", () => {
  it("is false for a completely empty form", () => {
    expect(isNewTaskFormValid({ title: "", start: "", end: "" })).toBe(false);
  });

  it("is false when a required field is missing", () => {
    expect(isNewTaskFormValid({ title: "Ship the report", start: "2026-10-01", end: "" })).toBe(false);
    expect(isNewTaskFormValid({ title: "Ship the report", start: "", end: "2026-10-05" })).toBe(false);
    expect(isNewTaskFormValid({ title: "", start: "2026-10-01", end: "2026-10-05" })).toBe(false);
  });

  it("is false when a required value is invalid", () => {
    // Whitespace-only title trims to empty.
    expect(isNewTaskFormValid({ title: "   ", start: "2026-10-01", end: "2026-10-05" })).toBe(false);
    // Over the backend's 255-character title limit.
    expect(
      isNewTaskFormValid({ title: "a".repeat(256), start: "2026-10-01", end: "2026-10-05" }),
    ).toBe(false);
  });

  it("is true when all required fields are present and valid", () => {
    expect(
      isNewTaskFormValid({ title: "Ship the report", start: "2026-10-01", end: "2026-10-05" }),
    ).toBe(true);
    // Exactly at the 255-character limit is still valid.
    expect(
      isNewTaskFormValid({ title: "a".repeat(255), start: "2026-10-01", end: "2026-10-05" }),
    ).toBe(true);
  });
});
