import type { Metadata } from "next";
import { SeatInspector } from "@/components/admin/seat-inspector";

export const metadata: Metadata = {
  title: "Seats",
  description: "Inspect and release seats.",
};

export default async function SeatsPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  return <SeatInspector id={Number(id)} />;
}
