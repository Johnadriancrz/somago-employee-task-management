"use client";

import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
} from "react";
import {
  addWorkspaceMemberRequest,
  createBoardRequest,
  createTaskRequest,
  createWorkspaceRequest,
  deleteBoardRequest,
  deleteTaskRequest,
  deleteWorkspaceRequest,
  fetchAllTasks,
  fetchBoards,
  fetchPeople,
  fetchWorkspaces,
  removeWorkspaceMemberRequest,
  resetAllDataRequest,
  updateTaskRequest,
} from "./api-client";
import { useAuth } from "./auth";
import { BOARDS, DEFAULT_WORKSPACE_ID, PEOPLE, TASKS_BY_BOARD, WORKSPACES } from "./data";
import type {
  BoardId,
  BoardMeta,
  NewBoardInput,
  NewTaskInput,
  NewWorkspaceInput,
  Person,
  Status,
  Task,
  TaskGroup,
  Workspace,
  WorkspaceId,
} from "./types";

export type ViewId = "table" | "timeline" | "kanban" | "dashboard";
export type SortBy = "none" | "dueDate" | "priority" | "title";
export type GroupBy = "timeline" | "status" | "owner" | "priority" | "none";

interface BoardContextValue {
  workspace: Workspace | undefined;
  workspaces: Workspace[];
  activeWorkspaceId: WorkspaceId;
  setActiveWorkspace: (id: WorkspaceId) => void;
  createWorkspace: (input: NewWorkspaceInput) => void;
  /** Also deletes every board (and task) in the workspace. */
  deleteWorkspace: (id: WorkspaceId) => void;
  /** Adds an existing active account to a workspace. Owner-only server-side. */
  addWorkspaceMember: (workspaceId: WorkspaceId, personId: string) => void;
  /** Removes a member from a workspace. Owner-only server-side; the owner itself can't be removed. */
  removeWorkspaceMember: (workspaceId: WorkspaceId, personId: string) => void;
  /**
   * Local-only for now — there's no `/api/workspaces/:id` PATCH endpoint
   * yet (only list/create exist). Once one does, wrap this in the same
   * optimistic-update-then-request pattern `deleteBoard` uses.
   */
  updateWorkspace: (id: WorkspaceId, patch: Partial<NewWorkspaceInput>) => void;
  board: BoardMeta | undefined;
  /** Boards in the active workspace only. */
  boards: BoardMeta[];
  activeBoardId: BoardId;
  setActiveBoard: (id: BoardId) => void;
  createBoard: (input: Omit<NewBoardInput, "workspaceId">) => void;
  deleteBoard: (boardId: BoardId) => void;
  favoriteBoardIds: Set<BoardId>;
  toggleFavoriteBoard: (boardId: BoardId) => void;
  showNewBoardDialog: boolean;
  openNewBoardDialog: () => void;
  closeNewBoardDialog: () => void;
  showNewWorkspaceDialog: boolean;
  openNewWorkspaceDialog: () => void;
  closeNewWorkspaceDialog: () => void;
  /** Tasks for boards in the active workspace only, keyed by board id — for cross-board pages (My Work, Reports hub). */
  tasksByBoard: Record<BoardId, Task[]>;
  tasks: Task[];
  activeView: ViewId;
  setActiveView: (view: ViewId) => void;
  setStatus: (taskId: string, status: Status) => void;
  updateTask: (taskId: string, patch: Partial<Task>) => void;
  addTask: (input: NewTaskInput) => string;
  deleteTask: (taskId: string) => void;
  searchQuery: string;
  setSearchQuery: (query: string) => void;
  filterStatuses: Set<Status>;
  toggleFilterStatus: (status: Status) => void;
  filterOwnerIds: Set<string>;
  toggleFilterOwnerId: (personId: string) => void;
  clearFilters: () => void;
  sortBy: SortBy;
  setSortBy: (sortBy: SortBy) => void;
  groupBy: GroupBy;
  setGroupBy: (groupBy: GroupBy) => void;
  visibleTasks: Task[];
  /** The task currently open in the detail panel, or "new" while creating one. */
  activePanel: { mode: "view" | "new"; taskId: string | null; group?: TaskGroup; status?: Status } | null;
  openTask: (taskId: string) => void;
  /** Switches to the given board first, then opens the task — for opening a task found on a non-active board. */
  openTaskOnBoard: (boardId: BoardId, taskId: string) => void;
  openNewTask: (defaults?: { group?: TaskGroup; status?: Status }) => void;
  closePanel: () => void;
  people: Person[];
  personById: (personId: string) => Person;
  /**
   * Local-only for now — there's no people-update endpoint yet. Once one
   * exists, wrap this in the same optimistic-update-then-request pattern
   * `updateTask` uses.
   */
  updatePerson: (personId: string, patch: Partial<Person>) => void;
  /**
   * Wipes the real backend state (Workspaces/Boards/Tasks/People) via
   * `POST /api/reset` and applies its response to local state. Throws if
   * the backend call fails — callers must catch and surface the error
   * rather than assume success.
   */
  resetAllData: () => Promise<void>;
}

