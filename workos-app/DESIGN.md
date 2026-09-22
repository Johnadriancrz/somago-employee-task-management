---
name: Kinetic Work OS
description: Vibrant modern-SaaS productivity system for WorkOS — high-density data views (table, kanban, gantt, dashboard) built on a 60/30/10 palette (white/neutral, brick-red, electric-indigo accent) and kinetic, expo-eased micro-motion.
colors:
  primary: "#a13f44"
  primary-container: "#ac484c"
  on-primary-container: "#ffffff"
  primary-fixed: "#fadad9"
  on-primary-fixed: "#470007"
  secondary: "#775454"
  secondary-container: "#fed5d4"
  on-secondary-container: "#7b5a59"
  accent: "#6161ff"
  accent-container: "#e3e3ff"
  on-accent-container: "#2b2b8f"
  tertiary: "#006d3b"
  error: "#ba1a1a"
  error-container: "#ffdad6"
  on-error-container: "#93000a"
  on-surface: "#1c1917"
  on-surface-variant: "#57534e"
  outline: "#78716c"
  outline-variant: "#d6d3d1"
  background: "#ffffff"
  surface-subtle: "#fafaf9"
  surface-container: "#f5f5f4"
  surface-container-high: "#efedec"
  surface-container-highest: "#e7e5e4"
  surface-sidebar: "#492626"
  canvas-bg: "#ffffff"
  border-subtle: "#ececeb"
  border-dark: "#d6d3d1"
  status-done: "#00ca72"
  status-working: "#fdab3d"
  status-stuck: "#e2445c"
  status-empty: "#c4c4c4"
typography:
  display:
    fontFamily: "Inter, ui-sans-serif, system-ui, sans-serif"
    fontSize: "36px"
    fontWeight: 700
    lineHeight: "44px"
    letterSpacing: "-0.02em"
  headline-lg:
    fontFamily: "Inter, ui-sans-serif, system-ui, sans-serif"
    fontSize: "28px"
    fontWeight: 600
    lineHeight: "34px"
    letterSpacing: "-0.015em"
  headline-md:
    fontFamily: "Inter, ui-sans-serif, system-ui, sans-serif"
    fontSize: "20px"
    fontWeight: 600
    lineHeight: "26px"
    letterSpacing: "-0.01em"
  headline-sm:
    fontFamily: "Inter, ui-sans-serif, system-ui, sans-serif"
    fontSize: "16px"
    fontWeight: 600
    lineHeight: "22px"
  body-lg:
    fontFamily: "Inter, ui-sans-serif, system-ui, sans-serif"
    fontSize: "16px"
    fontWeight: 400
    lineHeight: "24px"
  body-md:
    fontFamily: "Inter, ui-sans-serif, system-ui, sans-serif"
    fontSize: "14px"
    fontWeight: 400
    lineHeight: "20px"
  body-sm:
    fontFamily: "Inter, ui-sans-serif, system-ui, sans-serif"
    fontSize: "13px"
    fontWeight: 400
    lineHeight: "18px"
  label-lg:
    fontFamily: "Inter, ui-sans-serif, system-ui, sans-serif"
    fontSize: "14px"
    fontWeight: 500
    lineHeight: "20px"
  label-md:
    fontFamily: "Inter, ui-sans-serif, system-ui, sans-serif"
    fontSize: "12px"
    fontWeight: 500
    lineHeight: "16px"
  label-sm:
    fontFamily: "Inter, ui-sans-serif, system-ui, sans-serif"
    fontSize: "11px"
    fontWeight: 600
    lineHeight: "14px"
    letterSpacing: "0.04em"
  data-cell:
    fontFamily: "Inter, ui-sans-serif, system-ui, sans-serif"
    fontSize: "13px"
    fontWeight: 400
    lineHeight: "18px"
  caption:
    fontFamily: "Inter, ui-sans-serif, system-ui, sans-serif"
    fontSize: "11px"
    fontWeight: 400
    lineHeight: "14px"
rounded:
  xs: "0.125rem"
  sm: "0.25rem"
  md: "0.375rem"
  lg: "0.5rem"
  xl: "0.75rem"
  full: "9999px"
spacing:
  space-xs: "0.25rem"
  space-sm: "0.5rem"
  space-md: "0.75rem"
  space-lg: "1rem"
  space-xl: "1.5rem"
  gutter: "1rem"
  margin: "1.5rem"
