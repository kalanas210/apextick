import type { Metadata } from "next";
import { Suspense } from "react";
import { EventTable } from "@/components/admin/event-table";

export const metadata: Metadata = {
  title: "Events",
  description: "Manage the event catalog.",
};

export default function AdminEventsPage() {
  // useSearchParams needs a Suspense boundary to keep the route statically shell-rendered.
  return (
    <Suspense fallback={null}>
      <EventTable />
    </Suspense>
  );
}
