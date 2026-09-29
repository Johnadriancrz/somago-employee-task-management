/**
 * Frontend mirror of the New Task drawer's required fields, scoped to
 * exactly what CreateTaskRequest (backend) already enforces as @NotBlank for
 * fields the form leaves user-editable: title (also @Size(max = 255)),
 * start, and end. group/status/priority/dueDate are always populated with
 * valid defaults by the form itself, so they can't go invalid here.
 */
export interface NewTaskFormValues {
  title: string;
  start: string;
  end: string;
}

const MAX_TITLE_LENGTH = 255;

export function isNewTaskFormValid(form: NewTaskFormValues): boolean {
  const title = form.title.trim();
  return (
    title.length > 0 &&
    title.length <= MAX_TITLE_LENGTH &&
    form.start.trim().length > 0 &&
    form.end.trim().length > 0
  );
}
