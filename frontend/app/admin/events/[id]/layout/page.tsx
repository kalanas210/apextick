import type { Metadata } from "next";
import { LayoutBuilder } from "@/components/admin/layout-builder";

export const metadata: Metadata = {
  title: "Seating layout",
  description: "Generate an event's seating.",
};

export default async function LayoutPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  return <LayoutBuilder id={Number(id)} />;
}
