import type { Metadata } from "next";
import { AccountOrders } from "@/components/account/order-list";

export const metadata: Metadata = {
  title: "My tickets",
  description: "Your ApexTick orders and tickets.",
  robots: { index: false, follow: false },
};

export default function AccountPage() {
  return (
    <div className="shell pb-24 pt-28 md:pt-32">
      <AccountOrders />
    </div>
  );
}
