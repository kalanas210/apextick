"use client";

import Link from "next/link";
import { useMemo, useState } from "react";
import type { FormEvent } from "react";
import { cn } from "@/lib/cn";
import { apiErrorCode, apiErrorMessage } from "@/lib/api";
import { formatPrice } from "@/lib/format";
import { useAdminEvent, useApplyLayout } from "@/hooks/useAdmin";
import { AdminHeader } from "./admin-shell";
import { Button } from "@/components/ui/button";
import { ConfirmDialog } from "@/components/ui/confirm-dialog";
import { DataTable } from "@/components/ui/data-table";
import { Field, Input, Select } from "@/components/ui/field";
import { Notice, useNotice } from "@/components/ui/notice";
import { Minus, Plus } from "@/components/ui/icons";
import type { Currency, LayoutInput, PriceTier, Section } from "@/lib/types";

/** The backend labels rows chr(65 + r), so 26 rows is a hard ceiling, not a policy. */
const MAX_ROWS = 26;
/** Above this the public seat map has to render every seat at once. */
const HEAVY_LAYOUT = 5000;

const SIDES = [
  { value: "n", label: "North" },
  { value: "s", label: "South" },
  { value: "e", label: "East" },
  { value: "w", label: "West" },
];

interface TierDraft {
  code: string;
  name: string;
  price: string;
  perks: string;
}

interface SectionDraft {
  code: string;
  name: string;
  tierCode: string;
  side: string;
  rows: string;
  seatsPerRow: string;
}

const emptyTier = (): TierDraft => ({ code: "", name: "", price: "", perks: "" });
const emptySection = (): SectionDraft => ({
  code: "",
  name: "",
  tierCode: "",
  side: "n",
  rows: "10",
  seatsPerRow: "20",
});

interface SectionPreview {
  code: string;
  name: string;
  seats: number;
  rowLabels: string;
  value: number;
}

/**
 * Pure fold from draft rows to the numbers the operator is about to commit to.
 * Row letters mirror the backend's own chr(65 + r) so the preview and the
 * generated seats read the same.
 */
function buildPreview(tiers: TierDraft[], sections: SectionDraft[]) {
  const priceByCode = new Map(tiers.map((t) => [t.code.trim(), Number(t.price) || 0]));
  const rows: SectionPreview[] = sections.map((s) => {
    const r = Math.max(0, Math.min(Number(s.rows) || 0, MAX_ROWS));
    const c = Math.max(0, Number(s.seatsPerRow) || 0);
    const seats = r * c;
    return {
      code: s.code.trim(),
      name: s.name.trim() || s.code.trim(),
      seats,
      rowLabels: r === 0 ? "—" : r === 1 ? "A" : `A–${String.fromCharCode(64 + r)}`,
      value: seats * (priceByCode.get(s.tierCode.trim()) ?? 0),
    };
  });
  return {
    rows,
    seats: rows.reduce((sum, r) => sum + r.seats, 0),
    value: rows.reduce((sum, r) => sum + r.value, 0),
  };
}

export function LayoutBuilder({ id }: { id: number }) {
  const { data: event, isLoading, error } = useAdminEvent(id);

  if (isLoading) {
    return (
      <div className="grid place-items-center py-24" aria-busy="true">
        <span className="h-7 w-7 animate-spin rounded-full border-2 border-line-2 border-t-accent" />
        <span className="sr-only">Loading the event…</span>
      </div>
    );
  }

  if (error || !event) {
    return (
      <div className="rounded-2xl border border-line bg-ink-2 p-8 text-center">
        <p className="text-[0.9rem] text-muted">
          {apiErrorMessage(error, "That event could not be loaded.")}
        </p>
      </div>
    );
  }

  const hasLayout = event.tiers.length > 0 || event.sections.length > 0;

  return (
    <>
      <AdminHeader
        kicker={
          <Link href={`/admin/events/${id}`} className="ulink">
            {event.name}
          </Link>
        }
        title="Seating layout"
        description={
          hasLayout
            ? "This event's seating has been generated."
            : "Define the price tiers, then the stands. Seats are generated from the two."
        }
        action={
          <Button href={`/admin/events/${id}`} size="sm" variant="outline">
            Back to event
          </Button>
        }
      />

      {hasLayout ? (
        <AppliedLayout id={id} tiers={event.tiers} sections={event.sections} currency={event.currency} />
      ) : (
        <Builder id={id} currency={event.currency} eventName={event.name} />
      )}
    </>
  );
}

