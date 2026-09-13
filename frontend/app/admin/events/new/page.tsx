import type { Metadata } from "next";
import { EventCreate } from "@/components/admin/event-create";

export const metadata: Metadata = {
  title: "New event",
  description: "Create an event.",
};

export default function NewEventPage() {
  return <EventCreate />;
}
