"use client";

import { useState, type FormEvent } from "react";

const TEST_CARDS = [
  { label: "Succeeds", number: "4242 4242 4242 4242" },
  { label: "Declined", number: "4000 0000 0000 0002" },
  { label: "No funds", number: "4000 0000 0000 9995" },
];

export interface MockCard {
  number: string;
  expMonth: number;
  expYear: number;
  cvc: string;
  holder: string;
}

/**
 * Card form for the offline `mock` gateway. These numbers never leave our own
 * backend — the mock provider recognises them and resolves deterministically,
 * which is what lets the whole booking flow be demoed with no payment account.
 */
export function MockCardForm({
  onSubmit,
  disabled,
  submitting,
  total,
}: {
  onSubmit: (card: MockCard) => void;
  disabled?: boolean;
  submitting?: boolean;
  total: string;
}) {
  const [number, setNumber] = useState("4242 4242 4242 4242");
  const [expiry, setExpiry] = useState("12/30");
  const [cvc, setCvc] = useState("123");
  const [holder, setHolder] = useState("");

  const submit = (event: FormEvent) => {
    event.preventDefault();
    const [month, year] = expiry.split("/").map((part) => parseInt(part.trim(), 10));
    onSubmit({
      number: number.replace(/\s/g, ""),
      expMonth: month || 12,
      expYear: year ? (year < 100 ? 2000 + year : year) : 2030,
      cvc,
      holder: holder.trim() || "ApexTick Demo",
    });
  };

  const field =
    "w-full rounded-lg border border-line-2 bg-ink px-3.5 py-2.5 text-[0.9rem] text-bone outline-none transition-colors placeholder:text-faint focus:border-bone";

  return (
    <form onSubmit={submit} className="space-y-4">
      <div className="flex flex-wrap gap-2">
        {TEST_CARDS.map((card) => (
          <button
            key={card.number}
            type="button"
            onClick={() => setNumber(card.number)}
            className="rounded-full border border-line-2 px-3 py-1 font-mono text-[0.66rem] text-muted transition-colors hover:border-bone hover:text-bone"
          >
            {card.label}
          </button>
        ))}
      </div>

      <label className="block">
        <span className="kicker mb-1.5 block">Card number</span>
        <input
          className={`${field} tnum`}
          value={number}
          onChange={(e) => setNumber(e.target.value)}
          inputMode="numeric"
          autoComplete="off"
          required
        />
      </label>

      <div className="grid grid-cols-2 gap-4">
        <label className="block">
          <span className="kicker mb-1.5 block">Expiry</span>
          <input
            className={`${field} tnum`}
            value={expiry}
            onChange={(e) => setExpiry(e.target.value)}
            placeholder="MM/YY"
            required
          />
        </label>
        <label className="block">
          <span className="kicker mb-1.5 block">CVC</span>
          <input
            className={`${field} tnum`}
            value={cvc}
            onChange={(e) => setCvc(e.target.value)}
            inputMode="numeric"
            required
          />
        </label>
      </div>

      <label className="block">
        <span className="kicker mb-1.5 block">Name on card</span>
        <input
          className={field}
          value={holder}
          onChange={(e) => setHolder(e.target.value)}
          placeholder="ApexTick Demo"
        />
      </label>

      <button
        type="submit"
        disabled={disabled || submitting}
        className="inline-flex h-12 w-full items-center justify-center gap-2 rounded-full bg-accent text-[0.92rem] font-medium text-accent-ink transition-[filter] hover:brightness-105 disabled:opacity-60"
      >
        {submitting ? (
          <>
            <span className="h-4 w-4 animate-spin rounded-full border-2 border-accent-ink/30 border-t-accent-ink" />
            Taking payment…
          </>
        ) : (
          <>Pay {total}</>
        )}
      </button>
    </form>
  );
}
