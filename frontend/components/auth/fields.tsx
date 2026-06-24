"use client";

import { useState, type InputHTMLAttributes, type ReactNode } from "react";
import { cn } from "@/lib/cn";
import { Check, Eye, EyeOff } from "@/components/ui/icons";

const inputBase =
  "w-full rounded-xl border bg-ink-2 px-4 py-3 text-[0.95rem] text-bone placeholder:text-faint outline-none transition-colors duration-200";

const labelBase =
  "mb-2 block font-mono text-[0.66rem] uppercase tracking-[0.18em] text-muted";

function borderFor(error?: string) {
  return error
    ? "border-[#ff6b6b] focus:border-[#ff6b6b]"
    : "border-line-2 focus:border-bone/45";
}

interface FieldProps {
  id: string;
  label: string;
  value: string;
  onChange: (v: string) => void;
  error?: string;
  type?: string;
  autoComplete?: string;
  placeholder?: string;
  required?: boolean;
  inputMode?: InputHTMLAttributes<HTMLInputElement>["inputMode"];
}

export function TextField({
  id,
  label,
  value,
  onChange,
  error,
  type = "text",
  autoComplete,
  placeholder,
  required,
  inputMode,
}: FieldProps) {
  return (
    <div>
      <label htmlFor={id} className={labelBase}>
        {label}
      </label>
      <input
        id={id}
        type={type}
        value={value}
        onChange={(e) => onChange(e.target.value)}
        autoComplete={autoComplete}
        placeholder={placeholder}
        required={required}
        inputMode={inputMode}
        aria-invalid={error ? true : undefined}
        aria-describedby={error ? `${id}-error` : undefined}
        className={cn(inputBase, borderFor(error))}
      />
      {error && (
        <p id={`${id}-error`} className="mt-1.5 text-[0.78rem] text-[#ff8c8c]">
          {error}
        </p>
      )}
    </div>
  );
}

export function PasswordField({
  id,
  label,
  value,
  onChange,
  error,
  autoComplete,
  placeholder,
  required,
}: Omit<FieldProps, "type" | "inputMode">) {
  const [show, setShow] = useState(false);
  return (
    <div>
      <label htmlFor={id} className={labelBase}>
        {label}
      </label>
      <div className="relative">
        <input
          id={id}
          type={show ? "text" : "password"}
          value={value}
          onChange={(e) => onChange(e.target.value)}
          autoComplete={autoComplete}
          placeholder={placeholder}
          required={required}
          aria-invalid={error ? true : undefined}
          aria-describedby={error ? `${id}-error` : undefined}
          className={cn(inputBase, "pr-12", borderFor(error))}
        />
        <button
          type="button"
          onClick={() => setShow((s) => !s)}
          aria-label={show ? "Hide password" : "Show password"}
          className="absolute right-1.5 top-1/2 grid h-9 w-9 -translate-y-1/2 place-items-center rounded-lg text-muted transition-colors hover:text-bone"
        >
          {show ? (
            <EyeOff className="h-[18px] w-[18px]" />
          ) : (
            <Eye className="h-[18px] w-[18px]" />
          )}
        </button>
      </div>
      {error && (
        <p id={`${id}-error`} className="mt-1.5 text-[0.78rem] text-[#ff8c8c]">
          {error}
        </p>
      )}
    </div>
  );
}

export function Checkbox({
  id,
  checked,
  onChange,
  children,
}: {
  id: string;
  checked: boolean;
  onChange: (v: boolean) => void;
  children: ReactNode;
}) {
  return (
    <label htmlFor={id} className="flex cursor-pointer items-start gap-3">
      <input
        id={id}
        type="checkbox"
        checked={checked}
        onChange={(e) => onChange(e.target.checked)}
        className="peer sr-only"
      />
      <span
        aria-hidden
        className={cn(
          "mt-0.5 grid h-5 w-5 shrink-0 place-items-center rounded-[5px] border transition-colors peer-focus-visible:outline peer-focus-visible:outline-2 peer-focus-visible:outline-offset-2 peer-focus-visible:outline-accent",
          checked ? "border-accent bg-accent" : "border-line-2",
        )}
      >
        {checked && <Check className="h-3 w-3 text-accent-ink" />}
      </span>
      <span className="text-[0.85rem] leading-snug text-muted">{children}</span>
    </label>
  );
}

export function SubmitButton({
  loading,
  children,
}: {
  loading?: boolean;
  children: ReactNode;
}) {
  return (
    <button
      type="submit"
      disabled={loading}
      className="inline-flex h-12 w-full items-center justify-center gap-2 rounded-full bg-accent text-[0.92rem] font-medium text-accent-ink transition hover:brightness-105 disabled:cursor-not-allowed disabled:opacity-70"
    >
      {loading && (
        <span className="h-4 w-4 animate-spin rounded-full border-2 border-accent-ink/30 border-t-accent-ink" />
      )}
      {children}
    </button>
  );
}

export function FormNotice({
  title,
  children,
  action,
}: {
  title: string;
  children: ReactNode;
  action?: ReactNode;
}) {
  return (
    <div
      role="status"
      className="rounded-2xl border border-line bg-ink-2 p-7 text-center"
    >
      <span className="mx-auto grid h-12 w-12 place-items-center rounded-full bg-accent text-accent-ink">
        <Check className="h-6 w-6" />
      </span>
      <h2 className="mt-5 font-display text-xl tracking-tight text-bone">
        {title}
      </h2>
      <p className="mt-2.5 text-[0.88rem] leading-relaxed text-muted">
        {children}
      </p>
      {action && <div className="mt-6">{action}</div>}
    </div>
  );
}
