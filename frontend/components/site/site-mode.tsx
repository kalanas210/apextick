"use client";

import { usePaymentConfig } from "@/hooks/useBooking";
import { cn } from "@/lib/cn";
import { authNote, checkoutNote, footerNote, siteMode } from "@/lib/site-mode";

const NOTES = { footer: footerNote, auth: authNote, checkout: checkoutNote };

/** This deployment's demo and test-mode flags, both false until the API has said otherwise. */
export function useSiteMode() {
  return siteMode(usePaymentConfig().data);
}

/** Beside the logo on a demo deployment, so nobody takes it for a real box office. */
export function DemoBadge({ className }: { className?: string }) {
  const { demo } = useSiteMode();
  if (!demo) return null;
  return (
    <span
      title="Sample fixtures and a shared demo account"
      className={cn(
        "rounded-full border border-accent/40 px-2 py-0.5 font-mono text-[0.58rem] uppercase tracking-[0.18em] text-accent",
        className,
      )}
    >
      Demo
    </span>
  );
}

/** A line about the site, rendered only when it is true of this deployment. */
export function SiteModeNote({
  variant,
  as: Tag = "p",
  className,
}: {
  variant: keyof typeof NOTES;
  as?: "p" | "span";
  className?: string;
}) {
  const note = NOTES[variant](useSiteMode());
  return note ? <Tag className={className}>{note}</Tag> : null;
}
