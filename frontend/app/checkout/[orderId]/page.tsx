import type { Metadata } from "next";
import { CheckoutPanel } from "@/components/checkout/checkout-panel";

export const metadata: Metadata = {
  title: "Checkout",
  description: "Pay for your held seats.",
  robots: { index: false, follow: false },
};

export default async function CheckoutPage({
  params,
}: {
  params: Promise<{ orderId: string }>;
}) {
  const { orderId } = await params;

  return (
    <div className="shell pb-24 pt-28 md:pt-32">
      <header className="border-b border-line pb-6">
        <span className="kicker">Checkout</span>
        <h1 className="display mt-3 text-[clamp(1.9rem,4vw,3rem)]">
          Confirm and pay
        </h1>
      </header>

      <div className="mt-10">
        <CheckoutPanel orderId={orderId} />
      </div>
    </div>
  );
}
