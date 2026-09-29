export type Status = "not-started" | "working" | "stuck" | "done";

export type TaskGroup = "this-week" | "next-week" | "this-month" | "next-month";

export type WorkspaceId = string;

export interface Workspace {
  id: WorkspaceId;
  name: string;
  initials: string;
  /** The Person who created this workspace — the only one who can manage its member list or delete it. */
  ownerId: string;
  /** Person ids who can see and work in this workspace — always includes ownerId. */
  memberIds: string[];
}

export type NewWorkspaceInput = Omit<Workspace, "id" | "ownerId" | "memberIds">;

/** Boards are created dynamically now — any string id, not a fixed set. */
export type BoardId = string;

/** Which lucide icon a board renders in the sidebar; "generic" is the default for new boards. */
export type BoardIcon = "table" | "kanban" | "bug" | "milestone" | "generic";

export interface BoardMeta {
  id: BoardId;
  workspaceId: WorkspaceId;
  name: string;
  description: string;
  icon: BoardIcon;
}

export type NewBoardInput = Omit<BoardMeta, "id">;

export interface Person {
  id: string;
  name: string;
  email: string;
  initials: string;
  role: string;
  /** Tailwind classes for the avatar chip background + text color. */
  chipClass: string;
  /**
   * One of the 8 fixed employee access roles (see `lib/roles.ts`), or
   * null/undefined until a CEO/Admin explicitly assigns one (spec section
   * 13.3) — a person with no accessRole gets no elevated permissions
   * anywhere. Optional so existing object literals (demo seed data, the
   * legacy admin console) that don't know about this field still typecheck.
   */
  accessRole?: string | null;
}

export type NewPersonInput = Omit<Person, "id">;

/**
 * Only used by admin account creation/password-reset — Person itself never
 * carries a password client-side, and no API response ever includes one.
 */
export interface NewPersonInputWithPassword extends NewPersonInput {
  password: string;
}

export interface Subtask {
  id: string;
  title: string;
  done: boolean;
}

export interface Attachment {
  id: string;
  name: string;
  /** Bytes. */
  size: number;
  type: string;
  /** A data: URI holding the file content — see BACKEND.md for what a real backend swaps this for. */
  dataUrl: string;
}

export interface Task {
  id: string;
  title: string;
  group: TaskGroup;
  status: Status;
  /** Who created/owns the task — set once, distinct from who it's assigned to. */
  ownerId: string;
  /** Who the task is assigned to — can be nobody, one person, or several. */
  assigneeIds?: string[];
  tag?: string;
  priority: 1 | 2 | 3 | 4 | 5;
  dueDate: string;
  /** ISO dates, used by the Timeline/Gantt view. */
  start: string;
  end: string;
  /** 0-100 completion, drives progress bars everywhere. */
  progress: number;
  subtasks?: Subtask[];
  attachments?: Attachment[];
  blocker?: string;
  note?: string;
  /** Id of a task this one depends on (drawn as a dependency arrow in Timeline). */
  dependsOn?: string;
  /** Epoch ms of the last real edit — unset until the task is actually touched, so a fresh demo starts with no activity history rather than a fabricated one. */
  updatedAt?: number;
}

export type NewTaskInput = Omit<Task, "id">;

export type ConversationId = string;

export interface ChatMessage {
  id: string;
  conversationId: ConversationId;
  authorId: string;
  text: string;
  /** ISO datetime. */
  createdAt: string;
}

export interface TimeEntry {
  id: string;
  personId: string;
  /** ISO datetime. */
  clockIn: string;
  /** ISO datetime, or null while still clocked in. */
  clockOut: string | null;
}

/**
 * One row of GET /api/reports/completed-tasks — a thin, server-filtered
 * projection of a completed (`status === "done"`) Task, scoped server-side to
 * the signed-in person's visible boards and, for non-privileged roles, to
 * tasks they own or are assigned to (see BACKEND.md's Reports section). Not
 * a `Task`: it carries only the fields the Reports page renders.
 */
export interface CompletedTaskReport {
  taskId: string;
  title: string;
  boardId: BoardId;
  boardName: string;
  ownerId: string;
  assigneeIds: string[];
  dueDate: string;
  end: string;
}

/** Matches NotificationEventType on the backend exactly — spec section 18 (TASK_ASSIGNED added in section S2). */
export type NotificationEventType =
  | "CHAT_MESSAGE"
  | "CLOCK_IN"
  | "CLOCK_OUT"
  | "TASK_WORKING"
  | "TASK_STUCK"
  | "TASK_DONE"
  | "BOARD_CREATED"
  | "TASK_ASSIGNED";

/** Matches NotificationResponse on the backend exactly (both the REST list and the STOMP payload). */
export interface Notification {
  id: string;
  eventType: NotificationEventType;
  actorId: string;
  actorName: string;
  message: string;
  read: boolean;
  /** ISO datetime. */
  createdAt: string;
}

/**
 * The four Settings notification toggles (Settings Phase S1). Matches
 * NotificationPreferencesResponse on the backend exactly. This is persisted
 * preference state only — it does not yet drive any notification delivery
 * behavior.
 */
export interface NotificationPreferences {
  mentionsEnabled: boolean;
  taskAssignedEnabled: boolean;
  dueSoonEnabled: boolean;
  weeklyDigestEnabled: boolean;
}

export const STATUS_LABEL: Record<Status, string> = {
  "not-started": "Not Started",
  working: "Working on it",
  stuck: "Stuck",
  done: "Done",
};

export const STATUS_ORDER: Status[] = ["not-started", "working", "stuck", "done"];
