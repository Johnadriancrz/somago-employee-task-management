---
name: Kinetic Work OS
colors:
  surface: '#fff7f7'
  surface-dim: '#ecd4d3'
  surface-bright: '#fff7f7'
  surface-container-lowest: '#ffffff'
  surface-container-low: '#ffefef'
  surface-container: '#ffe8e7'
  surface-container-high: '#fbe2e1'
  surface-container-highest: '#f5dddc'
  on-surface: '#281717'
  on-surface-variant: '#554241'
  inverse-surface: '#3d2b2b'
  inverse-on-surface: '#ffeceb'
  outline: '#877171'
  outline-variant: '#d9c0bf'
  surface-tint: '#a13f44'
  primary: '#a13f44'
  on-primary: '#ffffff'
  primary-container: '#bf565a'
  on-primary-container: '#ffffff'
  inverse-primary: '#f0b8b7'
  secondary: '#775454'
  on-secondary: '#ffffff'
  secondary-container: '#fed5d4'
  on-secondary-container: '#7b5a59'
  tertiary: '#006d3b'
  on-tertiary: '#ffffff'
  tertiary-container: '#00894b'
  on-tertiary-container: '#000702'
  error: '#ba1a1a'
  on-error: '#ffffff'
  error-container: '#ffdad6'
  on-error-container: '#93000a'
  primary-fixed: '#fadad9'
  primary-fixed-dim: '#f0b8b7'
  on-primary-fixed: '#470007'
  on-primary-fixed-variant: '#8a202c'
  secondary-fixed: '#fed9d7'
  secondary-fixed-dim: '#e4bcbb'
  on-secondary-fixed: '#2f1213'
  on-secondary-fixed-variant: '#5e3e3d'
  tertiary-fixed: '#5effa0'
  tertiary-fixed-dim: '#39e186'
  on-tertiary-fixed: '#00210e'
  on-tertiary-fixed-variant: '#00522b'
  background: '#fff7f7'
  on-background: '#281717'
  surface-variant: '#f5dddc'
  status-done: '#00CA72'
  status-working: '#FDAB3D'
  status-stuck: '#E2445C'
  status-empty: '#C4C4C4'
  canvas-bg: '#FFFFFF'
  surface-subtle: '#FBF6F6'
  surface-sidebar: '#492626'
  border-subtle: '#F0E7E6'
  border-dark: '#E3CFCF'
typography:
  display:
    fontFamily: Inter
    fontSize: 36px
    fontWeight: '700'
    lineHeight: 44px
    letterSpacing: -0.02em
  headline-lg:
    fontFamily: Inter
    fontSize: 28px
    fontWeight: '600'
    lineHeight: 34px
    letterSpacing: -0.015em
  headline-lg-mobile:
    fontFamily: Inter
    fontSize: 22px
    fontWeight: '600'
    lineHeight: 28px
    letterSpacing: -0.01em
  headline-md:
    fontFamily: Inter
    fontSize: 20px
    fontWeight: '600'
    lineHeight: 26px
    letterSpacing: -0.01em
  headline-sm:
    fontFamily: Inter
    fontSize: 16px
    fontWeight: '600'
    lineHeight: 22px
  body-lg:
    fontFamily: Inter
    fontSize: 16px
    fontWeight: '400'
    lineHeight: 24px
  body-md:
    fontFamily: Inter
    fontSize: 14px
    fontWeight: '400'
    lineHeight: 20px
  body-sm:
    fontFamily: Inter
    fontSize: 13px
    fontWeight: '400'
    lineHeight: 18px
  label-lg:
    fontFamily: Inter
    fontSize: 14px
    fontWeight: '500'
    lineHeight: 20px
  label-md:
    fontFamily: Inter
    fontSize: 12px
    fontWeight: '500'
    lineHeight: 16px
  label-sm:
    fontFamily: Inter
    fontSize: 11px
    fontWeight: '600'
    lineHeight: 14px
    letterSpacing: 0.04em
  data-cell:
    fontFamily: Inter
    fontSize: 13px
    fontWeight: '400'
    lineHeight: 18px
  caption:
    fontFamily: Inter
    fontSize: 11px
    fontWeight: '400'
    lineHeight: 14px
