import type { Metadata } from "next";
import { OrderInspector } from "@/components/admin/order-inspector";

export const metadata: Metadata = {
  title: "Order",
  description: "One order in full, and its refund.",
};

export default async function AdminOrderPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  return <OrderInspector id={id} />;
}
