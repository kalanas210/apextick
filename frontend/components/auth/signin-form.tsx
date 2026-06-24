"use client";

import { useState, type FormEvent } from "react";
import Link from "next/link";
import {
  TextField,
  PasswordField,
  Checkbox,
  SubmitButton,
  FormNotice,
} from "./fields";

const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

export function SignInForm() {
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [remember, setRemember] = useState(true);
  const [errors, setErrors] = useState<{ email?: string; password?: string }>(
    {},
  );
  const [loading, setLoading] = useState(false);
  const [done, setDone] = useState(false);

  const submit = (e: FormEvent) => {
    e.preventDefault();
    const next: typeof errors = {};
    if (!email) next.email = "Enter your email.";
    else if (!EMAIL_RE.test(email)) next.email = "That email does not look right.";
    if (!password) next.password = "Enter your password.";
    setErrors(next);
    if (Object.keys(next).length) return;
    setLoading(true);
    setTimeout(() => {
      setLoading(false);
      setDone(true);
    }, 900);
  };

  if (done) {
    return (
      <FormNotice
        title="You are all set"
        action={
          <Link
            href="/events"
            className="inline-flex h-11 items-center justify-center rounded-full bg-accent px-6 text-sm font-medium text-accent-ink transition hover:brightness-105"
          >
            Browse fixtures
          </Link>
        }
      >
        Sign in is not connected in this demonstration, so no session was
        created. Jump straight into the live fixtures instead.
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
        error={errors.email}
        placeholder="you@email.com"
      />
      <PasswordField
        id="password"
        label="Password"
        autoComplete="current-password"
        value={password}
        onChange={setPassword}
        error={errors.password}
        placeholder="Your password"
      />
      <div className="flex flex-wrap items-center justify-between gap-3">
        <Checkbox id="remember" checked={remember} onChange={setRemember}>
          Keep me signed in
        </Checkbox>
        <Link
          href="/forgot-password"
          className="text-[0.82rem] text-muted underline-offset-4 transition-colors hover:text-bone hover:underline"
        >
          Forgot password?
        </Link>
      </div>
      <SubmitButton loading={loading}>Sign in</SubmitButton>
      <p className="text-center text-[0.86rem] text-muted">
        New to ApexTick?{" "}
        <Link
          href="/register"
          className="text-bone underline-offset-4 hover:underline"
        >
          Create an account
        </Link>
      </p>
    </form>
  );
}
