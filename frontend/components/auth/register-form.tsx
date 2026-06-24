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

interface Errors {
  name?: string;
  email?: string;
  password?: string;
  confirm?: string;
  terms?: string;
}

export function RegisterForm() {
  const [name, setName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [confirm, setConfirm] = useState("");
  const [terms, setTerms] = useState(false);
  const [errors, setErrors] = useState<Errors>({});
  const [loading, setLoading] = useState(false);
  const [done, setDone] = useState(false);

  const submit = (e: FormEvent) => {
    e.preventDefault();
    const next: Errors = {};
    if (!name.trim()) next.name = "Tell us your name.";
    if (!email) next.email = "Enter your email.";
    else if (!EMAIL_RE.test(email)) next.email = "That email does not look right.";
    if (!password) next.password = "Choose a password.";
    else if (password.length < 8)
      next.password = "Use at least 8 characters.";
    if (confirm !== password) next.confirm = "Passwords do not match.";
    if (!terms) next.terms = "Please accept the terms to continue.";
    setErrors(next);
    if (Object.keys(next).length) return;
    setLoading(true);
    setTimeout(() => {
      setLoading(false);
      setDone(true);
    }, 1000);
  };

  if (done) {
    return (
      <FormNotice
        title="Account ready"
        action={
          <Link
            href="/signin"
            className="inline-flex h-11 items-center justify-center rounded-full bg-accent px-6 text-sm font-medium text-accent-ink transition hover:brightness-105"
          >
            Go to sign in
          </Link>
        }
      >
        This is a demonstration, so no account was actually created. In the full
        product your seat history and saved fixtures would live here.
      </FormNotice>
    );
  }

  return (
    <form onSubmit={submit} noValidate className="space-y-5">
      <TextField
        id="name"
        label="Full name"
        autoComplete="name"
        value={name}
        onChange={setName}
        error={errors.name}
        placeholder="Alex Fernando"
      />
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
        autoComplete="new-password"
        value={password}
        onChange={setPassword}
        error={errors.password}
        placeholder="At least 8 characters"
      />
      <PasswordField
        id="confirm"
        label="Confirm password"
        autoComplete="new-password"
        value={confirm}
        onChange={setConfirm}
        error={errors.confirm}
        placeholder="Repeat your password"
      />
      <div>
        <Checkbox id="terms" checked={terms} onChange={setTerms}>
          I agree to the terms of service and privacy policy.
        </Checkbox>
        {errors.terms && (
          <p className="mt-1.5 text-[0.78rem] text-[#ff8c8c]">{errors.terms}</p>
        )}
      </div>
      <SubmitButton loading={loading}>Create account</SubmitButton>
      <p className="text-center text-[0.86rem] text-muted">
        Already have an account?{" "}
        <Link
          href="/signin"
          className="text-bone underline-offset-4 hover:underline"
        >
          Sign in
        </Link>
      </p>
    </form>
  );
}