rounded:
  sm: 0.125rem
  DEFAULT: 0.25rem
  md: 0.375rem
  lg: 0.5rem
  xl: 0.75rem
  full: 9999px
spacing:
  gutter: 1rem
  margin: 1.5rem
  space-xs: 0.25rem
  space-sm: 0.5rem
  space-md: 0.75rem
  space-lg: 1rem
  space-xl: 1.5rem
---

## Brand & Style

This design system expresses a vibrant, highly structured modern SaaS productivity platform. The aesthetic merges corporate clarity with punchy chromatic feedback: stark functional efficiency elevated by spirited status indicators and crisp interactive surfaces.

The visual style employs Modern Corporate Productivity with dynamic micro-chromatics:
- **Tone:** Methodical, lively, reliable, and frictionless.
- **Visual Structure:** Ultra-clean white canvases, delicate cool slate dividers, and high-contrast typographic hierarchy designed to handle intense data density without visual fatigue.
- **Emotional Impact:** Fosters instantaneous clarity, control, and team momentum. Complex operations feel approachable, organized, and rewarding through vivid color-state transitions.

## Colors

The color architecture balances a clean, neutral core with vivid semantic status colors:
- **Primary (`#BF565A`):** The signature muted brick red. Used exclusively for core calls-to-action, active view tabs, focused input highlights, selection rings, and interactive progress states.
- **Secondary (`#311314`):** Deep oxblood ink for primary typography, titles, and anchor UI structures.
- **Tertiary (`#00CA72`):** Emerald green signaling completion, positive validation, and completed sprint health.
- **Neutral (`#796463`):** Medium warm taupe for secondary metadata, column headers, icon toggles, and placeholder labels.

### Semantic Status Colors
Data boards leverage high-recognition status fills with solid white text:
- `status-done` (`#00CA72`): Tasks complete or signed off.
- `status-working` (`#FDAB3D`): Active progression, under review, or in-flight tasks.
- `status-stuck` (`#E2445C`): Blockers, high-severity dependencies, or urgent errors.
- `status-empty` (`#C4C4C4`): Unassigned or pending status fields.

### Backgrounds & Borders
- `surface-subtle` (`#FBF6F6`) tints table header rows, card canvas zones, and hover states.
- `surface-sidebar` (`#492626`) delivers focused contrast for multi-tier collapsible workspace menus.
- `border-subtle` (`#F0E7E6`) forms the 1px grid definition between data cells, board groups, and toolbar sections.

## Typography

Inter powers all typographic applications across this design system, ensuring immaculate rendering at compact sizes inside dense multi-column grids, kanban badges, and Gantt charts.

- **Hierarchy & Proportions:** Data grids leverage `data-cell` (13px/18px) for compact readability without sacrificing vertical rhythm. Section titles on boards rely on `headline-md` and `headline-sm`.
- **Labels & Microcopy:** `label-sm` utilizes uppercase casing with `+0.04em` letter-spacing for column category headers (e.g., "OWNER", "STATUS", "DUE DATE", "TIMELINE").
- **Weights:** Restricted strictly to Regular (400) for standard reading, Medium (500) for interactive affordances and navigation items, and Semi-Bold/Bold (600/700) for titles, counters, and structural group names.

## Layout & Spacing

The layout is architected around a flexible, high-density dashboard shell with a split-pane layout model:
- **Left Navigation Drawer:** Global workspace navigation collapsible between an icon rail (48px) and an expanded tree view (240px).
- **Canvas Board Surface:** Fluid grid accommodating horizontal scrolling for high-column matrices (Table View) and fluid distribution across multi-lane Kanban and Gantt/Timeline views.
- **Rhythm:** An 8px spatial grid dominates global panels, while a tightly tuned 4px micro-grid dictates internal table cell paddings, status badge dimensions, and inline toolbars.
- **Breakpoints:**
  - **Desktop (1280px+):** Full workspace rail, contextual filter bars, multi-column board view, side-drawer item peek view.
  - **Tablet (768px - 1279px):** Collapsed navigation rail, sticky horizontal table scroll, floating action bar for batch editing.
  - **Mobile (<768px):** Single-column stacked cards or condensed card lists, full-screen drawer overlays for cell editing, touch-optimized bottom sheets.

