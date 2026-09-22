# Product

<!-- impeccable:product-schema 1 -->

## Platform

web

## Stack

Next.js (App Router) + React + Tailwind CSS v4. User specified this stack explicitly. Motion/interaction layer: `motion` (Framer Motion's successor), chosen for Emil Kowalski-style animation craft (spring physics, careful easing, orchestrated micro-interactions) per user request.

## Users

Cross-functional product/engineering teams (PMs, engineers, designers, ops) coordinating shared work inside a workspace tool ("WorkOS"). The immediate demo scenario is a single board, "Q3 Project Overview," used to track cross-functional engineering deliverables, sprint milestones, and launch velocity across a quarter.

## Product Purpose

A Monday.com/Asana-style work management tool. Teams track tasks across four interchangeable views of the same underlying board: a grouped Table (spreadsheet-like rows with status, owner, timeline, due date, priority), a Kanban board (status-grouped cards), a Timeline/Gantt (date-scaled bars with dependencies), and a Dashboard (KPIs, status distribution, team workload, upcoming milestones, activity feed). Success means a viewer can switch views and immediately read the same project state through whichever lens suits the task at hand.

## Positioning

Distinguishing mechanism: one board, four fully-realized, view-native representations of the same task set (not a single view with alternate skins) — table/kanban/timeline/dashboard each has real, view-appropriate interaction (grouping/collapse, drag-by-status, date-scale zoom, KPI drill-in), inheriting a single shared design system ("Kinetic Work OS") across all four.

## Operating Context

- Workspace shell: collapsible icon rail + expandable board/workspace tree (left), global search + integrations/automation shortcuts + profile (top).
- Single board "Q3 Project Overview" with a view switcher (Main Table / Timeline / Kanban / Dashboard) and a shared toolbar (New Task, search, Person/Filter/Sort, Hide/Group by).
- Table view: two date groups ("This month" / "Next month"), each with 4 tasks, collapsible, with per-group status mini-graphs and aggregate footers.
- Kanban view: 4 status columns (Not Started / Working on it / Stuck / Done) with task cards carrying tags, subtask progress, due dates, owner avatars, blockers.
- Timeline/Gantt view: left task hierarchy pane synced to a right date-scaled canvas with dependency arrows, a "today" marker, and scale controls (Days/Weeks/Months/Quarters, zoom).
- Dashboard view: 4 KPI tiles, a status-distribution donut, team workload bars per person, upcoming milestones list, and a live activity feed.

## Capabilities and Constraints

- **Functional scope (confirmed with user):** interactive frontend only — real React state drives view switching, group collapse, sorting/filtering affordances, and Kanban drag-and-drop, but data lives in memory/mock only. No backend, no persistence, no auth.
- Content/data (task names, owners, dates, statuses, KPI numbers) comes from the approved Stitch export at `stitch-export/` and should be treated as the real seed dataset for this demo, not placeholder to be reinvented.
- Design tokens (colors, type scale, spacing, radii) are fixed by the existing "Kinetic Work OS" design system (`stitch-export/code/design-system.md`) — not open for reinvention.
- Icons in the source mockups use Google Material Symbols; equivalent icons may be substituted if it materially improves build quality, but the icon vocabulary (per-feature meaning) should be preserved.

## Brand Commitments

- Product name: "WorkOS". Primary accent color `#6161FF` (electric indigo), Inter typeface, light mode only, `rounded-sm`-leaning roundness per the brand's own logo metadata.
- Design system name: "Kinetic Work OS" — vibrant modern SaaS productivity aesthetic, ultra-clean white canvases, high-contrast typographic hierarchy, semantic status colors (done/working/stuck/empty).

## Evidence on Hand

- Full-fidelity HTML/Tailwind mockups for all 4 views at `stitch-export/code/*.html` (structure, copy, and real interaction affordances already specified by the user's Stitch export — this is production-grade visual authority, not a rough sketch).
- Matching screenshots at `stitch-export/images/*.png`.
- Design system spec at `stitch-export/code/design-system.md` (full color/typography/spacing/component tokens).
- Absence: no real backend, API, or persistence layer exists or is being built in this pass (see Capabilities and Constraints).

## Product Principles

1. The four views are equally real — no view is a stub or a lesser reflection of another; each gets its own view-native interaction.
2. Inherit the given design system exactly; do not re-invent palette, type, or spacing — this world was already chosen by the user via Stitch.
3. Motion is structural, not decorative: view switches, drag-and-drop, status changes, and collapses should feel physically coherent (spring-based, interruptible, orchestrated) rather than tacked-on CSS transitions.
4. Content fidelity: reuse the real task names, owners, and dates from the Stitch export so the four views visibly describe the same underlying project.
