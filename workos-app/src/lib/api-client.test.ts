import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import {
  fetchCompletedTasks,
  fetchNotifications,
  fetchUnreadNotificationCount,
  markAllNotificationsReadRequest,
  markNotificationReadRequest,
  SPRING_API_BASE_URL,
} from "./api-client";
import type { CompletedTaskReport, Notification } from "./types";

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

function notification(overrides: Partial<Notification> = {}): Notification {
  return {
    id: "notif-1",
    eventType: "CLOCK_IN",
    actorId: "person-1",
    actorName: "John Adrian Cruz",
    message: "John Adrian Cruz clocked in.",
    read: false,
    createdAt: "2026-01-01T00:00:00.000Z",
    ...overrides,
  };
}

describe("Notifications API", () => {
  beforeEach(() => {
    vi.stubGlobal("fetch", vi.fn());
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("fetchNotifications requests GET /api/notifications on the Spring backend", async () => {
    (fetch as ReturnType<typeof vi.fn>).mockResolvedValue({
      ok: true,
      status: 200,
      json: async () => [notification()],
    });

    const result = await fetchNotifications();

    expect(fetch).toHaveBeenCalledWith(
      `${SPRING_API_BASE_URL}/api/notifications`,
      expect.objectContaining({ credentials: "include" }),
    );
    expect(result).toEqual([notification()]);
  });

  it("fetchUnreadNotificationCount unwraps the { count } response to a plain number", async () => {
    (fetch as ReturnType<typeof vi.fn>).mockResolvedValue({
      ok: true,
      status: 200,
      json: async () => ({ count: 3 }),
    });

    const result = await fetchUnreadNotificationCount();

    expect(fetch).toHaveBeenCalledWith(
      `${SPRING_API_BASE_URL}/api/notifications/unread-count`,
      expect.objectContaining({ credentials: "include" }),
    );
    expect(result).toBe(3);
  });

  it("markNotificationReadRequest PATCHes /api/notifications/:id/read", async () => {
    (fetch as ReturnType<typeof vi.fn>).mockResolvedValue({
      ok: true,
      status: 200,
      json: async () => notification({ read: true }),
    });

    const result = await markNotificationReadRequest("notif-1");

    expect(fetch).toHaveBeenCalledWith(
      `${SPRING_API_BASE_URL}/api/notifications/notif-1/read`,
      expect.objectContaining({ method: "PATCH", credentials: "include" }),
    );
    expect(result).toEqual(notification({ read: true }));
  });

  it("markAllNotificationsReadRequest PATCHes /api/notifications/read-all", async () => {
    (fetch as ReturnType<typeof vi.fn>).mockResolvedValue({
      ok: true,
      status: 204,
      json: async () => null,
    });

    await markAllNotificationsReadRequest();

    expect(fetch).toHaveBeenCalledWith(
      `${SPRING_API_BASE_URL}/api/notifications/read-all`,
      expect.objectContaining({ method: "PATCH", credentials: "include" }),
    );
  });
});
