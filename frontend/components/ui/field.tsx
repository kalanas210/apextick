"use client";

import { forwardRef, useId } from "react";
import type { ComponentProps, ReactNode } from "react";
import { cn } from "@/lib/cn";

/**
 * The shared control surface. Lifted out of the checkout card form, which had it
 * as a local const — the admin forms would otherwise be its second copy.
 */
export const field =
  "w-full rounded-lg border border-line-2 bg-ink px-3.5 py-2.5 text-[0.9rem] text-bone outline-none transition-colors placeholder:text-faint focus:border-bone";

const invalid = "border-[#ff6b6b]/60 focus:border-[#ff6b6b]";

/**
 * Label, hint and error around one control, with the aria wiring that makes the
 * error reach a screen reader rather than only the eye.
 */
export function Field({
  label,
  hint,
  error,
  required,
  className,
  children,
}: {
  label: string;
  hint?: ReactNode;
  error?: string;
  required?: boolean;
  className?: string;
  /** Receives the id and aria props to spread onto the control. */
  children: (props: {
    id: string;
    "aria-invalid": boolean | undefined;
    "aria-describedby": string | undefined;
  }) => ReactNode;
}) {
  const id = useId();
  const hintId = `${id}-hint`;
  const errorId = `${id}-error`;
  const describedBy = error ? errorId : hint ? hintId : undefined;

  return (
    <div className={cn("block", className)}>
      <label htmlFor={id} className="kicker mb-1.5 block">
        {label}
        {required && <span className="ml-1 text-accent">*</span>}
      </label>
      {children({
        id,
        "aria-invalid": error ? true : undefined,
        "aria-describedby": describedBy,
      })}
      {error ? (
        <p id={errorId} role="alert" className="mt-1.5 text-[0.75rem] text-[#ff6b6b]">
          {error}
        </p>
      ) : hint ? (
        <p id={hintId} className="mt-1.5 text-[0.75rem] text-faint">
          {hint}
        </p>
      ) : null}
    </div>
  );
}

export const Input = forwardRef<HTMLInputElement, ComponentProps<"input">>(
  function Input({ className, ...props }, ref) {
    return (
      <input
        ref={ref}
        className={cn(field, props["aria-invalid"] && invalid, className)}
        {...props}
      />
    );
  },
);

export const Textarea = forwardRef<HTMLTextAreaElement, ComponentProps<"textarea">>(
  function Textarea({ className, ...props }, ref) {
    return (
      <textarea
        ref={ref}
        className={cn(field, "min-h-24 resize-y", props["aria-invalid"] && invalid, className)}
        {...props}
      />
    );
  },
);

export interface SelectOption {
  value: string;
  label: string;
}

/**
 * A native select. The dropdown itself is drawn by the OS, so the dark styling
 * stops at the closed control — the alternative is a hand-rolled listbox, which
 * would be the one control here that behaves unlike every other on the site.
 */
export const Select = forwardRef<
  HTMLSelectElement,
  ComponentProps<"select"> & { options: SelectOption[]; placeholder?: string }
>(function Select({ options, placeholder, className, ...props }, ref) {
  return (
    <select
      ref={ref}
      className={cn(field, "appearance-none pr-8", props["aria-invalid"] && invalid, className)}
      {...props}
    >
      {placeholder !== undefined && <option value="">{placeholder}</option>}
      {options.map((o) => (
        <option key={o.value} value={o.value}>
          {o.label}
        </option>
      ))}
    </select>
  );
});