/**
 * There is no update endpoint, so once a layout exists there is nothing to
 * submit — showing a disabled form would only invite someone to try. This
 * reports what was built and says plainly how to change it.
 */
function AppliedLayout({
  id,
  tiers,
  sections,
  currency,
}: {
  id: number;
  tiers: PriceTier[];
  sections: Section[];
  currency: Currency;
}) {
  const total = tiers.reduce((sum, t) => sum + t.total, 0);

  return (
    <div className="mt-8 space-y-8">
      <Notice tone="info">
        Seating layouts are created once. There is no edit endpoint — to change this one, delete the
        event and create it again. That only works until the first order: after that, cancel the
        event instead.
      </Notice>

      <section>
        <h2 className="kicker mb-3">
          Price tiers · <span className="tnum">{total}</span> seats
        </h2>
        <DataTable<PriceTier>
          caption="price tiers"
          rows={tiers}
          rowKey={(t) => t.id}
          columns={[
            { key: "code", header: "Code", cell: (t) => <span className="tnum">{t.code}</span> },
            { key: "name", header: "Name", cell: (t) => t.name },
            {
              key: "price",
              header: "Price",
              align: "right",
              cell: (t) => formatPrice(t.price, currency),
            },
            { key: "seats", header: "Seats", align: "right", cell: (t) => t.total },
            {
              key: "remaining",
              header: "Available",
              align: "right",
              cell: (t) => t.remaining,
            },
            {
              key: "perks",
              header: "Perks",
              cell: (t) => <span className="text-muted">{t.perks.join(", ") || "—"}</span>,
            },
          ]}
        />
      </section>

      <section>
        <h2 className="kicker mb-3">Sections</h2>
        <DataTable<Section>
          caption="sections"
          rows={sections}
          rowKey={(s) => s.id}
          columns={[
            { key: "code", header: "Code", cell: (s) => <span className="tnum">{s.code}</span> },
            { key: "name", header: "Name", cell: (s) => s.name },
            { key: "tier", header: "Tier", cell: (s) => <span className="tnum">{s.tierCode}</span> },
            { key: "side", header: "Side", cell: (s) => <span className="text-muted">{s.side}</span> },
            {
              key: "grid",
              header: "Grid",
              align: "right",
              cell: (s) => `${s.rows} × ${s.seatsPerRow}`,
            },
            { key: "available", header: "Available", align: "right", cell: (s) => s.available },
          ]}
        />
      </section>

      <div>
        <Button href={`/admin/events/${id}/seats`} size="md" variant="outline">
          Inspect seats
        </Button>
      </div>
    </div>
  );
}

