import { describe, expect, it } from "vitest";
import { canManageAllWorkspaces, canManageWorkspace } from "./roles";

describe("canManageAllWorkspaces", () => {
  it("is true only for CEO", () => {
    expect(canManageAllWorkspaces("CEO")).toBe(true);
    expect(canManageAllWorkspaces("Operation Manager")).toBe(false);
    expect(canManageAllWorkspaces(null)).toBe(false);
    expect(canManageAllWorkspaces(undefined)).toBe(false);
  });
});

describe("canManageWorkspace", () => {
  const workspace = { ownerId: "owner-1" };

  it("lets a CEO manage a workspace they neither own nor belong to", () => {
    expect(canManageWorkspace("CEO", workspace, "someone-else")).toBe(true);
  });

  it("lets the owner manage their own workspace", () => {
    expect(canManageWorkspace("Employee", workspace, "owner-1")).toBe(true);
  });

  it("denies a non-owner, non-CEO actor", () => {
    expect(canManageWorkspace("Employee", workspace, "someone-else")).toBe(false);
  });

  it("denies an Operation Manager who does not own the workspace", () => {
    // Spec section 12/20 item 5: OM's broader management scope beyond
    // owned workspaces is an open product decision, intentionally not
    // granted here — matches the backend's owner-or-CEO gate exactly.
    expect(canManageWorkspace("Operation Manager", workspace, "someone-else")).toBe(false);
  });

  it("denies a signed-out/unknown actor (no userId)", () => {
    expect(canManageWorkspace("Employee", workspace, undefined)).toBe(false);
    expect(canManageWorkspace(null, workspace, null)).toBe(false);
  });
});
