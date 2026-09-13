import type { Metadata } from "next";
import { EventEditor } from "@/components/admin/event-editor";

export const metadata: Metadata = {
  title: "Edit event",
  description: "Edit an event.",
};

export default async function EditEventPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  return <EventEditor id={Number(id)} />;
}