function Builder({ id, currency, eventName }: { id: number; currency: Currency; eventName: string }) {
  const apply = useApplyLayout(id);
  const { notice, show, clear } = useNotice();
  const [tiers, setTiers] = useState<TierDraft[]>([emptyTier()]);
  const [sections, setSections] = useState<SectionDraft[]>([emptySection()]);
  const [errors, setErrors] = useState<string[]>([]);
  const [confirm, setConfirm] = useState(false);

  const preview = useMemo(() => buildPreview(tiers, sections), [tiers, sections]);
  const tierCodes = tiers.map((t) => t.code.trim()).filter(Boolean);

  const setTier = (i: number, patch: Partial<TierDraft>) =>
    setTiers((list) => list.map((t, n) => (n === i ? { ...t, ...patch } : t)));
  const setSection = (i: number, patch: Partial<SectionDraft>) =>
    setSections((list) => list.map((s, n) => (n === i ? { ...s, ...patch } : s)));

  /** Mirrors the server's rules, so most of them never have to be hit to be learned. */
  const validate = (): string[] => {
    const found: string[] = [];
    if (!tiers.length) found.push("Add at least one price tier.");
    if (!sections.length) found.push("Add at least one section.");

    tiers.forEach((t, i) => {
      if (!t.code.trim()) found.push(`Tier ${i + 1} needs a code.`);
      if (!t.name.trim()) found.push(`Tier ${i + 1} needs a name.`);
      if (!t.price.trim() || Number(t.price) < 0) found.push(`Tier ${i + 1} needs a price.`);
    });
    if (new Set(tierCodes).size !== tierCodes.length) found.push("Tier codes must be unique.");

    const sectionCodes = sections.map((s) => s.code.trim()).filter(Boolean);
    if (new Set(sectionCodes).size !== sectionCodes.length) {
      found.push("Section codes must be unique.");
    }
    sections.forEach((s, i) => {
      const label = s.code.trim() || `Section ${i + 1}`;
      if (!s.code.trim()) found.push(`Section ${i + 1} needs a code.`);
      if (!s.tierCode.trim()) found.push(`${label} needs a tier.`);
      const rows = Number(s.rows);
      const spr = Number(s.seatsPerRow);
      if (!(rows >= 1 && rows <= MAX_ROWS)) {
        found.push(`${label}: rows are labelled A–Z, so 1 to ${MAX_ROWS}.`);
      }
      if (!(spr >= 1)) found.push(`${label}: needs at least one seat per row.`);
    });
    return found;
  };

  const submit = (e: FormEvent) => {
    e.preventDefault();
    clear();
    const found = validate();
    setErrors(found);
    if (found.length === 0) setConfirm(true);
  };

  const commit = () => {
    const body: LayoutInput = {
      tiers: tiers.map((t, i) => ({
        code: t.code.trim(),
        name: t.name.trim(),
        price: Number(t.price),
        perks: t.perks
          .split(",")
          .map((p) => p.trim())
          .filter(Boolean),
        sortOrder: i,
      })),
      sections: sections.map((s, i) => ({
        code: s.code.trim(),
        name: s.name.trim() || s.code.trim(),
        tierCode: s.tierCode.trim(),
        side: s.side as "n" | "s" | "e" | "w",
        rows: Number(s.rows),
        seatsPerRow: Number(s.seatsPerRow),
        sortOrder: i,
      })),
    };

    apply.mutate(body, {
      onSuccess: (result) => {
        setConfirm(false);
        show("success", `Generated ${result.seatsCreated} seats.`);
      },
      onError: (err) => {
        setConfirm(false);
        // On LAYOUT_EXISTS somebody got there first. The mutation's own
        // invalidation refetches the event, which flips this page into its
        // read-only half rather than leaving a form that cannot work.
        show(
          "error",
          apiErrorCode(err) === "LAYOUT_EXISTS"
            ? "This event already has a seating layout."
            : apiErrorMessage(err, "Could not apply the layout."),
        );
      },
    });
  };

  return (
    <>
      <Notice tone="info" className="mt-6">
        This runs once. Applying generates every seat immediately, and there is no way to edit or
        re-run it afterwards.
      </Notice>

      {notice && (
        <Notice tone={notice.tone} onDismiss={clear} className="mt-4">
          {notice.message}
        </Notice>
      )}

      <form onSubmit={submit} className="mt-8 grid gap-10 lg:grid-cols-12 lg:gap-12">
        <div className="space-y-10 lg:col-span-7">
          <section>
            <div className="mb-4 flex items-center justify-between gap-4">
              <h2 className="kicker">Price tiers</h2>
              <RowButton onClick={() => setTiers((l) => [...l, emptyTier()])} label="Add tier" />
            </div>

            <div className="space-y-4">
              {tiers.map((tier, i) => (
                <fieldset key={i} className="rounded-2xl border border-line bg-ink-2 p-5">
                  <legend className="sr-only">Tier {i + 1}</legend>
                  <div className="grid gap-4 sm:grid-cols-2">
                    <Field label="Code" required>
                      {(a) => (
                        <Input
                          {...a}
                          value={tier.code}
                          onChange={(e) => setTier(i, { code: e.target.value })}
                          placeholder="VIP"
                        />
                      )}
                    </Field>
                    <Field label="Name" required>
                      {(a) => (
                        <Input
                          {...a}
                          value={tier.name}
                          onChange={(e) => setTier(i, { name: e.target.value })}
                          placeholder="VIP Box"
                        />
                      )}
                    </Field>
                    <Field label={`Price (${currency})`} required>
                      {(a) => (
                        <Input
                          {...a}
                          type="number"
                          min="0"
                          step="1"
                          value={tier.price}
                          onChange={(e) => setTier(i, { price: e.target.value })}
                          placeholder="25000"
                        />
                      )}
                    </Field>
                    <Field label="Perks" hint="Comma separated">
                      {(a) => (
                        <Input
                          {...a}
                          value={tier.perks}
                          onChange={(e) => setTier(i, { perks: e.target.value })}
                          placeholder="Lounge access, Free parking"
                        />
                      )}
                    </Field>
                  </div>
                  {tiers.length > 1 && (
                    <div className="mt-4">
                      <RowButton
                        remove
                        onClick={() => setTiers((l) => l.filter((_, n) => n !== i))}
                        label={`Remove tier ${i + 1}`}
                      />
                    </div>
                  )}
                </fieldset>
              ))}
            </div>
          </section>

          <section>
            <div className="mb-4 flex items-center justify-between gap-4">
              <h2 className="kicker">Sections</h2>
              <RowButton
                onClick={() =>
                  setSections((l) => [...l, { ...emptySection(), tierCode: tierCodes[0] ?? "" }])
                }
                label="Add section"
              />
            </div>

            <div className="space-y-4">
              {sections.map((section, i) => (
                <fieldset key={i} className="rounded-2xl border border-line bg-ink-2 p-5">
                  <legend className="sr-only">Section {i + 1}</legend>
                  <div className="grid gap-4 sm:grid-cols-2">
                    <Field label="Code" required>
                      {(a) => (
                        <Input
                          {...a}
                          value={section.code}
                          onChange={(e) => setSection(i, { code: e.target.value })}
                          placeholder="A"
                        />
                      )}
                    </Field>
                    <Field label="Name">
                      {(a) => (
                        <Input
                          {...a}
                          value={section.name}
                          onChange={(e) => setSection(i, { name: e.target.value })}
                          placeholder="Pavilion A"
                        />
                      )}
                    </Field>
                    {/* Picking from the tiers typed above makes the server's
                        UNKNOWN_TIER error unreachable rather than merely handled. */}
                    <Field label="Tier" required hint={tierCodes.length ? undefined : "Add a tier first"}>
                      {(a) => (
                        <Select
                          {...a}
                          value={section.tierCode}
                          onChange={(e) => setSection(i, { tierCode: e.target.value })}
                          placeholder="Pick a tier"
                          options={tierCodes.map((c) => ({ value: c, label: c }))}
                        />
                      )}
                    </Field>
                    <Field label="Side">
                      {(a) => (
                        <Select
                          {...a}
                          value={section.side}
                          onChange={(e) => setSection(i, { side: e.target.value })}
                          options={SIDES}
                        />
                      )}
                    </Field>
                    <Field label="Rows" required hint={`1 to ${MAX_ROWS} — labelled A to Z`}>
                      {(a) => (
                        <Input
                          {...a}
                          type="number"
                          min="1"
                          max={MAX_ROWS}
                          value={section.rows}
                          onChange={(e) => setSection(i, { rows: e.target.value })}
                        />
                      )}
                    </Field>
                    <Field label="Seats per row" required>
                      {(a) => (
                        <Input
                          {...a}
                          type="number"
                          min="1"
                          value={section.seatsPerRow}
                          onChange={(e) => setSection(i, { seatsPerRow: e.target.value })}
                        />
                      )}
                    </Field>
                  </div>
                  {sections.length > 1 && (
                    <div className="mt-4">
                      <RowButton
                        remove
                        onClick={() => setSections((l) => l.filter((_, n) => n !== i))}
                        label={`Remove section ${i + 1}`}
                      />
                    </div>
                  )}
                </fieldset>
              ))}
            </div>
          </section>

          {errors.length > 0 && (
            <Notice tone="error">
              <ul className="list-disc space-y-1 pl-4">
                {errors.map((e) => (
                  <li key={e}>{e}</li>
                ))}
              </ul>
            </Notice>
          )}
        </div>

        <aside className="lg:col-span-5">
          {/* Capped and scrollable: a sticky column taller than the viewport
              would strand whatever sits at its bottom. */}
            <div className="lg:sticky lg:top-8 lg:max-h-[calc(100vh-4rem)] lg:overflow-y-auto lg:pr-1 [scrollbar-width:thin]">
            <div className="rounded-2xl border border-line bg-ink-2 p-6">
              <h2 className="kicker">Preview</h2>
              <p className="tnum mt-3 font-display text-[2rem] leading-none tracking-tight text-bone">
                {preview.seats.toLocaleString("en-GB")}
              </p>
              <p className="mt-1 text-[0.78rem] text-faint">
                seats · {formatPrice(preview.value, currency)} at face value
              </p>

              <ul className="mt-5 space-y-2 border-t border-line pt-4 text-[0.8rem]">
                {preview.rows.map((r, i) => (
                  <li key={i} className="flex items-baseline justify-between gap-3">
                    <span className="min-w-0 truncate text-muted">
                      {r.name || <span className="text-faint">Unnamed</span>}
                      <span className="text-faint"> · rows {r.rowLabels}</span>
                    </span>
                    <span className="tnum shrink-0 text-bone">{r.seats}</span>
                  </li>
                ))}
              </ul>

              {preview.seats > HEAVY_LAYOUT && (
                <Notice tone="info" className="mt-5 border-accent/40 text-accent">
                  {preview.seats.toLocaleString("en-GB")} seats is a lot. Generating them is fine,
                  but the public seat map loads every seat at once and will struggle.
                </Notice>
              )}

              <div className="mt-6">
                <Button
                  type="submit"
                  size="md"
                  className={apply.isPending ? "pointer-events-none opacity-60" : ""}
                >
                  {apply.isPending ? "Generating…" : "Apply layout"}
                </Button>
              </div>
            </div>
          </div>
        </aside>
      </form>

      <ConfirmDialog
        open={confirm}
        title="Generate this layout?"
        description={
          <>
            Creates <strong className="text-bone">{tiers.length}</strong>{" "}
            {tiers.length === 1 ? "tier" : "tiers"},{" "}
            <strong className="text-bone">{sections.length}</strong>{" "}
            {sections.length === 1 ? "section" : "sections"} and{" "}
            <strong className="text-bone">{preview.seats.toLocaleString("en-GB")}</strong> seats for{" "}
            {eventName}. Layouts cannot be edited or re-run.
          </>
        }
        confirmLabel="Generate"
        busy={apply.isPending}
        onConfirm={commit}
        onCancel={() => setConfirm(false)}
      />
    </>
  );
}

function RowButton({
  onClick,
  label,
  remove,
}: {
  onClick: () => void;
  label: string;
  remove?: boolean;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={cn(
        "inline-flex items-center gap-1.5 rounded-full border border-line-2 px-3 py-1.5 text-[0.78rem] transition-colors",
        remove
          ? "text-faint hover:border-[#ff6b6b]/50 hover:text-[#ff6b6b]"
          : "text-muted hover:border-bone hover:text-bone",
      )}
    >
      {remove ? <Minus className="h-3.5 w-3.5" /> : <Plus className="h-3.5 w-3.5" />}
      {label}
    </button>
  );
}
