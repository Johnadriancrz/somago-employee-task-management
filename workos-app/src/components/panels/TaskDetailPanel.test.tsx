// @vitest-environment jsdom
import { afterEach, describe, expect, it, vi } from "vitest";
import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { TaskForm } from "./TaskDetailPanel";
import type { Person } from "@/lib/types";

const people: Person[] = [
  {
    id: "person-1",
    name: "Ann Employee",
    email: "ann@example.com",
    initials: "AE",
    role: "Employee",
    chipClass: "bg-accent text-on-accent",
    accessRole: null,
  },
];

const closePanel = vi.fn();
const updateTask = vi.fn();
const addTask = vi.fn();
const deleteTask = vi.fn();

vi.mock("@/lib/store", () => ({
  useBoard: () => ({
    closePanel,
    updateTask,
    addTask,
    deleteTask,
    people,
    personById: (id: string) => people.find((p) => p.id === id) ?? people[0],
  }),
}));

vi.mock("@/lib/auth", () => ({
  useAuth: () => ({ user: { id: "person-1", accessRole: null } }),
}));

vi.mock("@/lib/confirm", () => ({
  useConfirm: () => vi.fn().mockResolvedValue(true),
}));

afterEach(() => {
  cleanup();
  closePanel.mockClear();
  updateTask.mockClear();
  addTask.mockClear();
  deleteTask.mockClear();
});

function renderNewTaskForm() {
  const utils = render(<TaskForm editingTask={null} />);
  const titleInput = screen.getByPlaceholderText("Task title...") as HTMLInputElement;
  const dateInputs = utils.container.querySelectorAll('input[type="date"]');
  const startInput = dateInputs[0] as HTMLInputElement;
  const endInput = dateInputs[1] as HTMLInputElement;
  const createButton = screen.getByRole("button", { name: "Create task" }) as HTMLButtonElement;
  return { ...utils, titleInput, startInput, endInput, createButton };
}

describe("New task Create button", () => {
  it("is disabled on an empty form", () => {
    const { createButton } = renderNewTaskForm();
    expect(createButton.disabled).toBe(true);
  });

  it("stays disabled when a required field is missing", () => {
    const { titleInput, startInput, createButton } = renderNewTaskForm();
    fireEvent.change(titleInput, { target: { value: "Ship the report" } });
    fireEvent.change(startInput, { target: { value: "2026-10-01" } });
    // end left blank
    expect(createButton.disabled).toBe(true);
  });

  it("stays disabled when a required value is invalid", () => {
    const { titleInput, startInput, endInput, createButton } = renderNewTaskForm();
    fireEvent.change(titleInput, { target: { value: "   " } }); // trims to empty
    fireEvent.change(startInput, { target: { value: "2026-10-01" } });
    fireEvent.change(endInput, { target: { value: "2026-10-05" } });
    expect(createButton.disabled).toBe(true);
  });

  it("enables once every required field is valid", () => {
    const { titleInput, startInput, endInput, createButton } = renderNewTaskForm();
    fireEvent.change(titleInput, { target: { value: "Ship the report" } });
    fireEvent.change(startInput, { target: { value: "2026-10-01" } });
    fireEvent.change(endInput, { target: { value: "2026-10-05" } });
    expect(createButton.disabled).toBe(false);
  });

  it("cannot create a task by clicking Create while disabled", () => {
    const { titleInput, createButton } = renderNewTaskForm();
    fireEvent.change(titleInput, { target: { value: "Ship the report" } });
    // start/end left blank — button stays disabled
    fireEvent.click(createButton);
    expect(addTask).not.toHaveBeenCalled();
    expect(closePanel).not.toHaveBeenCalled();
  });

  it("still creates the task once the form is valid", () => {
    const { titleInput, startInput, endInput, createButton } = renderNewTaskForm();
    fireEvent.change(titleInput, { target: { value: "Ship the report" } });
    fireEvent.change(startInput, { target: { value: "2026-10-01" } });
    fireEvent.change(endInput, { target: { value: "2026-10-05" } });
    fireEvent.click(createButton);

    expect(addTask).toHaveBeenCalledTimes(1);
    expect(addTask).toHaveBeenCalledWith(
      expect.objectContaining({
        title: "Ship the report",
        start: "2026-10-01",
        end: "2026-10-05",
      }),
    );
    expect(closePanel).toHaveBeenCalledTimes(1);
  });
});
