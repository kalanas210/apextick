import type { Metadata } from "next";
import { AdminShell } from "@/components/admin/admin-shell";

/**
 * Declared once here and inherited by every /admin segment: none of this belongs
 * in a search index.
 */
export const metadata: Metadata = {
  title: { default: "Admin", template: "%s, Admin" },
  robots: { index: false, follow: false, nocache: true },
};

export default function AdminLayout({ children }: { children: React.ReactNode }) {
  return <AdminShell>{children}</AdminShell>;
}
