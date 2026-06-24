"use client";

import { useState, type FormEvent } from "react";
import Link from "next/link";
import { TextField, SubmitButton, FormNotice } from "./fields";

const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

export function ForgotForm() {
  const [email, setEmail] = useState("");
  const [error, setError] = useState<string>();
  const [loading, setLoading] = useState(false);
  const [sentTo, setSentTo] = useState<string | null>(null);

  const submit = (e: FormEvent) => {
    e.preventDefault();
    if (!email) {
      setError("Enter your email.");
      return;
    }
    if (!EMAIL_RE.test(email)) {
      setError("That email does not look right.");
      return;
    }
    setError(undefined);
    setLoading(true);
    setTimeout(() => {
      setLoading(false);
      setSentTo(email);
    }, 900);
  };

  if (sentTo) {
    return (
      <FormNotice
        title="Check your inbox"
        action={
          <Link
            href="/signin"
            className="inline-flex h-11 items-center justify-center rounded-full border border-line-2 px-6 text-sm font-medium text-bone transition-colors hover:border-bone"
          >
            Back to sign in
          </Link>
        }
      >
        If an account exists for {sentTo}, a password reset link is on its way.
        This is a demonstration, so no email is actually sent.
      </FormNotice>
    );
  }

  return (
    <form onSubmit={submit} noValidate className="space-y-5">
      <TextField
        id="email"
        label="Email"
        type="email"
        inputMode="email"
        autoComplete="email"
        value={email}
        onChange={setEmail}
        error={error}
        placeholder="you@email.com"
      />
      <SubmitButton loading={loading}>Send reset link</SubmitButton>
      <p className="text-center text-[0.86rem] text-muted">
        Remembered it?{" "}
        <Link
          href="/signin"
          className="text-bone underline-offset-4 hover:underline"
        >
          Back to sign in
        </Link>
      </p>
    </form>
  );
}
