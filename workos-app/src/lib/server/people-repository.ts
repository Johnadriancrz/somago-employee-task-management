import { PEOPLE } from "@/lib/data";
import { DEMO_PASSWORD } from "./demo-credentials";
import type { NewPersonInput, NewPersonInputWithPassword, Person } from "@/lib/types";

/** The internal record — password never leaves this file's return values. */
interface StoredPerson extends Person {
  password: string;
}

/** Same stub-backend shape as board-repository.ts — see the comment there. */
let people: StoredPerson[] = seedFromPeople();
let nextSeq = 1;

function seedFromPeople(): StoredPerson[] {
  // Every seeded account shares DEMO_PASSWORD, same as before this file
  // tracked passwords at all — only accounts created through /admin get a
  // real, distinct password.
  return clone(PEOPLE).map((p: Person) => ({ ...p, password: DEMO_PASSWORD }));
}

/** Rotates through the same avatar-chip palette the seed data uses. */
const CHIP_CLASSES = [
  "bg-secondary-container text-on-secondary-container",
  "bg-primary-fixed text-on-primary-fixed",
  "bg-surface-container-high text-primary",
  "bg-surface-container-highest text-secondary",
  "bg-primary/10 text-primary",
  "bg-tertiary-container/15 text-tertiary",
];

function clone<T>(value: T): T {
  return JSON.parse(JSON.stringify(value));
}

function withoutPassword({ password, ...rest }: StoredPerson): Person {
  void password;
  return clone(rest);
}

function slugify(name: string): string {
  return (
    name
      .toLowerCase()
      .trim()
      .replace(/[^a-z0-9]+/g, "-")
      .replace(/(^-|-$)/g, "") || "person"
  );
}

function initialsFor(name: string): string {
  const parts = name.trim().split(/\s+/).filter(Boolean);
  const initials = (parts[0]?.[0] ?? "") + (parts[parts.length - 1]?.[0] ?? "");
  return (initials || "?").toUpperCase();
}

export function listPeople(): Person[] {
  return people.map(withoutPassword);
}

export function getPerson(personId: string): Person | null {
  const person = people.find((p) => p.id === personId);
  return person ? withoutPassword(person) : null;
}

export function getPersonByEmail(email: string): Person | null {
  const person = people.find((p) => p.email.toLowerCase() === email.trim().toLowerCase());
  return person ? withoutPassword(person) : null;
}

/** The only function that ever looks at a password — used exclusively by the login route. */
export function verifyPersonCredentials(email: string, password: string): Person | null {
  const person = people.find((p) => p.email.toLowerCase() === email.trim().toLowerCase());
  if (!person || person.password !== password) return null;
  return withoutPassword(person);
}

export function createPerson(input: NewPersonInputWithPassword): Person {
  const base = slugify(input.name);
  const id = people.some((p) => p.id === base) ? `${base}-${nextSeq++}` : base;
  const person: StoredPerson = {
    ...input,
    id,
    initials: input.initials || initialsFor(input.name),
    chipClass: input.chipClass || CHIP_CLASSES[people.length % CHIP_CLASSES.length],
  };
  people = [...people, person];
  return withoutPassword(person);
}

export function updatePerson(
  personId: string,
  patch: Partial<NewPersonInput> & { password?: string },
): Person | null {
  let updated: StoredPerson | null = null;
  people = people.map((person) => {
    if (person.id !== personId) return person;
    updated = { ...person, ...patch, id: person.id };
    return updated;
  });
  return updated ? withoutPassword(updated) : null;
}

export function deletePerson(personId: string): boolean {
  if (!people.some((p) => p.id === personId)) return false;
  people = people.filter((p) => p.id !== personId);
  return true;
}

export function resetAllPeople(): Person[] {
  people = seedFromPeople();
  return listPeople();
}
