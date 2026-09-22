/** The workspace board tree (Sidebar) only makes sense on the board page itself — every other module (Notifications, Chat, My Work, etc.) isn't scoped to a board. */
export function isBoardRoute(pathname: string) {
  return pathname === "/";
}
