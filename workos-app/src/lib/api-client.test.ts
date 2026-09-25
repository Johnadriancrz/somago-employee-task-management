import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { fetchCompletedTasks, SPRING_API_BASE_URL } from "./api-client";
import type { CompletedTaskReport } from "./types";

function completedTask(overrides: Partial<CompletedTaskReport> = {}): CompletedTaskReport {
  return {
    taskId: "task-1",
    title: "Ship the report",
    boardId: "board-1",
    boardName: "Engineering",
    ownerId: "person-1",
    assigneeIds: [],
    dueDate: "Sep 19",
    end: "2025-09-19",
    ...overrides,
  };
}

describe("fetchCompletedTasks", () => {
  beforeEach(() => {
    vi.stubGlobal("fetch", vi.fn());
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("requests GET /api/reports/completed-tasks on the Spring backend, with credentials", async () => {
    (fetch as ReturnType<typeof vi.fn>).mockResolvedValue({
      ok: true,
      status: 200,
      json: async () => [completedTask()],
    });

    const result = await fetchCompletedTasks();

    expect(fetch).toHaveBeenCalledWith(
      `${SPRING_API_BASE_URL}/api/reports/completed-tasks`,
      expect.objectContaining({ credentials: "include" }),
    );
    expect(result).toEqual([completedTask()]);
  });

  it("rejects when the Spring backend responds with a non-OK status", async () => {
    (fetch as ReturnType<typeof vi.fn>).mockResolvedValue({
      ok: false,
      status: 500,
      json: async () => ({ error: "Internal server error" }),
    });

    await expect(fetchCompletedTasks()).rejects.toThrow("Internal server error");
  });

  it("rejects (rather than resolving to an empty list) when the request itself fails", async () => {
    (fetch as ReturnType<typeof vi.fn>).mockRejectedValue(new Error("ECONNREFUSED"));

    await expect(fetchCompletedTasks()).rejects.toThrow("ECONNREFUSED");
  });
});
