import type { Metadata } from "next";
import { TicketScanner } from "@/components/admin/ticket-scanner";

export const metadata: Metadata = {
  title: "Scan tickets",
  description: "Verify tickets at the gate.",
};

export default function ScanPage() {
  return <TicketScanner />;
}
