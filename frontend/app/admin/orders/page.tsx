import type { Metadata } from "next";
import { Suspense } from "react";
import { OrderTable } from "@/components/admin/order-table";

export const metadata: Metadata = {
  title: "Orders",
  description: "Every order across the catalog.",
};

export default function AdminOrdersPage() {
  return (
    <Suspense fallback={null}>
      <OrderTable />
    </Suspense>
  );
}