components:
  button-primary:
    backgroundColor: "{colors.primary-container}"
    textColor: "{colors.on-primary-container}"
    rounded: "{rounded.lg}"
    padding: "6px 12px"
  button-primary-hover:
    backgroundColor: "{colors.primary}"
  status-pill-done:
    backgroundColor: "{colors.status-done}"
    textColor: "#ffffff"
    rounded: "{rounded.sm}"
  status-pill-working:
    backgroundColor: "{colors.status-working}"
    textColor: "#ffffff"
    rounded: "{rounded.sm}"
  status-pill-stuck:
    backgroundColor: "{colors.status-stuck}"
    textColor: "#ffffff"
    rounded: "{rounded.sm}"
  status-pill-empty:
    backgroundColor: "{colors.status-empty}"
    textColor: "{colors.on-surface}"
    rounded: "{rounded.sm}"
  kanban-card:
    backgroundColor: "{colors.canvas-bg}"
    rounded: "{rounded.lg}"
    padding: "12px"
  avatar:
    rounded: "{rounded.full}"
    size: "28px"
---

# Design System: Kinetic Work OS

## Overview

**Creative North Star: "The Kinetic Work OS"**

This is a modern-corporate productivity system built to carry a lot of simultaneous state — status, ownership, priority, timeline, capacity — without visual fatigue. The palette follows a deliberate 60/30/10 ratio: true white/neutral-gray surfaces dominate (~60%), the muted brick-red brand color is the clear secondary presence (~30% — sidebar, primary actions, tags, selection), and a single electric-indigo accent carries interactive emphasis (~10% — focus rings, active-filter/search highlight). The four semantic status colors (done/working/stuck/empty) sit outside this decorative ratio entirely. Density is high by design: 36–44px table rows, 13px data cells, 11px uppercase labels, but a consistent 4px/8px spatial rhythm keeps that density legible rather than cramped.

The system was inherited from a pre-approved Stitch export ("Kinetic Work OS," `stitch-export/code/design-system.md`) and ported into Tailwind v4 `@theme` tokens — type ramp, radii, and spacing carry the original names and values unchanged. The color ramp was rebalanced from the inherited version to the 60/30/10 ratio above (surfaces moved from a warm blush tint to true neutral gray; a new `accent` token pair was added) per explicit user direction; the brick-red hue family itself, the four semantic status colors, and the type/spacing/radius system are otherwise untouched. What this build adds on top is a motion layer (`motion`, expo-out easing `cubic-bezier(0.16, 1, 0.3, 1)`) that makes the system feel alive: shared-layout tab underlines, `layoutId`-based Kanban drag transitions, spring-feeling status-pill presses, and orchestrated collapse/expand — motion is applied to existing surfaces, never used to invent new ones.

**Key Characteristics:**
- Ultra-clean white/near-white canvases with a true neutral-gray surface family (`surface-subtle`, `surface-container` through `surface-container-highest`) for tonal layering instead of borders alone — the 60% majority color.
- Brick red (`primary-container` `#AC484C` / `primary` `#A13F44`) as the 30% secondary: the icon rail, primary actions, tag chips, selection, and progress fills — present throughout, never a background fill for informational content.
- Electric indigo (`accent` `#6161FF`) as the 10% accent, reserved for one job: interactive emphasis (focus rings app-wide, search/input focus glow). Chosen to match the original brand-accent commitment in PRODUCT.md rather than repurposing the reserved tertiary green, which already carries the "done" status meaning.
- Four solid, high-recognition semantic status colors carry all task-state meaning across all four views identically.
- Every state transition (status change, drag, tab switch, group collapse, KPI count-up) animates on the same expo-out curve; motion is structural, not decorative.
- Light mode only — no dark theme exists anywhere in the tokens or components.

## Colors

The palette follows a 60/30/10 ratio: a true neutral scale for surfaces (60%), a muted brick-red secondary (30%), and one electric-indigo accent (10%) — plus four solid semantic status colors that never bleed into decorative use and sit outside the ratio.

