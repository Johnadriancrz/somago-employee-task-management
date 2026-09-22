import type { ReactNode } from "react";
import { AdminAuthProvider } from "@/lib/admin-auth";
import { ConfirmProvider } from "@/lib/confirm";

/**
 * Deliberately separate from src/app/(app)/layout.tsx — the admin area does
 * not mount AuthProvider's workspace-user session, BoardProvider, or
 * ClockProvider. It's its own tool with its own login (AdminAuthProvider).
 */
export default function AdminLayout({ children }: { children: ReactNode }) {
  return (
    <AdminAuthProvider>
      <ConfirmProvider>{children}</ConfirmProvider>
    </AdminAuthProvider>
  );
}
