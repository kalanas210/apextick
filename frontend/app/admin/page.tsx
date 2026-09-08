import type { Metadata } from "next";
import { AdminDashboard } from "@/components/admin/dashboard";

export const metadata: Metadata = {
  title: "Overview",
  description: "ApexTick admin dashboard.",
};

export default function AdminPage() {
  return <AdminDashboard />;
}