### Primary (30%)
- **Brick Red** (`primary-container` `#AC484C`, `primary` `#A13F44`): the icon rail (`surface-sidebar`), active view-switcher tab + its underline, primary buttons (New Task, Add Widget), progress-bar fills, active toolbar toggles (e.g. Kanban's "Priority" sort pill), and drag-target ring highlight.
- **Brick Red Fixed** (`primary-fixed` `#FADAD9` / `on-primary-fixed` `#470007`): reserved tint for a person's avatar chip and the icon-rail logo mark.

### Accent (10%)
- **Electric Indigo** (`accent` `#6161FF`, `accent-container` `#E3E3FF` / `on-accent-container` `#2B2B8F`): the one interactive-emphasis color — every keyboard `:focus-visible` outline app-wide, every text input's focus ring, and text selection. Never used for brand identity, primary actions, or status — those stay in the primary-red and status-color families respectively, so the accent's presence stays legible as "you are interacting with this" rather than competing with brand red.

### Secondary
- **Warm Taupe** (`secondary` `#775454`, `secondary-container` `#FED5D4`): Kanban card tag chips ("Ops", "Tech", "Strategy") and secondary metadata accents — part of the same red hue family as Primary, contributing to its 30% share.

### Tertiary
- **Signal Green** (`tertiary` `#006D3B`, distinct from `status-done`): reserved token in the inherited scale; not observed in active use in the shipped views (status completion uses `status-done` instead, and the new `accent` token owns the "second interactive color" role). Kept for system completeness, not exercised.

### Neutral (60%)
- **Ink** (`on-surface` `#1C1917`): primary text, titles, task names.
- **Warm Text** (`on-surface-variant` `#57534E`, `secondary` `#775454`): metadata, column headers, secondary copy.
- **Outline** (`outline` `#78716C`, `outline-variant` `#D6D3D1`): icon glyphs at rest, dividers, unfilled star/priority glyphs.
- **Canvas** (`canvas-bg` `#FFFFFF`): the flat white base for cards, table rows, panels.
- **Surface Subtle** (`surface-subtle` `#FAFAF9`): table header rows, hover states, toolbar chip backgrounds.
- **Surface Container family** (`surface-container` `#F5F5F4` → `surface-container-highest` `#E7E5E4`): true neutral tonal layering for nested chips, count badges, and Kanban column backgrounds — used instead of adding more border lines as density increases. Deliberately neutral gray, not red-tinted, so it reads as part of the 60% majority rather than diluting the 30% red share.
- **Sidebar Ink** (`surface-sidebar` `#492626`): the sole dark surface in the system, exclusive to the icon rail and the Kanban "board synced" status pill — counted toward the 30% red share since it's a shade within the brand hue.
- **Border Subtle** (`border-subtle` `#ECECEB`): the 1px grid line between table cells, groups, and toolbar sections.

### Named Rules
**The 60/30/10 Rule.** Surfaces (background, canvas, the full surface-container scale) stay true neutral gray and form the visual majority. Brick red (`primary`/`primary-container`/`secondary`/`surface-sidebar`) is the clear secondary presence — the icon rail, primary actions, tags, and selection all draw from it, but it is never used as a background fill for large blocks of informational content; that role belongs to the neutral surface-container scale. It is kept deliberately muted (mid-lightness, moderate chroma) so it reads distinctly from the brighter, more saturated `status-stuck` red used for blocked-task alerts — the two never get confused despite sharing a hue family. Electric indigo (`accent`) is reserved exclusively for interactive emphasis (focus/selection) and never appears as brand identity or a primary action.

**The Solid Status Rule.** `status-done`/`working`/`stuck`/`empty` are always solid fills with white (or near-white) text, never tinted or outlined, so a status reads at a glance across Table, Kanban, Timeline, and Dashboard without re-learning per view.

## Typography

**Display/Body/Label Font:** Inter (`ui-sans-serif, system-ui, sans-serif` fallback) — the only typeface in the system, at every size from 36px display down to 11px caption.

**Character:** A single-family system built for data density: weight does the differentiating work (400/500/600/700), not family switching, so dense grids (table cells, Kanban badges, Gantt bars) stay visually calm.

### Hierarchy
- **Display** (700, 36px/44px, −0.02em): KPI tile values on the Dashboard only (e.g. "11", "36%").
- **Headline-lg** (600, 28px/34px, −0.015em): the board title ("Q3 Project Overview").
- **Headline-md** (600, 20px/26px, −0.01em): SummaryWidget stat values on Table view.
- **Headline-sm** (600, 16px/22px): active view-tab label, Kanban column headers, card titles, section headers on Dashboard tiles.
- **Body-md/sm** (400, 14px/20px or 13px/18px): descriptions, activity-feed text, secondary copy.
- **Data-cell** (400, 13px/18px): dense grid values — dates, counts — anywhere tabular alignment matters. Paired with `.tabular-nums` for numeric columns.
- **Label-sm** (600, 11px/14px, +0.04em, uppercase): column headers ("OWNER", "STATUS"), KPI tile eyebrow labels, status-pill text.

### Named Rules
**The One-Family Rule.** Inter is the only font in the system, at every size. Hierarchy is carried entirely by weight, size, and letter-spacing — never by introducing a second typeface.

## Layout

The shell is a fixed split: a 48px icon rail (`surface-sidebar`, dark, the system's one dark surface) plus a 240px board/workspace tree (`Sidebar`, white, collapses on screens below `md`), a 56px fixed top bar, and a fluid canvas below that hosts the active view. Board-level chrome (title, tabs, toolbar) is `px-space-md` on mobile and `px-space-xl` on `md+`, establishing an 8px-grid gutter that scales with viewport.

Each of the four views manages its own internal density independently: Table uses a fixed seven-column grid (`minmax(280px,1.5fr) 100px 140px 170px 110px 120px 40px`) with 36–44px rows and a `md:hidden` → mobile-card fallback below `md`; Kanban is a 1/2/4-column responsive grid (`grid-cols-1 md:grid-cols-2 xl:grid-cols-4`) of `surface-container-low` columns; Timeline is a fixed 288px task pane plus a horizontally-scrollable date canvas with independent zoom; Dashboard is a `1 / 2 / 4`-column KPI row over a `1 / 2`-column content grid. Internal padding and gaps consistently pull from the named spacing scale (`space-xs` 4px through `space-xl` 24px, `gutter` 16px, `margin` 24px) — no arbitrary pixel gaps outside that scale in the reusable chrome.

Mobile (<768px) collapses the icon rail's sidebar entirely, stacks the Table view into full-width cards (`TaskCardMobile`), and keeps the icon rail and top bar fixed — confirmed against the shipped 390px screenshot.

## Elevation & Depth

A hybrid strategy: flat 1px borders (`border-subtle`) define the base grid (table cells, toolbar dividers), while low-opacity ambient shadows lift interactive/floating surfaces. There are no hard-offset or neobrutalist-style shadows anywhere in the system — every shadow observed in the shipped code is a soft, low-opacity blur.

### Shadow Vocabulary
- **Flat Grid** (`border: 1px solid var(--color-border-subtle)`): table cell/row separators, toolbar section dividers.
- **Ambient Card** (`shadow-sm`, effectively `0 1px 3px rgba(0,0,0,0.05), 0 1px 2px rgba(0,0,0,0.03)`): Kanban cards, Dashboard tiles, Table group wrapper, sidebar/topbar panels at rest.
- **Hover Lift** (`shadow-md` on hover, e.g. Kanban card `hover:shadow-md`, Dashboard KPI tile `hover:shadow-md`): the response to pointer interaction, never present at rest.
- **Floating Chrome** (`shadow-[0_1px_8px_rgba(0,0,0,0.04)]` / `shadow-[0_1px_4px_rgba(0,0,0,0.02)]`): fixed TopBar, Sidebar, and BoardToolbar, distinguishing fixed/sticky chrome from in-flow content.
- **Elevated Pill** (`shadow-xl`): the floating "Board synced" status pill on Kanban, the one element that hovers above the grid itself.

### Named Rules
**The Ambient-Only Rule.** Shadows are always soft and low-opacity, sized to communicate stacking order (grid < card < fixed chrome < floating pill), never used for graphic/offset effect. No hard-edged or neobrutalist shadow exists in this world; introducing one would be a foreign device, not a system extension.

## Shapes

Radius scales with a component's role, exactly as the inherited spec defines: `xs` (2px) for micro checkboxes, `sm` (4px) for structural/tabular elements (status-pill "full" variant, the WorkOS logo mark), `lg` (8px) for cards, buttons, chips, and containers — the most common radius in the built system — `xl` (12px) for Kanban columns and Dashboard panels, and `full` (9999px) reserved exclusively for avatars, status chips ("chip" variant), count badges, and pill-shaped toggle controls. Borders are hairline (1px, `border-subtle`/`border-dark`) and used structurally (grid lines, input outlines), never decoratively thick.

## Components

### Buttons
- **Shape:** `rounded-lg` (8px) is standard; the split "New Task" button group uses `rounded-lg overflow-hidden` for the joined primary+chevron pair.
- **Primary:** `primary-container` background, `on-primary-container` (white) text, `label-md` weight, `px-space-md py-1.5` — hovers to solid `primary`.
- **Secondary/Ghost:** transparent or `surface-subtle`-adjacent, `on-surface-variant` text, hover fills to `surface-subtle`/`surface-container` with text shifting to `on-surface`. This is the dominant button style across toolbars (Person, Filter, Sort, Hide, Group by).

### Status Pills
- **Style:** solid semantic fill (`status-done`/`working`/`stuck`/`status-empty` gray), white or near-white text, `label-md`, letter-spacing tracking-wide.
- **Variants:** `full` (fills the entire table status cell, `rounded-sm`) vs `chip` (inline rounded badge, `rounded-lg`, used on Kanban's Gantt task pane and mobile cards).
- **Behavior:** clicking cycles the status forward through `not-started → working → stuck → done` with a `whileTap` scale-down (0.95) on the expo-out curve — the pill is a live control, not a static label.

### Cards (Kanban / Dashboard tiles)
- **Corner Style:** `rounded-lg` (8px).
- **Background:** `canvas-bg` (white) sitting on a `surface-container-low` column/panel background — one level of tonal contrast, no border needed.
- **Shadow Strategy:** ambient `shadow-sm` at rest, `shadow-md` + `whileHover={{ y: -2 }}` lift on hover (Kanban cards), `layoutId` shared-element transition when a card's status changes column.
- **Internal Padding:** `p-space-md` (12px).

### Inputs / Search
- **Style:** flat, no visible border at rest inside `surface-subtle` fields (BoardToolbar search) or a `border-dark` outline on white (spec-level convention); `rounded-lg`.
- **Focus:** background shifts to `canvas-bg`/`surface-subtle` and applies a `ring-2 ring-accent/30` glow, app-wide — the 10% accent's primary job, consistent with the global `:focus-visible` outline.

### Avatars
- **Style:** `rounded-full`, initials-based, a role-derived background color, `ring-1 ring-canvas-bg` separation when stacked or placed on colored surfaces. Two sizes only: `sm` (24px) for dense table/mobile rows, default (28px) elsewhere.

### Navigation (Icon Rail / Sidebar / View Tabs)
- **Icon Rail:** the system's one dark surface (`surface-sidebar`), 48px fixed width, icon-only, `hover:bg-white/10`. Active/primary mark uses solid `primary` fill.
- **Sidebar:** white, 240px, hides below `md`; active board item gets a persistent `surface-container-high` + `primary` text fill (not just a hover state); hovered inactive items get a `layoutId`-shared moving highlight (`motion.span layoutId="sidebar-hover"`) rather than a plain background swap.
- **View Switcher Tabs:** underlined-tab pattern — active tab is `primary` text at `headline-sm` weight with a `layoutId`-shared 2px bottom-border indicator (`motion.span layoutId="board-tab-underline"`) that slides between tabs on the expo-out curve, exactly matching the inherited spec's "2px bottom border in `#BF565A`" rule but made a persistent animated element instead of a static one.

### Signature Component: The Kanban Drag Transition
Dragging a card between status columns is backed by a shared `layoutId={task.id}` on each `KanbanCard`; `motion` interpolates the card's position/size across the DOM re-parent so the card appears to physically travel between columns rather than disappearing and re-appearing. Combined with a `ring-2 ring-primary-container` drop-target highlight on `dragOver`, this is the system's clearest expression of "motion is structural."

## Do's and Don'ts

### Do:
- **Do** keep the palette at its 60/30/10 ratio: neutral surfaces dominant, brick red (`primary`/`primary-container`/`secondary`/`surface-sidebar`) as the clear secondary, `accent` reserved for interactive emphasis only. Don't let red creep into large background fills, and don't let accent creep into brand/primary-action roles.
- **Do** use solid semantic status colors (`status-done`/`working`/`stuck`/`empty`) with white text for any state indicator — never a tinted/outlined variant for the same meaning.
- **Do** animate state transitions (status change, drag, tab switch, collapse, count-up) on the shared expo-out curve (`cubic-bezier(0.16, 1, 0.3, 1)`); a transition without this curve reads as foreign to the system.
- **Do** use `layoutId`-based shared-element transitions for anything that moves between two visual containers (Kanban drag, tab underline, sidebar hover) rather than crossfading.
- **Do** reserve `rounded-full` exclusively for avatars, status chips, and count/pill badges; everything else uses `sm`/`lg`/`xl` per its structural role.

### Don't:
- **Don't** introduce a second typeface. Inter carries the entire hierarchy through weight and size alone.
- **Don't** use hard-offset or neobrutalist-style shadows. Every shadow in this system is a soft, low-opacity ambient blur; a heavy offset shadow would be a foreign device in this world.
- **Don't** add a dark-mode variant. The system is confirmed light-mode-only at the token level (`html { color-scheme: light }`, no dark media query) and at the brand-commitment level.
- **Don't** treat a single-use inline shadow or arbitrary pixel value as a token candidate — this file documents only values reused across two or more components (e.g. the specific `shadow-[0_1px_8px_rgba(0,0,0,0.04)]` fixed-chrome shadow is a repeated, named pattern; a one-off inline style elsewhere in a view is not elevated here).
