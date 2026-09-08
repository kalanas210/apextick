"use client";

import { useEffect, useRef } from "react";
import type { ReactNode } from "react";
import { cn } from "@/lib/cn";
import { Button } from "./button";

/**
 * A confirmation built on the native <dialog>, which brings focus trapping,
 * inertness behind the modal and Escape-to-close with the platform rather than
 * a hand-rolled copy of each.
 */
export function ConfirmDialog({
  open,
  title,
  description,
  confirmLabel = "Confirm",
  cancelLabel = "Cancel",
  tone = "default",
  busy = false,
  onConfirm,
  onCancel,
}: {
  open: boolean;
  title: string;
  description: ReactNode;
  confirmLabel?: string;
  cancelLabel?: string;
  /** `danger` for anything destructive — the lime accent never means "delete". */
  tone?: "default" | "danger";
  busy?: boolean;
  onConfirm: () => void;
  onCancel: () => void;
}) {
  const ref = useRef<HTMLDialogElement>(null);

  useEffect(() => {
    const dialog = ref.current;
    if (!dialog) return;
    if (open && !dialog.open) {
      dialog.showModal();
    } else if (!open && dialog.open) {
      dialog.close();
    }
  }, [open]);

  return (
    <dialog
      ref={ref}
      // Escape fires `cancel`; routing it through onCancel keeps React's state
      // the source of truth rather than letting the DOM close behind its back.
      onCancel={(e) => {
        e.preventDefault();
        if (!busy) onCancel();
      }}
      className="max-w-md rounded-2xl border border-line bg-ink-2 p-0 text-bone backdrop:bg-ink/70 backdrop:backdrop-blur-sm"
    >
      <div className="p-7">
        <h2 className="font-display text-lg font-semibold tracking-tight">{title}</h2>
        <div className="mt-2 text-[0.86rem] leading-relaxed text-muted">{description}</div>
        <div className="mt-7 flex justify-end gap-3">
          <Button onClick={onCancel} size="sm" variant="ghost">
            {cancelLabel}
          </Button>
          <Button
            onClick={onConfirm}
            size="sm"
            variant={tone === "danger" ? "outline" : "primary"}
            className={cn(
              tone === "danger" &&
                "border-[#ff6b6b]/50 text-[#ff6b6b] hover:border-[#ff6b6b] hover:bg-[#ff6b6b]/10",
              busy && "pointer-events-none opacity-60",
            )}
          >
            {busy ? "Working…" : confirmLabel}
          </Button>
        </div>
      </div>
    </dialog>
  );
}