const BoardContext = createContext<BoardContextValue | null>(null);

let nextTempSeq = 1;

const FALLBACK_PERSON: Person = {
  id: "unknown",
  name: "Unknown",
  email: "",
  initials: "?",
  role: "",
  chipClass: "bg-surface-container-high text-secondary",
};

const EMPTY_TASKS: Task[] = [];

function firstBoardId(boards: BoardMeta[], workspaceId: WorkspaceId): BoardId {
  return boards.find((b) => b.workspaceId === workspaceId)?.id ?? "";
}

export function BoardProvider({ children }: { children: React.ReactNode }) {
  const { user } = useAuth();
  const [activeWorkspaceId, setActiveWorkspaceIdState] = useState<WorkspaceId>(DEFAULT_WORKSPACE_ID);
  const [activeBoardId, setActiveBoardIdState] = useState<BoardId>("q3-overview");
  // Seed data renders first (identical on server and client, so hydration
  // always matches); an effect below then swaps in the live data from the
  // API once it lands, the same way this used to swap in localStorage.
  const [workspaces, setWorkspaces] = useState<Workspace[]>(WORKSPACES);
  const [allBoards, setAllBoards] = useState<BoardMeta[]>(BOARDS);
  const [allTasksByBoard, setAllTasksByBoard] = useState<Record<BoardId, Task[]>>(TASKS_BY_BOARD);
  const [people, setPeople] = useState<Person[]>(PEOPLE);
  const [activeView, setActiveView] = useState<ViewId>("table");
  const [searchQuery, setSearchQuery] = useState("");
  const [filterStatuses, setFilterStatuses] = useState<Set<Status>>(new Set());
  const [filterOwnerIds, setFilterOwnerIds] = useState<Set<string>>(new Set());
  const [sortBy, setSortBy] = useState<SortBy>("none");
  const [groupBy, setGroupBy] = useState<GroupBy>("timeline");
  const [activePanel, setActivePanel] = useState<BoardContextValue["activePanel"]>(null);
  const [showNewBoardDialog, setShowNewBoardDialog] = useState(false);
  const [showNewWorkspaceDialog, setShowNewWorkspaceDialog] = useState(false);
  const [favoriteBoardIds, setFavoriteBoardIds] = useState<Set<BoardId>>(new Set());

  useEffect(() => {
    let cancelled = false;
    // Each resource loads independently — a failure fetching one (e.g.
    // Workspaces from Spring Boot) must not block the others from landing.
    fetchWorkspaces()
      .then((workspacesData) => {
        if (!cancelled) setWorkspaces(workspacesData);
      })
      .catch((err) => {
        console.error("Failed to load workspaces from the API, staying on seed data", err);
      });
    fetchBoards()
      .then((boardsData) => {
        if (!cancelled) setAllBoards(boardsData);
      })
      .catch((err) => {
        console.error("Failed to load boards from the API, staying on seed data", err);
      });
    fetchAllTasks()
      .then((tasksData) => {
        if (!cancelled) setAllTasksByBoard(tasksData);
      })
      .catch((err) => {
        console.error("Failed to load tasks from the API, staying on seed data", err);
      });
    fetchPeople()
      .then((peopleData) => {
        if (!cancelled) setPeople(peopleData);
      })
      .catch((err) => {
        console.error("Failed to load people from the API, staying on seed data", err);
      });
    return () => {
      cancelled = true;
    };
  }, []);

  const workspace = workspaces.find((w) => w.id === activeWorkspaceId);
  const boards = useMemo(
    () => allBoards.filter((b) => b.workspaceId === activeWorkspaceId),
    [allBoards, activeWorkspaceId],
  );
  const tasksByBoard = useMemo(() => {
    const scoped: Record<BoardId, Task[]> = {};
    for (const b of boards) scoped[b.id] = allTasksByBoard[b.id] ?? EMPTY_TASKS;
    return scoped;
  }, [boards, allTasksByBoard]);
  const tasks = tasksByBoard[activeBoardId] ?? EMPTY_TASKS;
  const board = boards.find((b) => b.id === activeBoardId);

  const setActiveWorkspace = useCallback(
    (id: WorkspaceId) => {
      setActiveWorkspaceIdState(id);
      setActiveBoardIdState(firstBoardId(allBoards, id));
      setSearchQuery("");
      setFilterStatuses(new Set());
      setFilterOwnerIds(new Set());
      setSortBy("none");
      setGroupBy("timeline");
      setActivePanel(null);
    },
    [allBoards],
  );

  const setActiveBoard = useCallback((id: BoardId) => {
    setActiveBoardIdState(id);
    setSearchQuery("");
    setFilterStatuses(new Set());
    setFilterOwnerIds(new Set());
    setSortBy("none");
    setGroupBy("timeline");
    setActivePanel(null);
  }, []);

  const toggleFilterStatus = useCallback((status: Status) => {
    setFilterStatuses((prev) => {
      const next = new Set(prev);
      if (next.has(status)) {
        next.delete(status);
      } else {
        next.add(status);
      }
      return next;
    });
  }, []);

  const toggleFilterOwnerId = useCallback((personId: string) => {
    setFilterOwnerIds((prev) => {
      const next = new Set(prev);
      if (next.has(personId)) {
        next.delete(personId);
      } else {
        next.add(personId);
      }
      return next;
    });
  }, []);

  const clearFilters = useCallback(() => {
    setFilterStatuses(new Set());
    setFilterOwnerIds(new Set());
  }, []);

  const personById = useCallback(
    (personId: string) => people.find((p) => p.id === personId) ?? FALLBACK_PERSON,
    [people],
  );

  const visibleTasks = useMemo(() => {
    const q = searchQuery.trim().toLowerCase();
    let result = q ? tasks.filter((t) => t.title.toLowerCase().includes(q)) : tasks;
    if (filterStatuses.size > 0) result = result.filter((t) => filterStatuses.has(t.status));
    if (filterOwnerIds.size > 0) result = result.filter((t) => filterOwnerIds.has(t.ownerId));
    if (sortBy !== "none") {
      result = [...result].sort((a, b) => {
        if (sortBy === "dueDate") return a.end < b.end ? -1 : a.end > b.end ? 1 : 0;
        if (sortBy === "priority") return b.priority - a.priority;
        return a.title.localeCompare(b.title);
      });
    }
    return result;
  }, [tasks, searchQuery, filterStatuses, filterOwnerIds, sortBy]);

  // Every mutation below applies its change to local state immediately
  // (so the UI never waits on the network), then fires the matching API
  // request in the background and rolls the local change back if the
  // request fails. Swapping api-client's implementation for a real backend
  // needs no changes here.
  const updateTask = useCallback(
    (taskId: string, patch: Partial<Task>) => {
      const boardId = activeBoardId;
      const stamped: Partial<Task> = { ...patch, updatedAt: Date.now() };
      let previous: Task | undefined;
      setAllTasksByBoard((prev) => {
        const idx = (prev[boardId] ?? []).findIndex((t) => t.id === taskId);
        if (idx === -1) return prev;
        previous = prev[boardId][idx];
        const next = [...prev[boardId]];
        next[idx] = { ...previous, ...stamped };
        return { ...prev, [boardId]: next };
      });
      updateTaskRequest(taskId, stamped).catch((err) => {
        console.error("Failed to update task", err);
        const rollback = previous;
        if (!rollback) return;
        setAllTasksByBoard((prev) => ({
          ...prev,
          [boardId]: (prev[boardId] ?? []).map((t) => (t.id === taskId ? rollback : t)),
        }));
      });
    },
    [activeBoardId],
  );

  const setStatus = useCallback(
    (taskId: string, status: Status) => {
      updateTask(taskId, { status, ...(status === "done" ? { progress: 100 } : {}) });
    },
    [updateTask],
  );

  const addTask = useCallback(
    (input: NewTaskInput) => {
      const boardId = activeBoardId;
      const tempId = `temp-${Date.now()}-${nextTempSeq++}`;
      setAllTasksByBoard((prev) => ({
        ...prev,
        [boardId]: [...(prev[boardId] ?? []), { ...input, id: tempId }],
      }));
      createTaskRequest(boardId, input)
        .then((created) => {
          setAllTasksByBoard((prev) => ({
            ...prev,
            [boardId]: (prev[boardId] ?? []).map((t) => (t.id === tempId ? created : t)),
          }));
        })
        .catch((err) => {
          console.error("Failed to create task", err);
          setAllTasksByBoard((prev) => ({
            ...prev,
            [boardId]: (prev[boardId] ?? []).filter((t) => t.id !== tempId),
          }));
        });
      return tempId;
    },
    [activeBoardId],
  );

  const deleteTask = useCallback(
    (taskId: string) => {
      const boardId = activeBoardId;
      let removed: { task: Task; index: number } | undefined;
      setAllTasksByBoard((prev) => {
        const index = (prev[boardId] ?? []).findIndex((t) => t.id === taskId);
        if (index === -1) return prev;
        removed = { task: prev[boardId][index], index };
        return { ...prev, [boardId]: prev[boardId].filter((t) => t.id !== taskId) };
      });
      deleteTaskRequest(taskId).catch((err) => {
        console.error("Failed to delete task", err);
        const restore = removed;
        if (!restore) return;
        setAllTasksByBoard((prev) => {
          const next = [...(prev[boardId] ?? [])];
          next.splice(restore.index, 0, restore.task);
          return { ...prev, [boardId]: next };
        });
      });
    },
    [activeBoardId],
  );

  const createBoard = useCallback(
    (input: Omit<NewBoardInput, "workspaceId">) => {
      const workspaceId = activeWorkspaceId;
      const fullInput: NewBoardInput = { ...input, workspaceId };
      const tempId = `temp-board-${Date.now()}-${nextTempSeq++}`;
      const optimistic: BoardMeta = { ...fullInput, id: tempId };
      setAllBoards((prev) => [...prev, optimistic]);
      setAllTasksByBoard((prev) => ({ ...prev, [tempId]: [] }));
      setActiveBoard(tempId);
      createBoardRequest(fullInput)
        .then((created) => {
          setAllBoards((prev) => prev.map((b) => (b.id === tempId ? created : b)));
          setAllTasksByBoard((prev) => {
            const { [tempId]: existing, ...rest } = prev;
            return { ...rest, [created.id]: existing ?? [] };
          });
          setActiveBoardIdState((current) => (current === tempId ? created.id : current));
        })
        .catch((err) => {
          console.error("Failed to create board", err);
          setAllBoards((prev) => prev.filter((b) => b.id !== tempId));
          setAllTasksByBoard((prev) => {
            const next = { ...prev };
            delete next[tempId];
            return next;
          });
        });
    },
    [activeWorkspaceId, setActiveBoard],
  );

  const deleteBoard = useCallback(
    (boardId: BoardId) => {
      let removedBoard: { board: BoardMeta; index: number } | undefined;
      let removedTasks: Task[] | undefined;
      setAllBoards((prev) => {
        const index = prev.findIndex((b) => b.id === boardId);
        if (index === -1) return prev;
        removedBoard = { board: prev[index], index };
        return prev.filter((b) => b.id !== boardId);
      });
      setAllTasksByBoard((prev) => {
        removedTasks = prev[boardId];
        const next = { ...prev };
        delete next[boardId];
        return next;
      });
      setActiveBoardIdState((current) => {
        if (current !== boardId) return current;
        const remaining = allBoards.filter((b) => b.id !== boardId && b.workspaceId === activeWorkspaceId);
        return remaining[0]?.id ?? "";
      });
      setFavoriteBoardIds((prev) => {
        if (!prev.has(boardId)) return prev;
        const next = new Set(prev);
        next.delete(boardId);
        return next;
      });
      deleteBoardRequest(boardId).catch((err) => {
        console.error("Failed to delete board", err);
        const restore = removedBoard;
        if (!restore) return;
        setAllBoards((prev) => {
          const next = [...prev];
          next.splice(restore.index, 0, restore.board);
          return next;
        });
        setAllTasksByBoard((prev) => ({ ...prev, [boardId]: removedTasks ?? [] }));
      });
    },
    [allBoards, activeWorkspaceId],
  );

  const toggleFavoriteBoard = useCallback((boardId: BoardId) => {
    setFavoriteBoardIds((prev) => {
      const next = new Set(prev);
      if (next.has(boardId)) {
        next.delete(boardId);
      } else {
        next.add(boardId);
      }
      return next;
    });
  }, []);

  const createWorkspace = useCallback(
    (input: NewWorkspaceInput) => {
      const tempId = `temp-workspace-${Date.now()}-${nextTempSeq++}`;
      const ownerId = user?.id ?? "";
      const optimistic: Workspace = { ...input, id: tempId, ownerId, memberIds: [ownerId] };
      setWorkspaces((prev) => [...prev, optimistic]);
      setActiveWorkspaceIdState(tempId);
      setActiveBoardIdState("");
      setSearchQuery("");
      setActivePanel(null);
      createWorkspaceRequest(input)
        .then((created) => {
          setWorkspaces((prev) => prev.map((w) => (w.id === tempId ? created : w)));
          setActiveWorkspaceIdState((current) => (current === tempId ? created.id : current));
        })
        .catch((err) => {
          console.error("Failed to create workspace", err);
          setWorkspaces((prev) => prev.filter((w) => w.id !== tempId));
          setActiveWorkspaceIdState((current) => (current === tempId ? DEFAULT_WORKSPACE_ID : current));
        });
    },
    [user],
  );

  const deleteWorkspace = useCallback(
    (workspaceId: WorkspaceId) => {
      let removedWorkspace: { workspace: Workspace; index: number } | undefined;
      let removedBoards: BoardMeta[] = [];
      const removedTasksByBoard: Record<BoardId, Task[]> = {};

      setWorkspaces((prev) => {
        const index = prev.findIndex((w) => w.id === workspaceId);
        if (index === -1) return prev;
        removedWorkspace = { workspace: prev[index], index };
        return prev.filter((w) => w.id !== workspaceId);
      });
      setAllBoards((prev) => {
        removedBoards = prev.filter((b) => b.workspaceId === workspaceId);
        return prev.filter((b) => b.workspaceId !== workspaceId);
      });
      setAllTasksByBoard((prev) => {
        const next = { ...prev };
        for (const board of removedBoards) {
          removedTasksByBoard[board.id] = next[board.id];
          delete next[board.id];
        }
        return next;
      });
      setFavoriteBoardIds((prev) => {
        const removedIds = new Set(removedBoards.map((b) => b.id));
        if (![...prev].some((id) => removedIds.has(id))) return prev;
        const next = new Set(prev);
        for (const id of removedIds) next.delete(id);
        return next;
      });
      if (activeWorkspaceId === workspaceId) {
        const remaining = workspaces.filter((w) => w.id !== workspaceId);
        const nextWorkspaceId = remaining[0]?.id ?? DEFAULT_WORKSPACE_ID;
        setActiveWorkspaceIdState(nextWorkspaceId);
        setActiveBoardIdState(firstBoardId(allBoards.filter((b) => b.workspaceId !== workspaceId), nextWorkspaceId));
        setSearchQuery("");
        setFilterStatuses(new Set());
        setFilterOwnerIds(new Set());
        setSortBy("none");
        setGroupBy("timeline");
        setActivePanel(null);
      }

      deleteWorkspaceRequest(workspaceId).catch((err) => {
        console.error("Failed to delete workspace", err);
        const restore = removedWorkspace;
        if (!restore) return;
        setWorkspaces((prev) => {
          const next = [...prev];
          next.splice(restore.index, 0, restore.workspace);
          return next;
        });
        setAllBoards((prev) => [...prev, ...removedBoards]);
        setAllTasksByBoard((prev) => ({ ...prev, ...removedTasksByBoard }));
      });
    },
    [workspaces, allBoards, activeWorkspaceId],
  );

  const updateWorkspace = useCallback((id: WorkspaceId, patch: Partial<NewWorkspaceInput>) => {
    setWorkspaces((prev) => prev.map((w) => (w.id === id ? { ...w, ...patch } : w)));
  }, []);

  const addWorkspaceMember = useCallback((workspaceId: WorkspaceId, personId: string) => {
    let previous: Workspace | undefined;
    setWorkspaces((prev) =>
      prev.map((w) => {
        if (w.id !== workspaceId) return w;
        previous = w;
        return w.memberIds.includes(personId) ? w : { ...w, memberIds: [...w.memberIds, personId] };
      }),
    );
    addWorkspaceMemberRequest(workspaceId, personId)
      .then((updated) => {
        setWorkspaces((prev) => prev.map((w) => (w.id === workspaceId ? updated : w)));
      })
      .catch((err) => {
        console.error("Failed to add workspace member", err);
        setWorkspaces((prev) => prev.map((w) => (w.id === workspaceId ? (previous ?? w) : w)));
      });
  }, []);

  const removeWorkspaceMember = useCallback((workspaceId: WorkspaceId, personId: string) => {
    let previous: Workspace | undefined;
    setWorkspaces((prev) =>
      prev.map((w) => {
        if (w.id !== workspaceId) return w;
        previous = w;
        return { ...w, memberIds: w.memberIds.filter((id) => id !== personId) };
      }),
    );
    removeWorkspaceMemberRequest(workspaceId, personId)
      .then((updated) => {
        setWorkspaces((prev) => prev.map((w) => (w.id === workspaceId ? updated : w)));
      })
      .catch((err) => {
        console.error("Failed to remove workspace member", err);
        setWorkspaces((prev) => prev.map((w) => (w.id === workspaceId ? (previous ?? w) : w)));
      });
  }, []);

  const updatePerson = useCallback((personId: string, patch: Partial<Person>) => {
    setPeople((prev) => prev.map((p) => (p.id === personId ? { ...p, ...patch } : p)));
  }, []);

  const openTask = useCallback((taskId: string) => {
    setActivePanel({ mode: "view", taskId });
  }, []);

  const openTaskOnBoard = useCallback((boardId: BoardId, taskId: string) => {
    setActiveBoardIdState(boardId);
    setSearchQuery("");
    setActivePanel({ mode: "view", taskId });
  }, []);

  const openNewTask = useCallback((defaults?: { group?: TaskGroup; status?: Status }) => {
    setActivePanel({ mode: "new", taskId: null, group: defaults?.group, status: defaults?.status });
  }, []);

  const closePanel = useCallback(() => setActivePanel(null), []);

  const openNewBoardDialog = useCallback(() => setShowNewBoardDialog(true), []);
  const closeNewBoardDialog = useCallback(() => setShowNewBoardDialog(false), []);
  const openNewWorkspaceDialog = useCallback(() => setShowNewWorkspaceDialog(true), []);
  const closeNewWorkspaceDialog = useCallback(() => setShowNewWorkspaceDialog(false), []);

  /**
   * Resets the real, persisted backend state first (`ResetController`) and
   * only applies its actual response to local state once that succeeds —
   * never optimistically, since a false "reset" here would mean showing the
   * user seed data that doesn't match what the backend actually holds. On
   * failure, local state is left untouched and the error propagates to the
   * caller (the Settings page) instead of being swallowed.
   */
  const resetAllData = useCallback(async () => {
    const result = await resetAllDataRequest();
    setWorkspaces(result.workspaces);
    setAllBoards(result.boards);
    setAllTasksByBoard(result.tasksByBoard);
    setPeople(result.people);
    setActiveWorkspaceIdState(DEFAULT_WORKSPACE_ID);
    setActiveBoardIdState("q3-overview");
    setActiveView("table");
    setSearchQuery("");
    setFilterStatuses(new Set());
    setFilterOwnerIds(new Set());
    setSortBy("none");
    setGroupBy("timeline");
    setActivePanel(null);
  }, []);

  const value = useMemo(
    () => ({
      workspace,
      workspaces,
      activeWorkspaceId,
      setActiveWorkspace,
      createWorkspace,
      deleteWorkspace,
      addWorkspaceMember,
      removeWorkspaceMember,
      updateWorkspace,
      board,
      boards,
      activeBoardId,
      setActiveBoard,
      createBoard,
      deleteBoard,
      favoriteBoardIds,
      toggleFavoriteBoard,
      showNewBoardDialog,
      openNewBoardDialog,
      closeNewBoardDialog,
      showNewWorkspaceDialog,
      openNewWorkspaceDialog,
      closeNewWorkspaceDialog,
      tasksByBoard,
      tasks,
      activeView,
      setActiveView,
      setStatus,
      updateTask,
      addTask,
      deleteTask,
      searchQuery,
      setSearchQuery,
      filterStatuses,
      toggleFilterStatus,
      filterOwnerIds,
      toggleFilterOwnerId,
      clearFilters,
      sortBy,
      setSortBy,
      groupBy,
      setGroupBy,
      visibleTasks,
      activePanel,
      openTask,
      openTaskOnBoard,
      openNewTask,
      closePanel,
      people,
      personById,
      updatePerson,
      resetAllData,
    }),
    [
      workspace,
      workspaces,
      activeWorkspaceId,
      setActiveWorkspace,
      createWorkspace,
      deleteWorkspace,
      addWorkspaceMember,
      removeWorkspaceMember,
      updateWorkspace,
      board,
      boards,
      activeBoardId,
      setActiveBoard,
      createBoard,
      deleteBoard,
      favoriteBoardIds,
      toggleFavoriteBoard,
      showNewBoardDialog,
      openNewBoardDialog,
      closeNewBoardDialog,
      showNewWorkspaceDialog,
      openNewWorkspaceDialog,
      closeNewWorkspaceDialog,
      tasksByBoard,
      tasks,
      activeView,
      setStatus,
      updateTask,
      addTask,
      deleteTask,
      filterStatuses,
      toggleFilterStatus,
      filterOwnerIds,
      toggleFilterOwnerId,
      clearFilters,
      sortBy,
      groupBy,
      searchQuery,
      visibleTasks,
      activePanel,
      openTask,
      openTaskOnBoard,
      openNewTask,
      closePanel,
      people,
      personById,
      updatePerson,
      resetAllData,
    ],
  );

  return (
    <BoardContext.Provider value={value}>{children}</BoardContext.Provider>
  );
}

export function useBoard() {
  const ctx = useContext(BoardContext);
  if (!ctx) throw new Error("useBoard must be used within a BoardProvider");
  return ctx;
}
