# Copy & Paste Assignments in Timeline Grid

## Overview

Add clipboard-based copy/paste for assignments in the usage timeline grid. Users can Cmd+Click to select specific assignments, then click target cells to paste them — across architects and months.

## Interaction Flow

### State 1: Normal Mode

Everything works as today. Click an assignment opens usage editor. Drag from sidebar assigns a project. A subtle tooltip at the bottom hints at Cmd+Click for copy.

### State 2: Selecting (Cmd+Click)

- **Cmd+Click** (Ctrl+Click on Windows/Linux) on an assignment block toggles its selection
- Selected assignments get a highlighted style: blue background (#60a5fa), 2px solid border (#2563eb), white text, checkmark (✓) prefix
- Can select assignments from **different cells, different architects, different months**
- A **clipboard bar** appears at the bottom of the grid:
  - Shows: `📋 N assignments copied — click cells to paste • Esc to cancel`
  - Has a `✕ Clear` button on the right
- Normal click (without Cmd) still opens the usage editor — no conflict
- Cmd+Click on an already-selected assignment deselects it
- If all selections are removed, clipboard bar disappears and returns to normal mode

### State 3: Pasting (Click target cells)

- While clipboard has items, clicking any **month cell** pastes all clipboard assignments into that cell
- Each click creates assignments immediately (API calls)
- Clipboard data for each assignment: `{ projectId, tentative, usagePercent }`
- **Duplicate handling**: if the same project is already assigned to the same architect for the same month, that assignment is skipped silently
- Target cells that don't yet have the pasted projects show a dashed outline hint during paste mode
- Clipboard bar updates: `📋 N assignments — pasted to M cells so far • Esc to finish`
- Clipboard **stays active** after each paste — user can keep clicking more cells
- Can still Cmd+Click to add/remove assignments from clipboard during paste mode

### Exiting Paste Mode

- Press **Escape** key
- Click **✕ Done** button on clipboard bar
- Clipboard clears, all visual states revert to normal

## Visual Design

### Selected Assignment Block
- Background: #60a5fa (darker blue)
- Border: 2px solid #2563eb
- Text: white, bold
- Prefix: ✓ checkmark

### Clipboard Bar
- Position: bottom of `.timeline-grid`, inside the component
- Background: #eff6ff
- Border: 1px solid #bfdbfe
- Border-radius: 6px
- Padding: 8px 12px
- Content: left-aligned status text, right-aligned clear/done button

### Target Cell Hints (during paste mode)
- Cells without all clipboard projects: dashed outline (2px dashed #93c5fd, outline-offset: -2px)
- Cells that already have all clipboard projects: no special treatment

## Data Model

No backend changes needed. Paste operation reuses existing assignment creation endpoint — one API call per assignment per target cell. The clipboard is purely frontend state.

### Clipboard State (Vue ref)

```typescript
interface ClipboardItem {
  projectId: number
  tentative: boolean
  usagePercent: number
}

const clipboard = ref<ClipboardItem[]>([])
```

### Paste Logic

For each target cell click:
1. Get existing assignments for that architect+month
2. Filter clipboard items to exclude duplicates (same projectId already assigned)
3. Call `emit('drop', architectId, month, item)` for each remaining item
4. The parent view handles API calls (same as drag-and-drop flow)

## Keyboard Shortcuts

| Key | Action |
|-----|--------|
| Cmd/Ctrl + Click | Toggle assignment selection |
| Escape | Exit paste mode, clear clipboard |

## Edge Cases

- **Selecting from sidebar drag while clipboard active**: Drag-and-drop from sidebar still works normally during paste mode. Only cell clicks trigger paste.
- **Usage editor during paste mode**: Normal click on an assignment block opens the usage editor as before (only Cmd+Click selects). During paste mode, clicking the **cell background** (the `<td>`) triggers paste — clicking an **assignment block** (the `<div>` inside) opens the editor instead. This works because assignment blocks already use `@click.stop` to prevent event bubbling to the cell.
- **Over-allocation**: Pasting may cause total usage > 100%. Grid already handles this with red background coloring. No blocking — user decides.
- **Empty clipboard**: If user Cmd+Clicks to deselect all items, mode exits automatically.

## Scope

### In Scope
- Cmd+Click selection of assignment blocks
- Clipboard bar UI
- Click-to-paste on target cells
- Duplicate skipping
- Escape to exit
- Visual feedback (selected state, target cell hints)

### Out of Scope
- Keyboard-only paste (Cmd+V) — not needed, click-to-paste is simpler
- Undo/redo of paste operations
- Cut (move) assignments — only copy
- Persisting clipboard across page navigation