## Elevation & Depth

Visual hierarchy uses a hybrid strategy of crisp low-contrast borders and low-opacity ambient shadows, maintaining clean lines inside complex analytical environments:

- **Level 0 (Flat Base):** Canvases (`#FFFFFF`) and container headers (`#FBF6F6`) sit flush, delineated exclusively by 1px solid borders (`#F0E7E6`).
- **Level 1 (Hover & Embedded Cards):** Kanban cards and clickable table cell triggers use subtle boundaries accompanied by an ultra-soft shadow: `0 1px 3px rgba(0, 0, 0, 0.05), 0 1px 2px rgba(0, 0, 0, 0.03)`.
- **Level 2 (Popovers & Context Menus):** Cell editor popovers, status picker grids, and date selectors float above the board with a crisp outline and elevated projection: `0 4px 14px rgba(49, 19, 20, 0.08), 0 2px 6px rgba(49, 19, 20, 0.04)`.
- **Level 3 (Modals & Peek Panels):** Item detail slide-overs and setup modals: `0 12px 28px rgba(49, 19, 20, 0.14), 0 4px 10px rgba(49, 19, 20, 0.06)`.

## Shapes

The design system maintains a balanced, contemporary structural identity using soft geometric profiles (Level 1):
- **Base Components:** Form controls, table action triggers, and Kanban cards adopt `0.25rem` (4px) corner radii for structural stability and seamless tabular alignment.
- **Containers & Modals:** Slide-out drawers, modal windows, and notification banners use `0.5rem` (8px) (`rounded-lg`).
- **Status Badges & Avatar Cells:** Full pill radii (`9999px`) are reserved exclusively for cell status pills, numeric count tags, and assigned teammate avatar circles to maximize contrast against the squared grid.

## Components

### Buttons
- **Primary:** Filled with `#BF565A`, white text, 0.25rem radius, 32px height (table compact) or 38px height (primary actions). Subtle lift on hover (`#AF454A`).
- **Secondary/Outline:** Border 1px `#E3CFCF`, white background, `#311314` text, transitions to `#FBF6F6` background on hover.
- **Split Buttons:** Grouped primary action with an attached chevron trigger for view switching and automations.

### Status Pills & Chips
- **Board Status Cells:** 100% full-width cell fill or centered pill. Solid backgrounds (`#00CA72` for Done, `#FDAB3D` for Working on it, `#E2445C` for Stuck) with bold white text (`label-md`). Hover triggers an instant 3D press effect or a drop-down arrow indicator.
- **Filter & Tag Chips:** `#FBF6F6` background with `#796463` text, enclosed by `#F0E7E6` border. Active filter state shifts to a tinted `#BF565A` outline with primary-colored text.

### Data Table & Grid Elements
- **Rows & Cells:** Row heights fixed at 36px (compact) or 44px (default). Separated by 1px solid horizontal and vertical grid lines (`#F0E7E6`).
- **Group Headers:** Left border accent bar (3px solid matching custom group color), collapsible chevron indicator, and inline item count counters.

### Checkboxes & Selection Controls
- **Checkboxes:** 16px squares with 3px border radius. Unchecked has 1.5px border in `#C4C4C4`. Checked state is solid `#BF565A` containing a crisp white tick mark. Indeterminate states for group selections display a centered minus symbol.

### Input Fields
- **Search & Inline Editors:** Flat white background, 1px border in `#E3CFCF`, padding `0.375rem 0.75rem`. Focus applies an immediate 0 0 0 2px glow in `rgba(191, 86, 90, 0.2)` with a `#BF565A` border.

### Multi-View Board Navigators
- **View Switcher Tabs:** Segmented control bar above the board supporting Table, Kanban, Gantt, and Chart views. Active tab features a 2px bottom border in `#BF565A` with matching primary-toned typography and icon fill.
